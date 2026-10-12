import { FieldValue, Transaction } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { chatNotes, errors, pushes } from "./copy";
import { db, loadPerson, notify, parse, postToChat, requests, requireAuth, users } from "./db";
import { chatIdFor, RequestType, summarize } from "./format";
import { CreateRequestInput, DecideRequestInput, RequestIdInput } from "./schemas";

const MAX_PENDING = 10;

export interface RequestDoc {
  requesterId: string;
  requesterName: string;
  type: RequestType;
  packageName: string | null;
  appName: string | null;
  minutes: number;
  dailyMinutes: number;
  weeklyMinutes: number;
  message: string;
  status: "pending" | "approved" | "denied" | "cancelled" | "expired";
  approverIds: string[];
  dayKey: string;
}

/** The phone's local date is within a day of the server's UTC date, whatever its time zone. */
export function isAroundToday(dayKey: string, now: number): boolean {
  const day = Date.parse(`${dayKey}T00:00:00Z`);
  const today = Math.floor(now / 86_400_000) * 86_400_000;
  return Number.isFinite(day) && Math.abs(day - today) <= 86_400_000;
}

export const createRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const input = parse(CreateRequestInput, call.data);
  const me = await loadPerson(uid);
  if (me.account.approverIds.length === 0) throw new HttpsError("failed-precondition", errors.noBumblebees);
  if (!isAroundToday(input.dayKey, Date.now())) throw new HttpsError("invalid-argument", errors.badDate);

  const doc: RequestDoc = {
    requesterId: uid,
    requesterName: me.profile.displayName,
    type: input.type,
    packageName: null,
    appName: null,
    minutes: input.type === "extra_time" ? input.minutes : 0,
    dailyMinutes: input.type === "change_limit" ? input.dailyMinutes : 0,
    weeklyMinutes: input.type === "change_limit" ? input.weeklyMinutes : 0,
    message: input.message,
    status: "pending",
    approverIds: [...me.account.approverIds],
    dayKey: input.dayKey,
  };
  if (input.type === "disable_strict_mode") {
    if (!me.profile.strictMode) throw new HttpsError("failed-precondition", errors.strictAlreadyOff);
  } else {
    const limit = await users().doc(uid).collection("limits").doc(input.packageName).get();
    if (!limit.exists) throw new HttpsError("failed-precondition", errors.noLimit);
    doc.packageName = input.packageName;
    doc.appName = input.appName || (limit.get("appName") as string);
  }

  const pending = await requests().where("requesterId", "==", uid).where("status", "==", "pending").get();
  if (pending.size >= MAX_PENDING) throw new HttpsError("resource-exhausted", errors.tooManyRequests);
  if (pending.docs.some((d) => d.get("type") === doc.type && (d.get("packageName") ?? null) === doc.packageName)) {
    throw new HttpsError("already-exists", errors.duplicateRequest);
  }

  const ref = requests().doc();
  await ref.set({ ...doc, createdAt: FieldValue.serverTimestamp() });
  const summary = summarize(doc);
  await Promise.all(
    doc.approverIds.map((beeUid) =>
      postToChat(chatIdFor(uid, beeUid), { senderId: uid, kind: "request", text: chatNotes.request(me.profile, summary), requestId: ref.id }),
    ),
  );
  await notify(doc.approverIds, { type: "request_new", requestId: ref.id, ...pushes.request(me.profile, summary, doc.message) });
  return { requestId: ref.id };
});

/** Applies an approved request. Runs inside the decision transaction. */
function applyApproval(tx: Transaction, requestId: string, r: RequestDoc, deciderUid: string, grantedMinutes: number): void {
  const requesterRef = users().doc(r.requesterId);
  switch (r.type) {
    case "extra_time":
      tx.set(requesterRef.collection("grants").doc(requestId), {
        packageName: r.packageName,
        minutes: grantedMinutes,
        dayKey: r.dayKey,
        requestId,
        grantedBy: deciderUid,
        createdAt: FieldValue.serverTimestamp(),
      });
      break;
    case "change_limit":
      tx.set(
        requesterRef.collection("limits").doc(r.packageName!),
        {
          packageName: r.packageName,
          appName: r.appName,
          dailyMinutes: r.dailyMinutes,
          weeklyMinutes: r.weeklyMinutes,
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
      break;
    case "remove_limit":
      tx.delete(requesterRef.collection("limits").doc(r.packageName!));
      break;
    case "disable_strict_mode":
      tx.update(requesterRef, { strictMode: false });
      break;
  }
}

export const decideRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const { requestId, approve, note, minutes } = parse(DecideRequestInput, call.data);
  const requestRef = requests().doc(requestId);

  const { r, decider } = await db().runTransaction(async (tx) => {
    const snap = await tx.get(requestRef);
    if (!snap.exists) throw new HttpsError("not-found", errors.requestGone);
    const r = snap.data() as RequestDoc;
    if (r.status !== "pending") throw new HttpsError("failed-precondition", errors.requestAnswered);
    if (r.requesterId === uid) throw new HttpsError("permission-denied", errors.ownRequest);
    if (!r.approverIds.includes(uid)) throw new HttpsError("permission-denied", errors.notYourRequest);

    const [requester, deciderPerson] = await Promise.all([loadPerson(r.requesterId, tx), loadPerson(uid, tx)]);
    // Bumblebees who were removed or stepped down since the request was made can't answer it.
    if (!requester.account.approverIds.includes(uid)) throw new HttpsError("permission-denied", errors.noLongerBumblebee);
    const decider = deciderPerson.profile;

    const granted = approve && r.type === "extra_time" && minutes ? Math.min(minutes, r.minutes) : r.minutes;
    if (approve) applyApproval(tx, requestId, r, uid, granted);
    tx.update(requestRef, {
      status: approve ? "approved" : "denied",
      decidedBy: uid,
      decidedByName: decider.displayName,
      decisionNote: note,
      decidedAt: FieldValue.serverTimestamp(),
      ...(r.type === "extra_time" && approve ? { minutes: granted } : {}),
    });
    return { r: { ...r, minutes: granted }, decider };
  });

  const summary = summarize(r);
  await postToChat(chatIdFor(uid, r.requesterId), {
    senderId: uid,
    kind: "system",
    text: approve ? chatNotes.yes(decider, summary) : chatNotes.notToday(decider, summary),
  });
  await notify([r.requesterId], { type: "request_decided", requestId, ...pushes.decided(decider, approve, summary, note) });
  return { ok: true };
});

export const cancelRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const { requestId } = parse(RequestIdInput, call.data);
  const requestRef = requests().doc(requestId);
  await db().runTransaction(async (tx) => {
    const snap = await tx.get(requestRef);
    if (!snap.exists || snap.get("requesterId") !== uid) throw new HttpsError("not-found", errors.requestGone);
    if (snap.get("status") !== "pending") return;
    tx.update(requestRef, { status: "cancelled", decidedAt: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});
