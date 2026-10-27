import { test } from "node:test";
import assert from "node:assert/strict";
import { chatNotes, emergencyHeadline, emergencyScope, errors, pushes } from "./copy";

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

/** Every word a person can see, generated with sample values. */
function everything() {
  const maya = { displayName: "Maya", flower: "🌼" };
  const summary = "15 more minutes of YouTube";
  const usage = "Used 45 of 60 minutes today";
  const fns: Record<string, () => unknown> = {
    "errors.alreadyBumblebee": () => errors.alreadyBumblebee("Maya"),
    "errors.alreadyInvited": () => errors.alreadyInvited("Maya"),
    "errors.inviteeBusy": () => errors.inviteeBusy("Maya"),
    "errors.alreadyTheirBumblebee": () => errors.alreadyTheirBumblebee("Maya"),
    "errors.emergencyUsedUp": () => errors.emergencyUsedUp(3),
    "chatNotes.newBumblebee": () => chatNotes.newBumblebee(maya, sam),
    "chatNotes.removedBumblebee": () => chatNotes.removedBumblebee(sam, maya),
    "chatNotes.removalScheduled": () => chatNotes.removalScheduled(sam, maya),
    "chatNotes.removalCancelled": () => chatNotes.removalCancelled(sam, maya),
    "chatNotes.steppedDown": () => chatNotes.steppedDown(maya, sam),
    "chatNotes.request": () => [chatNotes.request(sam, summary, usage), chatNotes.request(sam, summary, null)],
    "chatNotes.yes": () => chatNotes.yes(maya, summary),
    "chatNotes.notToday": () => chatNotes.notToday(maya, summary),
    "chatNotes.emergency": () => chatNotes.emergency(emergencyHeadline(sam, false, false), emergencyScope("Maps", 30), "Lost"),
    "pushes.invite": () => pushes.invite(sam),
    "pushes.inviteAccepted": () => pushes.inviteAccepted(maya),
    "pushes.removedBumblebee": () => pushes.removedBumblebee(sam),
    "pushes.removalScheduled": () => pushes.removalScheduled(sam),
    "pushes.removalCancelled": () => pushes.removalCancelled(sam),
    "pushes.steppedDown": () => [0, 1, 2].map((n) => pushes.steppedDown(maya, n)),
    "pushes.request": () => [pushes.request(sam, summary, "Please?", usage), pushes.request(sam, summary, "", null)],
    "pushes.decided": () => [pushes.decided(maya, true, summary, "Enjoy!"), pushes.decided(maya, false, summary, "")],
    "pushes.chat": () => pushes.chat(maya, "Hello"),
    "pushes.protectionOff": () => pushes.protectionOff(sam),
    "pushes.gardenClosed": () => pushes.gardenClosed(sam),
    "pushes.bumblebeeLeftMeadow": () => pushes.bumblebeeLeftMeadow(maya),
    "pushes.quietPhone": () => pushes.quietPhone(sam),
    "pushes.emergency": () => pushes.emergency(emergencyHeadline(sam, true, false), emergencyScope(null, 60), "Lost"),
  };
  return fns;
}

const texts = (value: unknown): string[] =>
  typeof value === "string" ? [value]
    : Array.isArray(value) ? value.flatMap(texts)
      : value && typeof value === "object" ? Object.values(value).flatMap(texts) : [];

test("every message is covered here", () => {
  const covered = new Set(Object.keys(everything()));
  for (const [group, entries] of Object.entries({ errors, chatNotes, pushes })) {
    for (const [key, value] of Object.entries(entries)) {
      if (typeof value === "function") assert.ok(covered.has(`${group}.${key}`), `add ${group}.${key} to everything()`);
    }
  }
});

test("no message has a hole, a stray space or a forgotten value in it", () => {
  const all = [...Object.values(errors).filter((v) => typeof v === "string"), ...Object.values(everything()).flatMap((f) => texts(f()))];
  for (const text of all) {
    assert.ok(text.trim().length > 0, "empty message");
    assert.doesNotMatch(text, /undefined|null|NaN|\[object|\$\{|\s{2}|^\s|\s$| [.,!?]/, text);
  }
});

test("notifications fit on a lock screen", () => {
  for (const [name, make] of Object.entries(everything()).filter(([k]) => k.startsWith("pushes."))) {
    const all = [make()].flat() as { title: string; body: string }[];
    for (const { title, body } of all) {
      assert.ok(title.length > 0 && title.length <= 65, `${name} title: ${title}`);
      assert.ok(body.length <= 240, `${name} body: ${body}`);
    }
  }
});
