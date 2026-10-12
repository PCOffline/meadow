import { test } from "node:test";
import assert from "node:assert/strict";
import { isAroundToday } from "./requests";

test("requests are for today, give or take a time zone", () => {
  const now = Date.parse("2026-10-12T23:30:00Z");
  assert.ok(isAroundToday("2026-10-12", now));
  assert.ok(isAroundToday("2026-10-13", now), "a phone east of UTC is already on tomorrow");
  assert.ok(isAroundToday("2026-10-11", now), "a phone west of UTC is still on yesterday");
  assert.ok(!isAroundToday("2026-10-15", now));
  assert.ok(!isAroundToday("2027-01-01", now));
  assert.ok(!isAroundToday("2026-13-45", now));
});
