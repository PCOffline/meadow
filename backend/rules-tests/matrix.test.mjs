// Who can read and write what, as a full matrix: every kind of document against every kind of
// person. Run with `npm run test:rules`. The other rules tests cover each rule's details; this
// one makes sure nothing is readable or writable by anyone it shouldn't be.
import { after, before, describe, test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection, collectionGroup, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, setLogLevel, Timestamp, updateDoc, where,
} from "firebase/firestore";

// Denied writes are what these tests expect; the SDK would log each one as a warning.
setLogLevel("error");

let env;

// sam is looked after by bee, and looks after ward. stranger and anon (a guest garden) know nobody.
const PEOPLE = ["sam", "bee", "ward", "stranger", "anon", "signedOut"];

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-meadow",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const card = (name) => ({ displayName: name, flower: "🌼", avatarColor: "leaf", photoUrl: null, bio: "" });
    const account = (approverIds, approveeIds) => ({
      approverIds, approveeIds, pushIds: ["phone"], friendCode: "ABC234", strictMode: true,
      privacy: { shareLimits: true, shareTime: true, shareApps: true, shareProgress: true },
    });
    const circles = { sam: [["bee"], ["ward"]], bee: [[], ["sam"]], ward: [["sam"], []], stranger: [[], []], anon: [[], []] };
    for (const [uid, [approvers, approvees]] of Object.entries(circles)) {
      await setDoc(doc(db, `users/${uid}`), card(uid));
      await setDoc(doc(db, `users/${uid}/private/account`), account(approvers, approvees));
    }
    await setDoc(doc(db, "users/sam/inbox/r9"), { requesterId: "ward", status: "pending" });
    await setDoc(doc(db, "users/sam/limits/com.insta"), { packageName: "com.insta", appName: "Instagram", dailyMinutes: 30, weeklyMinutes: 0 });
    await setDoc(doc(db, "users/sam/grants/g1"), { packageName: "com.insta", minutes: 15, byUid: "bee" });
    await setDoc(doc(db, "users/sam/usage/2026-10-12"), { dayKey: "2026-10-12", totalMinutes: 42 });
    await setDoc(doc(db, "users/sam/appUsage/2026-10-12"), { dayKey: "2026-10-12", apps: [] });
    await setDoc(doc(db, "users/sam/progress/current"), { weekChange: -10, calmerWeeks: 1, keptDays: 5, limitDays: 7 });
    await setDoc(doc(db, "users/sam/emergencies/e1"), { minutes: 30, reason: "Maps" });
    await setDoc(doc(db, "users/sam/alerts/a1"), { kind: "protection_off", createdAt: Timestamp.now() });
    await setDoc(doc(db, "heartbeats/sam"), { at: Timestamp.now(), staleNotified: false });
    await setDoc(doc(db, "friendCodes/ABC234"), { uid: "sam" });
    await setDoc(doc(db, "removals/x"), { ownerUid: "sam", beeUid: "bee" });
    await setDoc(doc(db, "rateLimits/sam"), { request: [] });
    await setDoc(doc(db, "inviteLinks/AbCdEfGh12345678"), { ownerUid: "sam" });
    await setDoc(doc(db, "invites/i1"), { fromUid: "sam", toUid: "stranger" });
    await setDoc(doc(db, "requests/r1"), { requesterId: "sam", approverIds: ["bee"], status: "pending" });
    await setDoc(doc(db, "chats/bee_sam"), { participants: ["bee", "sam"] });
    await setDoc(doc(db, "chats/bee_sam/messages/m1"), { senderId: "bee", text: "Hi", kind: "text", createdAt: Timestamp.now() });
    await setDoc(doc(db, "_testOutbox/p1"), { to: "sam" });
  });
});

after(async () => {
  await env.cleanup();
});

function dbOf(who) {
  if (who === "signedOut") return env.unauthenticatedContext().firestore();
  const token = who === "anon" ? { firebase: { sign_in_provider: "anonymous" } } : {};
  return env.authenticatedContext(who, token).firestore();
}

