import { test } from "node:test";
import assert from "node:assert/strict";
import { CreateRequestInput, DecideRequestInput, EmergencyInput, InviteByCodeInput, InviteTokenInput, ProfileInput } from "./schemas";

const firstError = (r: { success: boolean; error?: { issues: { message: string }[] } }) => r.error?.issues[0]?.message;

test("profiles trim names and fall back to a daisy", () => {
  const ok = ProfileInput.parse({ displayName: "  Sam  ", flower: "🦖" });
  assert.deepEqual(ok, { displayName: "Sam", flower: "🌼" });
  assert.equal(firstError(ProfileInput.safeParse({ displayName: "   " })), "Please choose a name.");
});

test("friend codes are normalised", () => {
  assert.equal(InviteByCodeInput.parse({ friendCode: " ab12cd " }).friendCode, "AB12CD");
});

test("invite tokens must look right", () => {
  assert.ok(InviteTokenInput.safeParse({ token: "AbCdEfGh12345678" }).success);
  assert.ok(!InviteTokenInput.safeParse({ token: "short" }).success);
  assert.ok(!InviteTokenInput.safeParse({ token: "AbCdEfGh1234567/" }).success);
});

test("requests are checked per type", () => {
  const dayKey = "2026-10-12";
  assert.ok(CreateRequestInput.safeParse({ type: "extra_time", packageName: "a.b", minutes: 15, dayKey }).success);
  assert.ok(!CreateRequestInput.safeParse({ type: "extra_time", packageName: "a.b", minutes: 0, dayKey }).success);
  assert.equal(
    firstError(CreateRequestInput.safeParse({ type: "change_limit", packageName: "a.b", dailyMinutes: 0, weeklyMinutes: 0, dayKey })),
    "Set a daily or weekly limit.",
  );
  assert.equal(firstError(CreateRequestInput.safeParse({ type: "add_approver", dayKey })), "That kind of request isn't supported.");
  assert.equal(firstError(CreateRequestInput.safeParse({ type: "remove_limit", packageName: "a.b", dayKey: "today" })), "That date doesn't look right.");
  // Extra fields from older clients are ignored rather than rejected.
  assert.ok(CreateRequestInput.safeParse({ type: "remove_limit", dayKey, packageName: "a.b", minutes: 0 }).success);
  // Strict mode turns off without asking now.
  assert.ok(!CreateRequestInput.safeParse({ type: "disable_strict_mode", dayKey }).success);
});

test("a request can name the Bumblebees to ask, and a new limit can be only hours", () => {
  const dayKey = "2026-10-12";
  assert.ok(CreateRequestInput.safeParse({ type: "extra_time", packageName: "a.b", minutes: 5, dayKey, approverIds: ["bee"] }).success);
  assert.equal(
    firstError(CreateRequestInput.safeParse({ type: "extra_time", packageName: "a.b", minutes: 5, dayKey, approverIds: [] })),
    "Pick at least one Bumblebee to ask.",
  );
  const evenings = { "1": [{ from: 600, to: 1320 }] };
  assert.ok(CreateRequestInput.safeParse({ type: "change_limit", packageName: "a.b", dailyMinutes: 0, weeklyMinutes: 0, schedule: evenings, dayKey }).success);
  assert.ok(!CreateRequestInput.safeParse({ type: "change_limit", packageName: "a.b", dailyMinutes: 30, weeklyMinutes: 0, schedule: { "9": [] }, dayKey }).success);
});

test("decisions accept an optional smaller amount", () => {
  assert.equal(DecideRequestInput.parse({ requestId: "r1", approve: true }).note, "");
  assert.equal(DecideRequestInput.parse({ requestId: "r1", approve: true, minutes: null }).minutes, null);
  assert.ok(!DecideRequestInput.safeParse({ requestId: "r1", approve: "yes" }).success);
});

test("emergency unlocks need a real reason and a set length", () => {
  assert.ok(EmergencyInput.safeParse({ minutes: 30, reason: "Need maps to get home" }).success);
  assert.equal(firstError(EmergencyInput.safeParse({ minutes: 45, reason: "Need maps to get home" })), "Pick 15, 30 or 60 minutes.");
  assert.equal(firstError(EmergencyInput.safeParse({ minutes: 15, reason: "pls" })), "Please say briefly what the emergency is.");
});
