import { test } from "node:test";
import assert from "node:assert/strict";
import type { CallableRequest } from "firebase-functions/v2/https";
import { errors } from "./copy";
import { RECENT_SIGN_IN_MS, requireRecentSignIn } from "./db";

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
