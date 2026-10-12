import { test } from "node:test";
import assert from "node:assert/strict";
import { emergencyHeadline, emergencyScope, protectionNames, pushes } from "./copy";

const sam = { displayName: "Sam", flower: "🌻" };

test("lists switched-off protections naturally", () => {
  assert.equal(protectionNames(["usageAccess"]), "usage access");
  assert.equal(protectionNames(["usageAccess", "accessibility"]), "usage access and the garden keeper");
  assert.equal(
    protectionNames(["usageAccess", "accessibility", "deviceAdmin"]),
    "usage access, the garden keeper and uninstall protection",
  );
  assert.equal(protectionNames(["unknown"]), "a protection");
});

test("emergency notes say what happened", () => {
  assert.equal(emergencyHeadline(sam, false, false), "🚨 Sam used an emergency unlock");
  assert.equal(emergencyHeadline(sam, true, true), "🚨 Sam used an emergency unlock (while offline, beyond their monthly allowance)");
  assert.equal(emergencyScope(null, 30), "all limits for 30 minutes");
  assert.equal(emergencyScope("Instagram", 15), "Instagram for 15 minutes");
});

test("notifications use the Bumblebee wording", () => {
  for (const push of [pushes.invite(sam), pushes.inviteAccepted(sam), pushes.steppedDown(sam, 0), pushes.removedBumblebee(sam, sam, 1)]) {
    assert.match(push.title + push.body, /Bumblebee/);
    assert.doesNotMatch(push.title + push.body, /approver|Petal Pal/i);
  }
  assert.equal(pushes.removedBumblebee(sam, sam, 1).body, "Sam has 1 Bumblebee left.");
  assert.equal(pushes.removedBumblebee(sam, sam, 2).body, "Sam has 2 Bumblebees left.");
});