/** Resolves to whether the operation was allowed; any other error fails the test. */
async function allowed(operation) {
  try {
    await operation;
    return true;
  } catch (e) {
    if (e?.code === "permission-denied") return false;
    throw e;
  }
}

/** Checks one operation for every person at once and reports every mismatch together. */
async function expectOnly(label, readers, operation) {
  const wrong = [];
  for (const who of PEOPLE) {
    const ok = await allowed(operation(dbOf(who)));
    if (ok !== readers.includes(who)) wrong.push(`${who} ${ok ? "can" : "can't"}`);
  }
  assert.deepEqual(wrong, [], `${label}: ${wrong.join(", ")}`);
}

describe("reading", () => {
  const cases = [
    ["sam's name card", "users/sam", ["sam", "bee", "ward"]],
    ["sam's private account (circle, settings, strict mode)", "users/sam/private/account", ["sam"]],
    ["sam's Bumblebee inbox", "users/sam/inbox/r9", ["sam"]],
    ["sam's limits", "users/sam/limits/com.insta", ["sam", "bee"]],
    ["sam's extra-time grants", "users/sam/grants/g1", ["sam"]],
    ["sam's shared screen time", "users/sam/usage/2026-10-12", ["sam", "bee"]],
    ["sam's shared top apps", "users/sam/appUsage/2026-10-12", ["sam", "bee"]],
    ["sam's shared progress", "users/sam/progress/current", ["sam", "bee"]],
    ["sam's emergency unlocks", "users/sam/emergencies/e1", ["sam", "bee"]],
    ["sam's protection alerts", "users/sam/alerts/a1", []],
    ["sam's heartbeat", "heartbeats/sam", []],
    ["a friend code", "friendCodes/ABC234", []],
    ["a pending removal", "removals/x", []],
    ["rate limits", "rateLimits/sam", []],
    ["an invite link", "inviteLinks/AbCdEfGh12345678", []],
    ["an invite", "invites/i1", ["sam", "stranger"]],
    ["sam's request (it lists every Bumblebee asked)", "requests/r1", ["sam"]],
    ["a chat", "chats/bee_sam", ["sam", "bee"]],
    ["a chat message", "chats/bee_sam/messages/m1", ["sam", "bee"]],
    ["the test outbox", "_testOutbox/p1", []],
  ];
  for (const [label, path, readers] of cases) {
    test(label, () => expectOnly(label, readers, (db) => getDoc(doc(db, path))));
  }
});

