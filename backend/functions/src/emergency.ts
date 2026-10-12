import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { chatIdFor, db, notify, parse, postToChat, requireAuth, userOf, users, UserDoc } from "./shared";

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
const EmergencyInput = z.object({
  minutes: z.number().int().refine((m) => ALLOWED_MINUTES.includes(m), "Pick 15, 30 or 60 minutes."),
  reason: z.string().trim().min(10, "Please say briefly what the emergency is.").max(280),
  packageName: z.string().trim().max(200).nullish(),
  appName: z.string().trim().max(100).nullish(),
  // Set only when reporting an unlock the phone already took while offline.
  startedAt: z.number().int().positive().nullish(),
  endedAt: z.number().int().positive().nullish(),
});

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

  const result = await db().runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    const user = userOf(snap);
    const uses = recentUses(snap.get("emergencyUses"), now);
    const overAllowance = uses.length >= EMERGENCY_ALLOWANCE;
    if (overAllowance && !offline) {
      throw new HttpsError(
        "resource-exhausted",
        `You've used all ${EMERGENCY_ALLOWANCE} emergency unlocks for the last 30 days. Ask a Petal Pal for time instead.`,
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
      appName,
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
    appName,
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
