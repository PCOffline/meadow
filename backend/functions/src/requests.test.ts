import { test } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { appRateKey, isAroundToday, isStale, pickApprovers, REQUEST_TTL_MS, usageLine } from "./requests";

test("requests are for today, give or take a time zone", () => {
  const now = Date.parse("2026-10-12T23:30:00Z");
  assert.ok(isAroundToday("2026-10-12", now));
  assert.ok(isAroundToday("2026-10-13", now), "a phone east of UTC is already on tomorrow");
  assert.ok(isAroundToday("2026-10-11", now), "a phone west of UTC is still on yesterday");
  assert.ok(!isAroundToday("2026-10-15", now));
  assert.ok(!isAroundToday("2027-01-01", now));
  assert.ok(!isAroundToday("2026-13-45", now));
});

test("a request goes to the chosen Bumblebees, which must be the asker's own", () => {
  assert.deepEqual(pickApprovers(["a", "b", "c"], null), ["a", "b", "c"]);
  assert.deepEqual(pickApprovers(["a", "b", "c"], ["b", "b"]), ["b"]);
  assert.throws(() => pickApprovers(["a", "b"], ["a", "stranger"]), /own Bumblebees/);
});

test("requests expire half an hour after they're sent", () => {
  const now = Date.parse("2026-10-12T12:00:00Z");
  assert.equal(REQUEST_TTL_MS, 30 * 60_000);
  assert.ok(!isStale(Timestamp.fromMillis(now - 29 * 60_000), now));
  assert.ok(isStale(Timestamp.fromMillis(now - 30 * 60_000), now));
  assert.ok(!isStale(null, now), "a request still being written isn't stale");
});

test("each app has its own request counter, with a key Firestore accepts", () => {
  assert.equal(appRateKey("com.instagram.android"), "request:com_instagram_android");
  assert.notEqual(appRateKey("com.a"), appRateKey("com.b"));
  assert.equal(appRateKey(null), "request:none");
});

test("Bumblebees see how the day stands", () => {
  assert.equal(usageLine({ usedMinutes: 45, allowedMinutes: 60 }), "Used 45m of 1h today.");
  assert.equal(usageLine({ usedMinutes: 90, allowedMinutes: 0 }), "Used 1h 30m today.");
  assert.equal(usageLine({ usedMinutes: null, allowedMinutes: 60 }), null);
});
