import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { chatIdFor, db, input, int, notify, postToChat, requireAuth, text, userOf, users, UserDoc } from "./shared";

/** Emergency unlocks skip approval, so they are rationed and always reported to approvers. */
export const EMERGENCY_ALLOWANCE = 3;
export const EMERGENCY_WINDOW_MS = 30 * 24 * 60 * 60_000;
const ALLOWED_MINUTES = [15, 30, 60];
const MAX_OFFLINE_AGE_MS = 7 * 24 * 60 * 60_000;

function recentUses(raw: unknown, now: number): number[] {
  const list = Array.isArray(raw) ? raw : [];
  return list
    .map((t) => (t instanceof Timestamp ? t.toMillis() : typeof t === "number" ? t : 0))
    .filter((ms) => ms > now - EMERGENCY_WINDOW_MS);
}

/**
 * Lifts limits right away for up to an hour. With `startedAt` it records an unlock the phone
 * already took while offline: that one is reported even if it went over the allowance,
 * because it has already happened.
 */
export const emergencyUnlock = onCall(async (req) => {
  const uid = requireAuth(req);
  const data = input(req);
  const minutes = int(data.minutes, 1, 60, "Minutes");
  if (!ALLOWED_MINUTES.includes(minutes)) throw new HttpsError("invalid-argument", "Pick 15, 30 or 60 minutes.");
  const reason = text(data.reason, 280);
  if (reason.length < 10) throw new HttpsError("invalid-argument", "Please say briefly what the emergency is.");
  const packageName = text(data.packageName, 200) || null;
  const now = Date.now();
  const offline = typeof data.startedAt === "number";
  const startedAt = offline ? Math.min(now, Math.max(now - MAX_OFFLINE_AGE_MS, data.startedAt as number)) : now;
  // An offline unlock that was ended early on the phone must not come back to life here.
  const endedAt = offline && typeof data.endedAt === "number" ? Math.max(startedAt, data.endedAt as number) : Infinity;
  const until = Math.min(startedAt + minutes * 60_000, endedAt);
  const userRef = users().doc(uid);

  const result = await db().runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    const user = userOf(snap);
    const uses = recentUses(snap.get("emergencyUses"), now);
    const overAllowance = uses.length >= EMERGENCY_ALLOWANCE;
    if (overAllowance && !offline) {
      throw new HttpsError(
        "resource-exhausted",
        `You've used all ${EMERGENCY_ALLOWANCE} emergency unlocks for the last 30 days. Ask an approver for time instead.`,
      );
    }
    const currentUntil = (snap.get("emergencyUntil") as Timestamp | undefined)?.toMillis() ?? 0;
    if (!offline && currentUntil > now) {
      throw new HttpsError("failed-precondition", "An emergency unlock is already active.");
    }
    tx.update(userRef, {
      emergencyUses: [...uses, startedAt].map((ms) => Timestamp.fromMillis(ms)),
      emergencyUntil: Timestamp.fromMillis(Math.max(until, currentUntil)),
      emergencyPackage: packageName,
    });
    tx.set(userRef.collection("emergencies").doc(), {
      reason,
      minutes,
      packageName,
      appName: text(data.appName, 100) || null,
      offline,
      overAllowance,
      startedAt: Timestamp.fromMillis(startedAt),
      createdAt: FieldValue.serverTimestamp(),
    });
    return { user, remaining: Math.max(0, EMERGENCY_ALLOWANCE - uses.length - 1), overAllowance };
  });

  await announce(uid, result.user, {
    minutes,
    reason,
    appName: text(data.appName, 100) || null,
    offline,
    overAllowance: result.overAllowance,
  });
  return { until, remaining: result.remaining };
});

async function announce(
  uid: string,
  user: UserDoc,
  e: { minutes: number; reason: string; appName: string | null; offline: boolean; overAllowance: boolean },
): Promise<void> {
  const scope = e.appName ? `${e.appName} for ${e.minutes} minutes` : `all limits for ${e.minutes} minutes`;
  const extra = [e.offline ? "while offline" : null, e.overAllowance ? "beyond their monthly allowance" : null]
    .filter(Boolean).join(", ");
  const headline = `🚨 ${user.displayName} used an emergency unlock${extra ? ` (${extra})` : ""}`;
  await Promise.all(
    user.approverIds.map((approverId) =>
      postToChat(chatIdFor(uid, approverId), {
        senderId: uid,
        kind: "system",
        text: `${headline}: lifted ${scope}. Reason: “${e.reason}”`,
      }),
    ),
  );
  await notify(user.approverIds, {
    type: "emergency",
    uid,
    title: headline,
    body: `Lifted ${scope}. “${e.reason}”`,
  });
}
