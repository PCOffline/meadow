import { test } from "node:test";
import assert from "node:assert/strict";
import {
  CreateRequestInput, DecideRequestInput, EmergencyInput, InviteByCodeInput, InviteIdInput, InviteTokenInput, ProfileInput, RemoveBumblebeeInput,
  isPackageName, RequestIdInput, SaveLimitInput,
} from "./schemas";

const firstError = (r: { success: boolean; error?: { issues: { message: string }[] } }) => r.error?.issues[0]?.message;

test("profiles trim names and fall back to a daisy", () => {
  const ok = ProfileInput.parse({ displayName: "  Sam  ", flower: "🦖" });
  assert.deepEqual(ok, { displayName: "Sam", flower: "🌼" });
  assert.equal(firstError(ProfileInput.safeParse({ displayName: "   " })), "Please choose a name.");
});

test("friend codes are normalised", () => {
  assert.equal(InviteByCodeInput.parse({ friendCode: " ab12cd " }).friendCode, "AB12CD");
});

test("nothing a caller sends can become a different document path", () => {
  for (const bad of ["../../users/x", "a/b", ".", "..", "", " ", "x".repeat(129)]) {
    assert.ok(!RequestIdInput.safeParse({ requestId: bad }).success, bad);
    assert.ok(!InviteIdInput.safeParse({ inviteId: bad }).success, bad);
    assert.ok(!RemoveBumblebeeInput.safeParse({ approverUid: bad }).success, bad);
    assert.ok(!InviteByCodeInput.safeParse({ friendCode: `AB/${bad}CD` }).success, bad);
  }
  for (const bad of ["a/b", "../x", ".", "a..b", ".a", "a.", "com.app/../../x"]) {
    assert.equal(firstError(SaveLimitInput.safeParse({ packageName: bad, dailyMinutes: 30, weeklyMinutes: 0 })), "Which app is this about?", bad);
  }
  assert.ok(RequestIdInput.safeParse({ requestId: "Xy3_k-9AbCdEfGh12345" }).success);
  assert.ok(SaveLimitInput.safeParse({ packageName: "com.example.app_2", dailyMinutes: 30, weeklyMinutes: 0 }).success);
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

test("a limit can be only some days' own limits, or only a cap within hours", () => {
  const base = { packageName: "a.b", dailyMinutes: 0, weeklyMinutes: 0 };
  assert.ok(SaveLimitInput.safeParse({ ...base, dayMinutes: { "6": 120 } }).success);
  assert.equal(firstError(SaveLimitInput.safeParse({ ...base, dayMinutes: { "6": 0 } })), "Set a daily or weekly limit.", "no limit on one day is no limit");
  assert.ok(SaveLimitInput.safeParse({ ...base, caps: [{ days: [1], from: 600, to: 960, minutes: 30 }] }).success);
  assert.equal(firstError(SaveLimitInput.safeParse({ ...base, caps: [] })), "Set a daily or weekly limit.");
  assert.ok(SaveLimitInput.safeParse({ ...base, dailyMinutes: 30, dayMinutes: null, caps: null }).success);
  assert.ok(!SaveLimitInput.safeParse({ ...base, dailyMinutes: 30, caps: [{ days: [1], from: 600, to: 960, minutes: 400 }] }).success);
});

test("a request can say how the day stands", () => {
  const dayKey = "2026-10-12";
  const ask = { type: "extra_time", packageName: "a.b", minutes: 15, dayKey };
  const parsed = CreateRequestInput.parse({ ...ask, usedMinutes: 45, allowedMinutes: 60 });
  assert.equal(parsed.usedMinutes, 45);
  assert.equal(parsed.allowedMinutes, 60);
  assert.ok(CreateRequestInput.safeParse(ask).success, "older phones don't send it");
  assert.ok(!CreateRequestInput.safeParse({ ...ask, usedMinutes: -1 }).success);
  assert.ok(!CreateRequestInput.safeParse({ ...ask, usedMinutes: 2000 }).success);
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

test("only package names are looked up as limits", () => {
  assert.ok(isPackageName("com.instagram.android"));
  for (const bad of ["", "com/instagram", "..", "com..insta", "Maps app"]) assert.equal(isPackageName(bad), false, bad);
});

test("a website is limited under its domain, in lower case, and nothing else passes for one", () => {
  for (const ok of ["site:youtube.com", "site:m.youtube.com", "site:xn--bcher-kva.de", "site:my-site.co.uk", "site:a1.io"]) {
    assert.ok(isPackageName(ok), ok);
  }
  const bad = [
    "site:", "site:youtube", "site:YouTube.com", "site:-bad.com", "site:bad-.com", "site:you tube.com", "site:youtube.com/",
    "site:youtube.com/watch", "site:https://youtube.com", "site:a..com", "site:.com", "site:youtube.com.", `site:${"a".repeat(64)}.com`,
    "site:youtube.com:443", "site:you_tube.com", "site:../x.com", "web:youtube.com",
  ];
  for (const b of bad) assert.equal(isPackageName(b), false, b);
  assert.equal(isPackageName(`site:${"a".repeat(60)}.${"b".repeat(60)}.${"c".repeat(60)}.com`), true);
  assert.equal(isPackageName(`site:${["a", "b", "c", "d"].map((l) => l.repeat(50)).join(".")}.com`), false, "over 200 characters");
});
