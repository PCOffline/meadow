import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { chatNotes, emergencyHeadline, emergencyScope, errors, pushes } from "./copy";
import { accountOf, accountRef, consumeRate, db, notify, parse, postToChat, profileOf, requireAuth, users } from "./db";

// Emergency state lives in the owner-only account document; the record of each unlock in
// users/{uid}/emergencies, which Bumblebees may read since they're told about every unlock.
import { chatIdFor } from "./format";
import { EmergencyInput } from "./schemas";

/** Emergency unlocks skip approval, so they're rationed and always reported to Bumblebees. */
export const EMERGENCY_ALLOWANCE = 3;
export const EMERGENCY_WINDOW_MS = 30 * 24 * 60 * 60_000;
const MAX_OFFLINE_AGE_MS = 7 * 24 * 60 * 60_000;
/**
 * Unlocks recorded a day, offline reports included. The app makes at most four a month, so only a
 * modified one meets this; without it, it could post and push to every Bumblebee without end.
 */
export const EMERGENCY_REPORTS_PER_DAY = 6;
/** Unlock times kept on the account. Only whether the allowance is used up matters. */
export const MAX_STORED_USES = 20;

/** Start times of unlocks within the rolling window. */
export function recentUses(raw: unknown, now: number): number[] {
  const list = Array.isArray(raw) ? raw : [];
  return list
    .map((t) => (t instanceof Timestamp ? t.toMillis() : typeof t === "number" ? t : 0))
    .filter((ms) => ms > now - EMERGENCY_WINDOW_MS);
}

/**
 * The unlock times to store after one more at `startedAt`: the most recent ones, oldest first.
 * Offline unlocks can be reported late, so they're put in order before the oldest are dropped.
 */
export function withUse(uses: number[], startedAt: number): number[] {
  return [...uses, startedAt].sort((a, b) => a - b).slice(-MAX_STORED_USES);
}

/**
 * Lifts limits right away for up to an hour. With `startedAt` it records an unlock the phone
 * already took while offline; that one is recorded even beyond the allowance, since it happened.
 * Phones allow one offline unlock per window, and so does this: any further "offline" report is
 * recorded and told to Bumblebees, but lifts nothing. Otherwise a modified app could unlock
 * forever by claiming to be offline. Reports are rate limited (EMERGENCY_REPORTS_PER_DAY).
 */
export const emergencyUnlock = onCall(async (req) => {
  const uid = requireAuth(req);
  const input = parse(EmergencyInput, req.data);
  const { minutes, reason } = input;
  const packageName = input.packageName || null;
  const appName = input.appName || null;
  const now = Date.now();
  const offline = input.startedAt != null;
  const startedAt = input.startedAt == null ? now : Math.min(now, Math.max(now - MAX_OFFLINE_AGE_MS, input.startedAt));
  // An offline unlock that was ended early on the phone must not come back to life here.
  const endedAt = offline && input.endedAt != null ? Math.max(startedAt, input.endedAt) : Infinity;
  const userRef = users().doc(uid);
  const stateRef = accountRef(uid);
  // A retried report (the reply was lost) reuses its id, so it's recorded and announced once.
  const emergencyRef = input.reportId ? userRef.collection("emergencies").doc(input.reportId) : userRef.collection("emergencies").doc();

  const outcome = await db().runTransaction(async (tx) => {
    const [profileSnap, snap, existing] = await Promise.all([tx.get(userRef), tx.get(stateRef), tx.get(emergencyRef)]);
    const currentUntil = (snap.get("emergencyUntil") as Timestamp | undefined)?.toMillis() ?? 0;
    if (existing.exists) return { duplicate: true as const, until: currentUntil };
    const user = { ...profileOf(profileSnap), approverIds: accountOf(snap).approverIds };
    const uses = recentUses(snap.get("emergencyUses"), now);
    const overAllowance = uses.length >= EMERGENCY_ALLOWANCE;
    if (overAllowance && !offline) throw new HttpsError("resource-exhausted", errors.emergencyUsedUp(EMERGENCY_ALLOWANCE));
    if (!offline && currentUntil > now) throw new HttpsError("failed-precondition", errors.emergencyActive);
    const commitRate = await consumeRate(tx, uid, "emergency", EMERGENCY_REPORTS_PER_DAY, 24 * 60 * 60_000);
    const lastOffline = (snap.get("offlineEmergencyAt") as Timestamp | undefined)?.toMillis() ?? 0;
    const honoured = !offline || now - lastOffline > EMERGENCY_WINDOW_MS;
    const until = honoured ? Math.min(startedAt + minutes * 60_000, endedAt) : Math.min(now, endedAt);
    commitRate();
    tx.update(stateRef, {
      emergencyUses: withUse(uses, startedAt).map((ms) => Timestamp.fromMillis(ms)),
      emergencyUntil: Timestamp.fromMillis(honoured ? Math.max(until, currentUntil) : currentUntil),
      emergencyPackage: honoured ? packageName : (snap.get("emergencyPackage") ?? null),
      ...(offline && honoured ? { offlineEmergencyAt: Timestamp.fromMillis(now) } : {}),
    });
    tx.set(emergencyRef, {
      reason,
      minutes,
      packageName,
      appName,
      offline,
      overAllowance: overAllowance || !honoured,
      startedAt: Timestamp.fromMillis(startedAt),
      createdAt: FieldValue.serverTimestamp(),
    });
    return {
      duplicate: false as const,
      user,
      until,
      remaining: Math.max(0, EMERGENCY_ALLOWANCE - uses.length - 1),
      overAllowance: overAllowance || !honoured,
    };
  });
  if (outcome.duplicate) return { until: outcome.until, remaining: 0 };
  const { user, until, remaining, overAllowance } = outcome;

  const headline = emergencyHeadline(user, offline, overAllowance);
  const scope = emergencyScope(appName, minutes);
  await Promise.all(
    user.approverIds.map((beeUid) =>
      postToChat(chatIdFor(uid, beeUid), { senderId: uid, kind: "system", text: chatNotes.emergency(headline, scope, reason) }),
    ),
  );
  await notify(user.approverIds, { type: "emergency", uid, ...pushes.emergency(headline, scope, reason) });
  return { until, remaining };
});
