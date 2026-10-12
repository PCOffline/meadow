import { DocumentData, DocumentReference, FieldValue, Transaction, UpdateData } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { chatNotes, errors, pushes } from "./copy";
import { consumeRate, db, inboxRef, loadPerson, notify, parse, postToChat, requests, requireAuth, users } from "./db";
import { chatIdFor, RequestType, summarize } from "./format";
import { isScheduleStricter, normalize, Schedule } from "./schedule";
import { CreateRequestInput, DecideRequestInput, RequestIdInput, SaveLimitInput } from "./schemas";

const MAX_PENDING = 10;
const REQUESTS_PER_HOUR = 10;
const HOUR_MS = 60 * 60_000;

export type RequestStatus = "pending" | "approved" | "denied" | "cancelled" | "expired";

/**
 * The full request, readable only by the person who asked. It names every Bumblebee asked;
 * each Bumblebee gets their own inbox copy without that list, so they never learn who else
 * looks after the same person.
 */
export interface RequestDoc {
  requesterId: string;
  requesterName: string;
  type: RequestType;
  packageName: string | null;
  appName: string | null;
  minutes: number;
  dailyMinutes: number;
  weeklyMinutes: number;
  schedule: Schedule | null;
  message: string;
  status: RequestStatus;
  approverIds: string[];
  dayKey: string;
}

/** The phone's local date is within a day of the server's UTC date, whatever its time zone. */
export function isAroundToday(dayKey: string, now: number): boolean {
  const day = Date.parse(`${dayKey}T00:00:00Z`);
  const today = Math.floor(now / 86_400_000) * 86_400_000;
  return Number.isFinite(day) && Math.abs(day - today) <= 86_400_000;
}

/** Who to ask: the chosen ones, which must all be the asker's Bumblebees, or all of them. */
export function pickApprovers(all: string[], chosen: string[] | null | undefined): string[] {
  if (!chosen) return [...all];
  const unique = [...new Set(chosen)];
  if (unique.some((uid) => !all.includes(uid))) throw new HttpsError("permission-denied", errors.notYourBumblebees);
  return unique;
}

/** What one Bumblebee's inbox holds about a request. */
function inboxCopy(id: string, r: RequestDoc, requesterFlower: string) {
  return {
    requestId: id,
    requesterId: r.requesterId,
    requesterName: r.requesterName,
    requesterFlower,
    type: r.type,
    packageName: r.packageName,
    appName: r.appName,
    minutes: r.minutes,
    dailyMinutes: r.dailyMinutes,
    weeklyMinutes: r.weeklyMinutes,
    schedule: r.schedule,
    message: r.message,
    status: r.status,
    dayKey: r.dayKey,
    myDecision: null,
    createdAt: FieldValue.serverTimestamp(),
  };
}

type Writer = { update(ref: DocumentReference, data: UpdateData<DocumentData>): unknown };

/**
 * Brings Bumblebees' inbox copies in line with the request; `decider` sees their own answer.
 * Pass only copies that exist: requests from before inboxes have none.
 */
export function updateInboxes(
  writer: Writer,
  id: string,
  approverIds: string[],
  status: RequestStatus,
  decider?: { uid: string; approve: boolean; grantedMinutes: number },
): void {
  for (const beeUid of approverIds) {
    const mine = decider?.uid === beeUid;
    writer.update(inboxRef(beeUid, id), {
      status,
      decidedAt: FieldValue.serverTimestamp(),
      // Others only learn that it was answered, never by whom.
      answeredByMe: mine,
      ...(mine ? { myDecision: decider!.approve ? "yes" : "no", grantedMinutes: decider!.grantedMinutes } : {}),
    });
  }
}

/** The Bumblebees among `approverIds` who hold an inbox copy of the request. */
export async function existingInboxes(tx: Transaction, requestId: string, approverIds: string[]): Promise<string[]> {
  if (approverIds.length === 0) return [];
  const snaps = await tx.getAll(...approverIds.map((b) => inboxRef(b, requestId)));
  return approverIds.filter((_, i) => snaps[i].exists);
}

