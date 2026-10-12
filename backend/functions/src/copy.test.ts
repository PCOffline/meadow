import { test } from "node:test";
import assert from "node:assert/strict";
import { emergencyHeadline, emergencyScope, pushes } from "./copy";

const sam = { displayName: "Sam", flower: "🌻" };

test("alerts never say which protection or setting changed", () => {
  for (const push of [pushes.protectionOff(sam), pushes.quietPhone(sam)]) {
    assert.doesNotMatch(push.title + push.body, /strict|battery|keeper|usage access|uninstall|accessibility/i);
  }
});

test("emergency notes say what happened", () => {
  assert.equal(emergencyHeadline(sam, false, false), "🚨 Sam used an emergency unlock");
  assert.equal(emergencyHeadline(sam, true, true), "🚨 Sam used an emergency unlock (while offline, beyond their monthly allowance)");
  assert.equal(emergencyScope(null, 30), "all limits for 30 minutes");
  assert.equal(emergencyScope("Instagram", 15), "Instagram for 15 minutes");
});

test("notifications use the Bumblebee wording", () => {
  const all = [
    pushes.invite(sam), pushes.inviteAccepted(sam), pushes.steppedDown(sam, 0), pushes.removedBumblebee(sam),
    pushes.gardenClosed(sam), pushes.bumblebeeLeftMeadow(sam), pushes.removalScheduled(sam), pushes.removalCancelled(sam),
  ];
  for (const push of all) {
    assert.match(push.title + push.body, /Bumblebee|Meadow/);
    assert.doesNotMatch(push.title + push.body, /approver|Petal Pal/i);
  }
});

test("a removed Bumblebee isn't told about anyone else", () => {
  const { title, body } = pushes.removedBumblebee(sam);
  assert.doesNotMatch(title + body, /\d|other|left/);
});
