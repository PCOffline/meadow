import { test } from "node:test";
import assert from "node:assert/strict";
import { chatIdFor, formatMinutes, INVITE_TOKEN_LENGTH, inviteUrl, limitText, randomFriendCode, randomInviteToken, summarize } from "./format";

test("formats minutes like the app does", () => {
  assert.equal(formatMinutes(0), "0m");
  assert.equal(formatMinutes(45), "45m");
  assert.equal(formatMinutes(120), "2h");
  assert.equal(formatMinutes(90), "1h 30m");
});

test("describes limits", () => {
  assert.equal(limitText(30, 0), "30m a day");
  assert.equal(limitText(0, 300), "5h a week");
  assert.equal(limitText(30, 300), "30m a day and 5h a week");
  assert.equal(limitText(0, 0), "no limit");
});

test("summarizes each kind of request", () => {
  const base = { appName: "Instagram", minutes: 15, dailyMinutes: 20, weeklyMinutes: 0 };
  assert.equal(summarize({ ...base, type: "extra_time" }), "15 more minutes on Instagram");
  assert.equal(summarize({ ...base, type: "change_limit" }), "a new limit for Instagram: 20m a day");
  assert.equal(summarize({ ...base, type: "remove_limit" }), "removing the limit on Instagram");
  assert.equal(summarize({ ...base, type: "disable_strict_mode" }), "turning off strict mode");
});

test("friend codes avoid look-alike characters", () => {
  for (let i = 0; i < 200; i++) assert.match(randomFriendCode(), /^[A-HJ-NP-Z2-9]{6}$/);
});

test("invite tokens are long and URL-safe", () => {
  const tokens = new Set(Array.from({ length: 200 }, randomInviteToken));
  assert.equal(tokens.size, 200);
  for (const t of tokens) assert.match(t, new RegExp(`^[A-Za-z0-9]{${INVITE_TOKEN_LENGTH}}$`));
  assert.equal(inviteUrl("meadow-test", "abc"), "https://meadow-test.web.app/i/abc");
});

test("chat ids don't depend on who starts", () => {
  assert.equal(chatIdFor("b", "a"), chatIdFor("a", "b"));
});