export const createRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const input = parse(CreateRequestInput, call.data);
  if (!isAroundToday(input.dayKey, Date.now())) throw new HttpsError("invalid-argument", errors.badDate);
  const ref = requests().doc();

  const { doc, me } = await db().runTransaction(async (tx) => {
    const me = await loadPerson(uid, tx);
    if (me.account.approverIds.length === 0) throw new HttpsError("failed-precondition", errors.noBumblebees);
    const approverIds = pickApprovers(me.account.approverIds, input.approverIds);
    const commitRate = await consumeRate(tx, uid, "request", REQUESTS_PER_HOUR, HOUR_MS);
    const limit = await tx.get(users().doc(uid).collection("limits").doc(input.packageName));
    if (!limit.exists) throw new HttpsError("failed-precondition", errors.noLimit);
    // Read in the same transaction as the rate counter, so a double tap can't slip in twice.
    const pending = await tx.get(requests().where("requesterId", "==", uid).where("status", "==", "pending"));
    if (pending.size >= MAX_PENDING) throw new HttpsError("resource-exhausted", errors.tooManyRequests);
    if (pending.docs.some((d) => d.get("type") === input.type && (d.get("packageName") ?? null) === input.packageName)) {
      throw new HttpsError("already-exists", errors.duplicateRequest);
    }

    const doc: RequestDoc = {
      requesterId: uid,
      requesterName: me.profile.displayName,
      type: input.type,
      packageName: input.packageName,
      appName: input.appName || (limit.get("appName") as string),
      minutes: input.type === "extra_time" ? input.minutes : 0,
      dailyMinutes: input.type === "change_limit" ? input.dailyMinutes : 0,
      weeklyMinutes: input.type === "change_limit" ? input.weeklyMinutes : 0,
      schedule: input.type === "change_limit" ? normalize(input.schedule) : null,
      message: input.message,
      status: "pending",
      approverIds,
      dayKey: input.dayKey,
    };
    commitRate();
    tx.set(ref, { ...doc, createdAt: FieldValue.serverTimestamp() });
    for (const beeUid of approverIds) tx.set(inboxRef(beeUid, ref.id), inboxCopy(ref.id, doc, me.profile.flower));
    return { doc, me };
  });

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
          schedule: r.schedule ?? FieldValue.delete(),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
      break;
    case "remove_limit":
      tx.delete(requesterRef.collection("limits").doc(r.packageName!));
      break;
    case "disable_strict_mode":
      // Old requests only: strict mode now turns off without asking.
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

    const [requester, deciderPerson, inboxes] = await Promise.all([
      loadPerson(r.requesterId, tx), loadPerson(uid, tx), existingInboxes(tx, requestId, r.approverIds),
    ]);
    // Bumblebees who were removed or stepped down since the request was made can't answer it.
    if (!requester.account.approverIds.includes(uid)) throw new HttpsError("permission-denied", errors.noLongerBumblebee);
    const decider = deciderPerson.profile;

    const granted = approve && r.type === "extra_time" && minutes ? Math.min(minutes, r.minutes) : r.minutes;
    const status = approve ? "approved" : "denied";
    if (approve) applyApproval(tx, requestId, r, uid, granted);
    tx.update(requestRef, {
      status,
      decidedBy: uid,
      decidedByName: decider.displayName,
      decisionNote: note,
      decidedAt: FieldValue.serverTimestamp(),
      ...(r.type === "extra_time" && approve ? { minutes: granted } : {}),
    });
    updateInboxes(tx, requestId, inboxes, status, { uid, approve, grantedMinutes: approve ? granted : 0 });
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
    const inboxes = await existingInboxes(tx, requestId, (snap.get("approverIds") as string[] | undefined) ?? []);
    tx.update(requestRef, { status: "cancelled", decidedAt: FieldValue.serverTimestamp() });
    updateInboxes(tx, requestId, inboxes, "cancelled");
  });
  return { ok: true };
});

/**
 * Saves a limit whose allowed hours changed while the person has Bumblebees. Phones write other
 * limit changes themselves, but rules can't compare schedules, so this checks the change only
 * makes things stricter. Anything looser needs a request.
 */
export const saveLimit = onCall(async (call) => {
  const uid = requireAuth(call);
  const input = parse(SaveLimitInput, call.data);
  const ref = users().doc(uid).collection("limits").doc(input.packageName);
  await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const schedule = normalize(input.schedule);
    if (snap.exists) {
      const old = snap.data()!;
      const minutesStricter = (o: number, n: number) => o === 0 || (n > 0 && n <= o);
      const stricter = minutesStricter(old.dailyMinutes ?? 0, input.dailyMinutes)
        && minutesStricter(old.weeklyMinutes ?? 0, input.weeklyMinutes)
        && isScheduleStricter(old.schedule ?? null, schedule);
      if (!stricter) {
        const me = await loadPerson(uid, tx);
        if (me.account.approverIds.length > 0) throw new HttpsError("failed-precondition", errors.needsAYes);
      }
    }
    tx.set(ref, {
      packageName: input.packageName,
      appName: input.appName || snap.get("appName") || input.packageName,
      dailyMinutes: input.dailyMinutes,
      weeklyMinutes: input.weeklyMinutes,
      schedule: schedule ?? FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
      ...(snap.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
    }, { merge: true });
  });
  return { ok: true };
});
