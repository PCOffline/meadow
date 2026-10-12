import { DocumentData, DocumentReference, FieldValue, Timestamp, Transaction, UpdateData } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { chatNotes, errors, pushes } from "./copy";
import { consumeRates, db, inboxRef, loadPerson, notify, parse, postToChat, requests, requireAuth, users } from "./db";
import { chatIdFor, formatMinutes, RequestType, summarize } from "./format";
import { isScheduleStricter, normalize, Schedule } from "./schedule";
import { CreateRequestInput, DecideRequestInput, RequestIdInput, SaveLimitInput } from "./schemas";

const MAX_PENDING = 10;
const REQUESTS_PER_HOUR = 10;
/** Asking about the same app again and again: at most this many an hour, whatever the answers. */
const REQUESTS_PER_APP_PER_HOUR = 3;
const HOUR_MS = 60 * 60_000;
/** An unanswered request stops counting after this long: the moment it was about has passed. */
export const REQUEST_TTL_MS = 30 * 60_000;

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
  /** Time already spent on the app today, and how much today allows, as the phone saw it. */
  usedMinutes: number | null;
  allowedMinutes: number | null;
}

/** Whether a pending request has outlived [REQUEST_TTL_MS]. One still being written isn't. */
export function isStale(createdAt: Timestamp | undefined | null, now: number): boolean {
  return createdAt != null && now - createdAt.toMillis() >= REQUEST_TTL_MS;
}

/** The rate-limit key for requests about one app. Field paths can't hold dots. */
export function appRateKey(packageName: string | null): string {
  return `request:${(packageName ?? "none").replace(/[^A-Za-z0-9_]/g, "_")}`;
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
    usedMinutes: r.usedMinutes,
    allowedMinutes: r.allowedMinutes,
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
    const commitRate = await consumeRates(tx, uid, [
      { key: "request", max: REQUESTS_PER_HOUR, windowMs: HOUR_MS },
      { key: appRateKey(input.packageName), max: REQUESTS_PER_APP_PER_HOUR, windowMs: HOUR_MS },
    ]);
    const limit = await tx.get(users().doc(uid).collection("limits").doc(input.packageName));
    if (!limit.exists) throw new HttpsError("failed-precondition", errors.noLimit);
    // Read in the same transaction as the rate counter, so a double tap can't slip in twice.
    const now = Date.now();
    const pending = (await tx.get(requests().where("requesterId", "==", uid).where("status", "==", "pending")))
      .docs.filter((d) => !isStale(d.get("createdAt"), now));
    if (pending.length >= MAX_PENDING) throw new HttpsError("resource-exhausted", errors.tooManyRequests);
    // One open question per app at a time, whatever kind.
    if (pending.some((d) => (d.get("packageName") ?? null) === input.packageName)) {
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
      usedMinutes: input.usedMinutes ?? null,
      allowedMinutes: input.allowedMinutes ?? null,
    };
    commitRate();
    tx.set(ref, { ...doc, createdAt: FieldValue.serverTimestamp() });
    for (const beeUid of approverIds) tx.set(inboxRef(beeUid, ref.id), inboxCopy(ref.id, doc, me.profile.flower));
    return { doc, me };
  });

  const summary = summarize(doc);
  await Promise.all(
    doc.approverIds.map((beeUid) =>
      postToChat(chatIdFor(uid, beeUid), { senderId: uid, kind: "request", text: chatNotes.request(me.profile, summary, usageLine(doc)), requestId: ref.id }),
    ),
  );
  await notify(doc.approverIds, {
    type: "request_new",
    requestId: ref.id,
    ...pushes.request(me.profile, summary, doc.message, usageLine(doc)),
  });
  return { requestId: ref.id };
});

/** "Used 45m of 1h today", for the Bumblebee deciding. Null when the phone didn't say. */
export function usageLine(r: Pick<RequestDoc, "usedMinutes" | "allowedMinutes">): string | null {
  if (r.usedMinutes == null) return null;
  return r.allowedMinutes != null && r.allowedMinutes > 0
    ? `Used ${formatMinutes(r.usedMinutes)} of ${formatMinutes(r.allowedMinutes)} today.`
    : `Used ${formatMinutes(r.usedMinutes)} today.`;
}

/**
 * Settles pending requests that outlived [REQUEST_TTL_MS], for the asker and every Bumblebee.
 * Decisions on them are refused anyway; this keeps everyone's lists honest.
 */
export async function expireStaleRequests(now: number): Promise<number> {
  const stale = await requests()
    .where("status", "==", "pending")
    .where("createdAt", "<", Timestamp.fromMillis(now - REQUEST_TTL_MS))
    .limit(400)
    .get();
  for (const d of stale.docs) {
    await db().runTransaction(async (tx) => {
      const snap = await tx.get(d.ref);
      if (snap.get("status") !== "pending") return;
      const inboxes = await existingInboxes(tx, d.id, (snap.get("approverIds") as string[] | undefined) ?? []);
      tx.update(d.ref, { status: "expired", decidedAt: FieldValue.serverTimestamp() });
      updateInboxes(tx, d.id, inboxes, "expired");
    }).catch((e) => logger.warn("Could not expire request", { id: d.id, error: String(e) }));
  }
  return stale.size;
}

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

  const result = await db().runTransaction(async (tx) => {
    const snap = await tx.get(requestRef);
    if (!snap.exists) throw new HttpsError("not-found", errors.requestGone);
    const r = snap.data() as RequestDoc;
    if (r.requesterId === uid) throw new HttpsError("permission-denied", errors.ownRequest);
    if (!r.approverIds.includes(uid)) throw new HttpsError("permission-denied", errors.notYourRequest);
    // The first answer settles it for everyone asked.
    if (r.status !== "pending") throw new HttpsError("failed-precondition", errors.requestAnswered);

    const [requester, deciderPerson, inboxes] = await Promise.all([
      loadPerson(r.requesterId, tx), loadPerson(uid, tx), existingInboxes(tx, requestId, r.approverIds),
    ]);
    // Too late to answer: settle it as expired now rather than waiting for the sweep.
    if (isStale(snap.get("createdAt"), Date.now())) {
      tx.update(requestRef, { status: "expired", decidedAt: FieldValue.serverTimestamp() });
      updateInboxes(tx, requestId, inboxes, "expired");
      return null;
    }
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
  if (!result) throw new HttpsError("failed-precondition", errors.requestExpired);
  const { r, decider } = result;

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
