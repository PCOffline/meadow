import { test } from "node:test";
import assert from "node:assert/strict";
import type { DocumentSnapshot } from "firebase-admin/firestore";
import { type CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { errors } from "./copy";
import { MISSING_PROFILE, postToChat, profileOf, pushPayload, RECENT_SIGN_IN_MS, requireRecentSignIn } from "./db";

test("a push names who it is for, so another account on the same phone can drop it", () => {
  assert.deepEqual(pushPayload("sam", { type: "chat", title: "Bee", body: "hi" }), {
    type: "chat", title: "Bee", body: "hi", to: "sam",
  });
});

test("the push's own data can't change who it is for", () => {
  assert.equal(pushPayload("sam", { type: "alert", to: "someone-else" }).to, "sam");
});

const now = 1_760_000_000_000;
const signedIn = (provider: string, authTime?: number) =>
  ({ auth: { uid: "sam", token: { auth_time: authTime, firebase: { sign_in_provider: provider } } } }) as unknown as CallableRequest<unknown>;

test("steps that can't be undone need a sign-in from the last few minutes", () => {
  assert.doesNotThrow(() => requireRecentSignIn(signedIn("password", (now - 60_000) / 1000), now));
  assert.doesNotThrow(() => requireRecentSignIn(signedIn("emailLink", (now - RECENT_SIGN_IN_MS) / 1000), now));
  for (const req of [signedIn("password", (now - RECENT_SIGN_IN_MS - 1000) / 1000), signedIn("password")]) {
    assert.throws(() => requireRecentSignIn(req, now), { code: "unauthenticated", message: errors.signInAgain });
  }
});

test("accounts without an email, which can't sign in again, aren't asked to", () => {
  assert.doesNotThrow(() => requireRecentSignIn(signedIn("anonymous", (now - 30 * 24 * 3600_000) / 1000), now));
});

const snapshot = (data?: Record<string, unknown>) => ({ exists: data !== undefined, data: () => data }) as unknown as DocumentSnapshot;

test("a missing profile is refused, or stands in as a neutral name card when asked", () => {
  assert.throws(() => profileOf(snapshot()), (e) => e instanceof HttpsError && e.code === "not-found");
  assert.deepEqual(profileOf(snapshot(), { orMissing: true }), MISSING_PROFILE);
  assert.deepEqual(profileOf(snapshot({ displayName: "Sam", flower: "🐝" }), { orMissing: true }), {
    displayName: "Sam", flower: "🐝", avatarColor: "leaf", photoUrl: null,
  });
});

test("a chat note that can't be written never fails the action it describes", async () => {
  // No Firebase app is set up here, so every Firestore call fails.
  await assert.doesNotReject(postToChat("a_b", { senderId: "a", kind: "system", text: "Hello" }));
});
