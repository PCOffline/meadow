import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { EMERGENCY_WINDOW_MS, recentUses } from "./emergency";

test("only unlocks inside the rolling window count", () => {
  const now = Date.now();
  const uses = recentUses([Timestamp.fromMillis(now - 1000), now - EMERGENCY_WINDOW_MS - 1, "junk", now - 5000], now);
  assert.deepEqual(uses, [now - 1000, now - 5000]);
  assert.deepEqual(recentUses(undefined, now), []);
});
