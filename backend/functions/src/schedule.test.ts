import { test } from "node:test";
import assert from "node:assert/strict";
import { CapsSchema, DayMinutesSchema, describeCaps, describeDayMinutes, describeDays, isLimitStricter, isScheduleStricter, normalize, normalizeCaps, normalizeDayMinutes, ScheduleSchema } from "./schedule";

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

const limit = (daily: number, extra: object = {}) => ({ dailyMinutes: daily, weeklyMinutes: 0, ...extra });
const weekdays = [1, 2, 3, 4, 5];
const m = (n: number) => `${n}m`;

test("a day's own limit replaces the everyday one: lower is stricter, higher or none is not", () => {
  assert.equal(isLimitStricter(limit(30), limit(30, { dayMinutes: { "6": 20 } })), true);
  assert.equal(isLimitStricter(limit(30), limit(30, { dayMinutes: { "6": 120 } })), false);
  assert.equal(isLimitStricter(limit(30), limit(30, { dayMinutes: { "6": 0 } })), false, "no limit on Saturday is looser");
  assert.equal(isLimitStricter(limit(30, { dayMinutes: { "6": 120 } }), limit(30)), true, "dropping a longer weekend is stricter");
  assert.equal(isLimitStricter(limit(30, { dayMinutes: { "6": 20 } }), limit(30)), false, "dropping a shorter Saturday is looser");
  assert.equal(isLimitStricter(limit(0, { dayMinutes: { "1": 60 } }), limit(45)), true, "an everyday limit can stand in for a day's own");
});

test("a cap is kept by a tighter cap over at least its hours, or a daily limit no longer than it", () => {
  const cap = { days: weekdays, from: 600, to: 960, minutes: 30 };
  assert.equal(isLimitStricter(limit(60), limit(60, { caps: [cap] })), true, "adding a cap is stricter");
  assert.equal(isLimitStricter(limit(60, { caps: [cap] }), limit(60)), false, "removing one is not");
  assert.equal(isLimitStricter(limit(60, { caps: [cap] }), limit(60, { caps: [{ ...cap, minutes: 20 }] })), true);
  assert.equal(isLimitStricter(limit(60, { caps: [cap] }), limit(60, { caps: [{ ...cap, minutes: 45 }] })), false);
  assert.equal(isLimitStricter(limit(60, { caps: [cap] }), limit(60, { caps: [{ ...cap, from: 540, to: 1020 }] })), true, "wider hours hold back more");
  assert.equal(isLimitStricter(limit(60, { caps: [cap] }), limit(60, { caps: [{ ...cap, from: 660 }] })), false, "narrower hours hold back less");
  assert.equal(isLimitStricter(limit(60, { caps: [cap] }), limit(60, { caps: [{ ...cap, days: [1, 2, 3] }] })), false, "fewer days is looser");
  assert.equal(isLimitStricter(limit(60, { caps: [cap] }), limit(25)), true, "25m a day is within any 30m cap");
  assert.equal(isLimitStricter(limit(60, { caps: [cap] }), limit(60, { dayMinutes: { "1": 30, "2": 30, "3": 30, "4": 30, "5": 30 } })), true);
});

test("the week and the hours still count", () => {
  assert.equal(isLimitStricter({ dailyMinutes: 0, weeklyMinutes: 300 }, { dailyMinutes: 0, weeklyMinutes: 400 }), false);
  assert.equal(isLimitStricter(limit(30, { schedule: every(600, 960) }), limit(30)), false);
  assert.equal(isLimitStricter(limit(30), limit(30, { schedule: every(600, 960) })), true);
});

test("days and caps are checked and kept tidy", () => {
  assert.equal(DayMinutesSchema.safeParse({ "6": 120 }).success, true);
  assert.equal(DayMinutesSchema.safeParse({ "8": 120 }).success, false);
  assert.equal(DayMinutesSchema.safeParse({ "6": 1441 }).success, false);
  const cap = { days: [5, 1, 1], from: 600, to: 960, minutes: 30 };
  assert.equal(CapsSchema.safeParse([cap]).success, true);
  assert.equal(CapsSchema.safeParse([{ ...cap, to: 600 }]).success, false, "hours must end after they start");
  assert.equal(CapsSchema.safeParse([{ ...cap, minutes: 360 }]).success, false, "a cap as long as its hours holds nothing back");
  assert.equal(CapsSchema.safeParse([{ ...cap, days: [] }]).success, false);
  assert.equal(CapsSchema.safeParse([{ ...cap, days: [0] }]).success, false);
  assert.equal(CapsSchema.safeParse(Array(7).fill(cap)).success, false);
  assert.equal(CapsSchema.safeParse([{ ...cap, extra: 1 }]).success, false);
  assert.deepEqual(normalizeCaps([cap]), [{ days: [1, 5], from: 600, to: 960, minutes: 30 }]);
  assert.equal(normalizeCaps([]), null);
  assert.equal(normalizeCaps(null), null);
  assert.deepEqual(normalizeDayMinutes({ "7": 60, "6": 60 }), { "6": 60, "7": 60 });
  assert.equal(normalizeDayMinutes({}), null);
  assert.equal(normalizeDayMinutes(undefined), null);
});

test("days, a day's own limits and caps read as words", () => {
  assert.equal(describeDays([1, 2, 3, 4, 5, 6, 7]), "every day");
  assert.equal(describeDays([5, 4, 3, 2, 1]), "on weekdays");
  assert.equal(describeDays([6, 7]), "at weekends");
  assert.equal(describeDays([1, 3, 5]), "on Mon, Wed and Fri");
  assert.equal(describeDays([2]), "on Tue");
  assert.deepEqual(describeDayMinutes({ "6": 120, "7": 120, "3": 0 }, m), ["no limit on Wed", "120m at weekends"]);
  assert.deepEqual(describeDayMinutes(null, m), []);
  assert.deepEqual(describeCaps([{ days: weekdays, from: 600, to: 960, minutes: 30 }], m), ["up to 30m 10:00–16:00 on weekdays"]);
  assert.deepEqual(describeCaps(undefined, m), []);
});