describe("writing someone else's data", () => {
  // Only sam may change sam's own documents, and some not even sam. Each is tried as an update
  // (to a harmless field), a full overwrite and a delete.
  const samsDocs = [
    ["name card", "users/sam", { bio: "hi" }, ["sam"]],
    ["private account", "users/sam/private/account", { theme: "pond" }, ["sam"]],
    ["inbox", "users/sam/inbox/r9", { status: "approved" }, []],
    ["limit", "users/sam/limits/com.insta", { dailyMinutes: 20 }, ["sam"]],
    ["grant", "users/sam/grants/g1", { minutes: 600 }, []],
    ["usage", "users/sam/usage/2026-10-12", { totalMinutes: 1 }, ["sam"]],
    ["top apps", "users/sam/appUsage/2026-10-12", { apps: [] }, ["sam"]],
    ["progress", "users/sam/progress/current", { keptDays: 6 }, ["sam"]],
    ["emergency", "users/sam/emergencies/e1", { minutes: 60 }, []],
    ["heartbeat", "heartbeats/sam", { staleNotified: true }, []],
    ["request", "requests/r1", { status: "approved" }, []],
    ["invite", "invites/i1", { toUid: "bee" }, []],
  ];
  for (const [label, path, change, writers] of samsDocs) {
    test(`updating ${label}`, () => expectOnly(`update ${label}`, writers, (db) => updateDoc(doc(db, path), change)));
  }
  test("overwriting", async () => {
    // A full overwrite must pass the same checks. Only sam's shared parts and a stricter limit
    // can be written this way, and only by sam, each holding only what it's for.
    const valid = {
      limit: { packageName: "com.insta", appName: "Instagram", dailyMinutes: 10, weeklyMinutes: 0 },
      usage: { dayKey: "2026-10-12", totalMinutes: 5 },
      "top apps": { dayKey: "2026-10-12", apps: [] },
      progress: { weekChange: null, calmerWeeks: 0, keptDays: 0, limitDays: 0 },
    };
    for (const [label, path] of samsDocs) {
      const writers = label in valid ? ["sam"] : [];
      await expectOnly(`overwrite ${label}`, writers, (db) => setDoc(doc(db, path), valid[label] ?? { overwritten: true }));
      // Anything else in it is refused, even from sam.
      await expectOnly(`overwrite ${label} with more`, [], (db) => setDoc(doc(db, path), { ...(valid[label] ?? {}), overwritten: true }));
    }
  });
  test("deleting", async () => {
    const wrong = [];
    for (const [label, path] of samsDocs) {
      for (const who of PEOPLE.filter((p) => p !== "sam")) {
        if (await allowed(deleteDoc(doc(dbOf(who), path)))) wrong.push(`${who} deleted ${label}`);
      }
    }
    assert.deepEqual(wrong, []);
  });
  test("nobody creates documents in another person's space", async () => {
    const wrong = [];
    const paths = [
      "users/sam/limits/com.new", "users/sam/usage/2026-10-13", "users/sam/appUsage/2026-10-13", "users/sam/progress/current2",
      "users/sam/alerts/a2", "users/sam/inbox/r2", "users/sam/grants/g2",
    ];
    for (const path of paths) {
      for (const who of PEOPLE.filter((p) => p !== "sam")) {
        const data = path.includes("limits")
          ? { packageName: "com.new", appName: "New", dailyMinutes: 10, weeklyMinutes: 0 }
          : path.includes("alerts") ? { kind: "protection_off", createdAt: serverTimestamp() } : { totalMinutes: 1 };
        if (await allowed(setDoc(doc(dbOf(who), path), data))) wrong.push(`${who} created ${path}`);
      }
    }
    assert.deepEqual(wrong, []);
  });
});

describe("searching", () => {
  test("nobody can list everyone's name cards or accounts", () =>
    expectOnly("list users", [], (db) => getDocs(collection(db, "users"))));

  test("nobody can search every inbox, request, chat or heartbeat", async () => {
    for (const group of ["inbox", "private", "limits", "usage", "appUsage", "progress", "grants", "messages"]) {
      await expectOnly(`collection group ${group}`, [], (db) => getDocs(collectionGroup(db, group)));
    }
    for (const top of ["requests", "chats", "heartbeats", "invites", "friendCodes", "inviteLinks"]) {
      await expectOnly(`list ${top}`, [], (db) => getDocs(collection(db, top)));
    }
  });

  test("you can list your own requests, but a Bumblebee can't find who else was asked", async () => {
    await expectOnly("my requests", ["sam"], (db) => getDocs(query(collection(db, "requests"), where("requesterId", "==", "sam"))));
    await expectOnly("requests I was asked", [], (db) => getDocs(query(collection(db, "requests"), where("approverIds", "array-contains", "bee"))));
  });

  test("lists inside a garden follow the same rules as reading one document", async () => {
    await expectOnly("sam's limits", ["sam", "bee"], (db) => getDocs(collection(db, "users/sam/limits")));
    await expectOnly("sam's usage", ["sam", "bee"], (db) => getDocs(collection(db, "users/sam/usage")));
    await expectOnly("sam's top apps", ["sam", "bee"], (db) => getDocs(collection(db, "users/sam/appUsage")));
    await expectOnly("sam's progress", ["sam", "bee"], (db) => getDocs(collection(db, "users/sam/progress")));
    await expectOnly("sam's inbox", ["sam"], (db) => getDocs(collection(db, "users/sam/inbox")));
    await expectOnly("sam's grants", ["sam"], (db) => getDocs(collection(db, "users/sam/grants")));
    await expectOnly("chats with sam", ["sam"], (db) => getDocs(query(collection(db, "chats"), where("participants", "array-contains", "sam"))));
    await expectOnly("sam's chat messages", ["sam", "bee"], (db) => getDocs(collection(db, "chats/bee_sam/messages")));
  });
});
