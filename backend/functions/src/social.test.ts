import { test } from "node:test";
import assert from "node:assert/strict";
import { HttpsError } from "firebase-functions/v2/https";
import { alreadyParted } from "./social";

test("only a relationship that's already over counts as parted; anything else is tried again", () => {
  assert.ok(alreadyParted(new HttpsError("failed-precondition", "You're not Bumblebees")));
  assert.ok(!alreadyParted(new HttpsError("unavailable", "Try again")));
  assert.ok(!alreadyParted(new HttpsError("not-found", "No profile")));
  assert.ok(!alreadyParted(new Error("14 UNAVAILABLE: connection reset")));
  assert.ok(!alreadyParted(undefined));
});
