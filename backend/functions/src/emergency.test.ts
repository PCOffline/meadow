import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { EMERGENCY_ALLOWANCE, EMERGENCY_WINDOW_MS, MAX_STORED_USES, recentUses, withUse } from "./emergency";

test("only unlocks inside the rolling window count", () => {
  const now = Date.now();
  const uses = recentUses([Timestamp.fromMillis(now - 1000), now - EMERGENCY_WINDOW_MS - 1, "junk", now - 5000], now);
  assert.deepEqual(uses, [now - 1000, now - 5000]);
  assert.deepEqual(recentUses(undefined, now), []);
});

test("stored unlock times stay few, keeping the most recent so the allowance still counts", () => {
  const now = Date.now();
  let uses: number[] = [];
  for (let i = 0; i < 100; i++) uses = withUse(recentUses(uses, now), now - i * 60_000);
  assert.equal(uses.length, MAX_STORED_USES);
  assert.ok(MAX_STORED_USES >= EMERGENCY_ALLOWANCE);
  // The newest are kept, oldest first, even when a late offline report is older than the rest.
  assert.deepEqual(uses, Array.from({ length: MAX_STORED_USES }, (_, i) => now - (MAX_STORED_USES - 1 - i) * 60_000));
  assert.deepEqual(withUse([now - 10, now], now - 20), [now - 20, now - 10, now]);
});
