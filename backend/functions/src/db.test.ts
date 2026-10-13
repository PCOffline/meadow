import { test } from "node:test";
import assert from "node:assert/strict";
import { pushPayload } from "./db";

test("a push names who it is for, so another account on the same phone can drop it", () => {
  assert.deepEqual(pushPayload("sam", { type: "chat", title: "Bee", body: "hi" }), {
    type: "chat", title: "Bee", body: "hi", to: "sam",
  });
});

test("the push's own data can't change who it is for", () => {
  assert.equal(pushPayload("sam", { type: "alert", to: "someone-else" }).to, "sam");
});
