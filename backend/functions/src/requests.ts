import { FieldValue, Transaction } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import {
  chatIdFor, createInvite, db, input, int, notify, postToChat, requests, requireAuth, RequestDoc, RequestType,
  summarize, text, userOf, users, UserDoc,
} from "./shared";

const CLIENT_TYPES: RequestType[] = ["extra_time", "change_limit", "remove_limit", "disable_strict_mode", "remove_approver"];
const MAX_PENDING = 10;

type NewRequest = Omit<RequestDoc, "requesterId" | "requesterName" | "status" | "approverIds">;

/** Stores a request, drops a card into each approver's chat and pings them. */
export async function createApprovalRequest(uid: string, me: UserDoc, req: NewRequest): Promise<string> {
  let approverIds = [...me.approverIds];
  if (req.type === "remove_approver" && req.targetUid) {
    // The other approvers decide; if there are none, the approver being removed decides.
    const others = approverIds.filter((id) => id !== req.targetUid);
    approverIds = others.length > 0 ? others : [req.targetUid];
  }
  if (approverIds.length === 0) throw new HttpsError("failed-precondition", "Invite an approver first.");

  const ref = requests().doc();
  const doc: RequestDoc & { createdAt: FieldValue } = {
    ...req,
    requesterId: uid,
    requesterName: me.displayName,
    status: "pending",
    approverIds,
    createdAt: FieldValue.serverTimestamp(),
  };
  await ref.set(doc);
  const summary = summarize(doc);

  await Promise.all(
    approverIds.map((approverId) =>
      postToChat(chatIdFor(uid, approverId), {
        senderId: uid,
        kind: "request",
        text: `💌 ${me.displayName} asks: ${summary}`,
        requestId: ref.id,
      }),
    ),
  );
  await notify(approverIds, {
    type: "request_new",
    requestId: ref.id,
    title: `${me.flower} ${me.displayName} asks for ${summary}`,
    body: req.message ? `“${req.message}”` : "Tap to answer. You can also say yes right from here.",
  });
  return ref.id;
}

export const createRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const data = input(call);
  const type = data.type as RequestType;
  if (!CLIENT_TYPES.includes(type)) throw new HttpsError("invalid-argument", "Unknown request type.");

  const me = userOf(await users().doc(uid).get());
  if (me.approverIds.length === 0) throw new HttpsError("failed-precondition", "Invite an approver first.");

  const dayKey = text(data.dayKey, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dayKey)) throw new HttpsError("invalid-argument", "Bad date.");
  const message = text(data.message, 280);
  const packageName = text(data.packageName, 200) || null;
  let appName = text(data.appName, 100) || null;
  let minutes = 0;
  let dailyMinutes = 0;
  let weeklyMinutes = 0;
  let targetUid: string | null = null;
  let targetName: string | null = null;

  if (type === "extra_time" || type === "change_limit" || type === "remove_limit") {
    if (!packageName) throw new HttpsError("invalid-argument", "Which app?");
    const limit = await users().doc(uid).collection("limits").doc(packageName).get();
    if (!limit.exists) throw new HttpsError("failed-precondition", "That app doesn't have a limit.");
    appName = appName ?? (limit.get("appName") as string);
  }
  if (type === "extra_time") minutes = int(data.minutes, 1, 240, "Minutes");
  if (type === "change_limit") {
    dailyMinutes = int(data.dailyMinutes, 0, 1440, "Daily minutes");
    weeklyMinutes = int(data.weeklyMinutes, 0, 10080, "Weekly minutes");
    if (dailyMinutes === 0 && weeklyMinutes === 0) throw new HttpsError("invalid-argument", "Set a daily or weekly limit.");
  }
  if (type === "disable_strict_mode" && !me.strictMode) {
    throw new HttpsError("failed-precondition", "Strict mode is already off.");
  }
  if (type === "remove_approver") {
    targetUid = text(data.targetUid, 128);
    if (!me.approverIds.includes(targetUid)) throw new HttpsError("failed-precondition", "They're not your approver.");
    targetName = userOf(await users().doc(targetUid).get()).displayName;
  }

  const pending = await requests().where("requesterId", "==", uid).where("status", "==", "pending").get();
  if (pending.size >= MAX_PENDING) {
    throw new HttpsError("resource-exhausted", "You have lots of requests waiting already. Give your friends a moment 🌿");
  }
  const duplicate = pending.docs.some((d) =>
    d.get("type") === type && (d.get("packageName") ?? null) === packageName && (d.get("targetUid") ?? null) === targetUid,
  );
  if (duplicate) throw new HttpsError("already-exists", "You've already asked for this — it's waiting for an answer.");

  const requestId = await createApprovalRequest(uid, me, {
    type, packageName, appName, minutes, dailyMinutes, weeklyMinutes, targetUid, targetName, message, dayKey,
  });
  return { requestId };
});

