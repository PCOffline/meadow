import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { chatNotes, emergencyHeadline, emergencyScope, errors, pushes } from "./copy";
import { db, notify, parse, postToChat, requireAuth, userOf, users } from "./db";
import { chatIdFor } from "./format";
import { EmergencyInput } from "./schemas";

/** Emergency unlocks skip approval, so they're rationed and always reported to Bumblebees. */
export const EMERGENCY_ALLOWANCE = 3;
export const EMERGENCY_WINDOW_MS = 30 * 24 * 60 * 60_000;
const MAX_OFFLINE_AGE_MS = 7 * 24 * 60 * 60_000;

/** Start times of unlocks within the rolling window. */
export function recentUses(raw: unknown, now: number): number[] {
  const list = Array.isArray(raw) ? raw : [];
  return list
    .map((t) => (t instanceof Timestamp ? t.toMillis() : typeof t === "number" ? t : 0))
    .filter((ms) => ms > now - EMERGENCY_WINDOW_MS);
}

/**
 * Lifts limits right away for up to an hour. With `startedAt` it records an unlock the phone
 * already took while offline; that one is recorded even beyond the allowance, since it happened.
 */
export const emergencyUnlock = onCall(async (req) => {
  const uid = requireAuth(req);
  const input = parse(EmergencyInput, req.data);
  const { minutes, reason } = input;
  const packageName = input.packageName || null;
  const appName = input.appName || null;
  const now = Date.now();
  const offline = input.startedAt != null;
  const startedAt = offline ? Math.min(now, Math.max(now - MAX_OFFLINE_AGE_MS, input.startedAt!)) : now;
  // An offline unlock that was ended early on the phone must not come back to life here.
  const endedAt = offline && input.endedAt != null ? Math.max(startedAt, input.endedAt) : Infinity;
  const until = Math.min(startedAt + minutes * 60_000, endedAt);
  const userRef = users().doc(uid);

  const { user, remaining, overAllowance } = await db().runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    const user = userOf(snap);
    const uses = recentUses(snap.get("emergencyUses"), now);
    const overAllowance = uses.length >= EMERGENCY_ALLOWANCE;
    if (overAllowance && !offline) throw new HttpsError("resource-exhausted", errors.emergencyUsedUp(EMERGENCY_ALLOWANCE));
    const currentUntil = (snap.get("emergencyUntil") as Timestamp | undefined)?.toMillis() ?? 0;
    if (!offline && currentUntil > now) throw new HttpsError("failed-precondition", errors.emergencyActive);
    tx.update(userRef, {
      emergencyUses: [...uses, startedAt].map((ms) => Timestamp.fromMillis(ms)),
      emergencyUntil: Timestamp.fromMillis(Math.max(until, currentUntil)),
      emergencyPackage: packageName,
    });
    tx.set(userRef.collection("emergencies").doc(), {
      reason,
      minutes,
      packageName,
      appName,
      offline,
      overAllowance,
      startedAt: Timestamp.fromMillis(startedAt),
      createdAt: FieldValue.serverTimestamp(),
    });
    return { user, remaining: Math.max(0, EMERGENCY_ALLOWANCE - uses.length - 1), overAllowance };
  });

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
