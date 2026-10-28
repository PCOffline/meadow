import { test } from "node:test";
import assert from "node:assert/strict";
import { chatIdFor, chatOfPicture, formatMinutes, uploadId, INVITE_TOKEN_LENGTH, inviteUrl, limitText, randomFriendCode, randomInviteToken, storedAppName, summarize, withPhotoToken } from "./format";

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
  const evenings = Object.fromEntries(["1", "2", "3", "4", "5", "6", "7"].map((d) => [d, [{ from: 600, to: 1320 }]]));
  assert.equal(limitText(60, 0, evenings), "1h a day and only 10:00–22:00");
  assert.equal(limitText(30, 300, { "6": [] }), "30m a day, 5h a week and only at set hours");
  const caps = [{ days: [1, 2, 3, 4, 5], from: 600, to: 960, minutes: 30 }];
  assert.equal(limitText(30, 0, null, { "6": 120, "7": 120 }, caps), "30m a day, 2h at weekends and up to 30m 10:00–16:00 on weekdays");
});

test("summarizes each kind of request", () => {
  const base = { appName: "Instagram", minutes: 15, dailyMinutes: 20, weeklyMinutes: 0 };
  assert.equal(summarize({ ...base, type: "extra_time" }), "15 more minutes on Instagram");
  assert.equal(summarize({ ...base, type: "change_limit" }), "a new limit for Instagram: 20m a day");
  assert.equal(summarize({ ...base, type: "change_limit", dayMinutes: { "7": 0 } }), "a new limit for Instagram: 20m a day and no limit on Sun");
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

test("app names come from the stored limit, never from what the phone says", () => {
  const limit = (data: Record<string, unknown>) => ({ get: (field: string) => data[field] });
  assert.equal(storedAppName(limit({ appName: "Instagram" }), "com.instagram.android"), "Instagram");
  // No limit, or one without a usable name: the package name, which can't be dressed up.
  assert.equal(storedAppName(null, "com.instagram.android"), "com.instagram.android");
  assert.equal(storedAppName(limit({}), "com.instagram.android"), "com.instagram.android");
  assert.equal(storedAppName(limit({ appName: " " }), "com.instagram.android"), "com.instagram.android");
  assert.equal(storedAppName(limit({ appName: 42 }), "com.instagram.android"), "com.instagram.android");
});

test("a photo link gets a new token, keeping everything else", () => {
  const base = "https://firebasestorage.googleapis.com/v0/b/meadow.appspot.com/o/avatars%2Fsam?alt=media";
  assert.equal(withPhotoToken(`${base}&token=old-123#v=5`, "new-456"), `${base}&token=new-456#v=5`);
  assert.equal(withPhotoToken(`${base}&token=old-123`, "new-456"), `${base}&token=new-456`);
  assert.equal(withPhotoToken(base, "new-456"), base);
});

test("where a chat picture was sent, and the id of its note", () => {
  assert.equal(chatOfPicture("chats/a_b/a/photo1234.jpg"), "a_b");
  assert.equal(chatOfPicture("photo.jpg"), "");
  assert.equal(uploadId("chats/a_b/a/photo1234.jpg"), "chats|a_b|a|photo1234.jpg");
});
