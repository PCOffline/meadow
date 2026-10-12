import { test } from "node:test";
import assert from "node:assert/strict";
import { isScheduleStricter, normalize, ScheduleSchema } from "./schedule";

const every = (from: number, to: number) => Object.fromEntries(["1", "2", "3", "4", "5", "6", "7"].map((d) => [d, [{ from, to }]]));

test("adding hours to no schedule is stricter; removing a schedule is looser", () => {
  assert.equal(isScheduleStricter(null, every(600, 960)), true);
  assert.equal(isScheduleStricter(every(600, 960), null), false);
  assert.equal(isScheduleStricter(null, null), true);
});

test("narrower windows are stricter, wider or moved ones are not", () => {
  assert.equal(isScheduleStricter(every(600, 1320), every(600, 960)), true);
  assert.equal(isScheduleStricter(every(600, 960), every(540, 960)), false);
  assert.equal(isScheduleStricter(every(600, 960), every(900, 1000)), false);
});

test("a missing day allows all of it, an empty one none of it", () => {
  assert.equal(isScheduleStricter({ "6": [] }, {}), false);
  assert.equal(isScheduleStricter({}, { "6": [] }), true);
  assert.equal(isScheduleStricter({ "1": [{ from: 0, to: 600 }, { from: 600, to: 1440 }] }, {}), true, "touching windows cover the day");
});

test("windows must end after they start and days must be real", () => {
  assert.equal(ScheduleSchema.safeParse({ "1": [{ from: 600, to: 600 }] }).success, false);
  assert.equal(ScheduleSchema.safeParse({ "8": [] }).success, false);
  assert.equal(ScheduleSchema.safeParse({ "1": [{ from: 0, to: 1440 }] }).success, true);
  assert.deepEqual(normalize({ "1": [{ from: 700, to: 800 }, { from: 600, to: 750 }] }), { "1": [{ from: 600, to: 800 }] });
});
