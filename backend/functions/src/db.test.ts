import { test } from "node:test";
import assert from "node:assert/strict";
import { DocumentSnapshot } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { MISSING_PROFILE, postToChat, profileOf } from "./db";

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