/** Applies an approved request. Runs inside the decision transaction. */
function applyApproval(
  tx: Transaction,
  requestId: string,
  r: RequestDoc,
  deciderUid: string,
  grantedMinutes: number,
): void {
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
    case "remove_approver":
      tx.update(requesterRef, { approverIds: FieldValue.arrayRemove(r.targetUid) });
      tx.update(users().doc(r.targetUid!), { approveeIds: FieldValue.arrayRemove(r.requesterId) });
      break;
    case "add_approver":
      // The invite itself is sent after the transaction commits.
      break;
  }
}

export const decideRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const data = input(call);
  const requestId = text(data.requestId, 100);
  const approve = data.approve === true;
  const note = text(data.note, 200);
  const requestRef = requests().doc(requestId);

  const outcome = await db().runTransaction(async (tx) => {
    const snap = await tx.get(requestRef);
    if (!snap.exists) throw new HttpsError("not-found", "That request is gone.");
    const r = snap.data() as RequestDoc;
    if (r.status !== "pending") throw new HttpsError("failed-precondition", "That request was already answered.");
    if (r.requesterId === uid) throw new HttpsError("permission-denied", "You can't answer your own request 🌱");
    if (!r.approverIds.includes(uid)) throw new HttpsError("permission-denied", "You're not an approver for this.");

    const [requesterSnap, deciderSnap] = await Promise.all([
      tx.get(users().doc(r.requesterId)),
      tx.get(users().doc(uid)),
    ]);
    const requester = userOf(requesterSnap);
    const decider = userOf(deciderSnap);
    // Approvers who stepped down since the request was made can't answer it.
    const stillApprover = requester.approverIds.includes(uid) ||
      (r.type === "remove_approver" && r.targetUid === uid);
    if (!stillApprover) throw new HttpsError("permission-denied", "You're no longer their approver.");

    let grantedMinutes = r.minutes;
    if (approve && r.type === "extra_time" && typeof data.minutes === "number") {
      grantedMinutes = int(data.minutes, 1, r.minutes, "Minutes");
    }
    if (approve) applyApproval(tx, requestId, r, uid, grantedMinutes);
    tx.update(requestRef, {
      status: approve ? "approved" : "denied",
      decidedBy: uid,
      decidedByName: decider.displayName,
      decisionNote: note,
      decidedAt: FieldValue.serverTimestamp(),
      ...(r.type === "extra_time" && approve ? { minutes: grantedMinutes } : {}),
    });
    return { r: { ...r, minutes: grantedMinutes }, requester, decider };
  });

  const { r, requester, decider } = outcome;
  const summary = summarize(r);

  if (approve && r.type === "add_approver" && r.targetUid) {
    const target = userOf(await users().doc(r.targetUid).get());
    await createInvite(r.requesterId, requester, r.targetUid, target);
  }

  await postToChat(chatIdFor(uid, r.requesterId), {
    senderId: uid,
    kind: "system",
    text: approve ? `🌸 ${decider.displayName} said yes: ${summary}` : `🍃 ${decider.displayName} said not today: ${summary}`,
  });
  await notify([r.requesterId], {
    type: "request_decided",
    requestId,
    title: approve ? `🌸 ${decider.displayName} said yes!` : `🍃 ${decider.displayName} said not today`,
    body: note ? `${capitalize(summary)} — “${note}”` : capitalize(summary),
  });
  return { ok: true };
});

export const cancelRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const requestRef = requests().doc(text(input(call).requestId, 100));
  await db().runTransaction(async (tx) => {
    const snap = await tx.get(requestRef);
    if (!snap.exists || snap.get("requesterId") !== uid) throw new HttpsError("not-found", "That request is gone.");
    if (snap.get("status") !== "pending") return;
    tx.update(requestRef, { status: "cancelled", decidedAt: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
