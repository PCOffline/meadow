import { onCall } from "firebase-functions/v2/https";
import { pushes } from "./copy";
import { consumeRate, db, type PushResult, pushTo, requireAuth } from "./db";

/** Enough to try again after changing a setting, not enough to buzz a phone all day. */
export const TEST_PUSHES_PER_HOUR = 5;
/** The channels people most often find silent, each sent so its own setting is checked. */
export const TEST_CHANNELS = ["chat", "requests"] as const;

/**
 * Sends the caller one test notification per channel in [TEST_CHANNELS], to their own phones
 * only, and says how many phones FCM took it for. Lets someone whose chat or request
 * notifications never show tell whether the push or the phone is at fault.
 */
export const sendTestPush = onCall(async (req): Promise<PushResult> => {
  const uid = requireAuth(req);
  await db().runTransaction(async (tx) => (await consumeRate(tx, uid, "testPush", TEST_PUSHES_PER_HOUR, 60 * 60_000))());
  const results: PushResult[] = [];
  for (const channel of TEST_CHANNELS) {
    results.push(await pushTo(uid, { type: "test", channel, ...pushes.test(channel) }));
  }
  return {
    phones: Math.max(...results.map((r) => r.phones)),
    delivered: Math.min(...results.map((r) => r.delivered)),
    errors: [...new Set(results.flatMap((r) => r.errors))],
  };
});
