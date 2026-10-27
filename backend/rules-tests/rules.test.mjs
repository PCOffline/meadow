// Security rules tests. Run with `npm run test:rules` (starts the Firestore emulator).
import { after, before, beforeEach, describe, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { addDoc, collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, setLogLevel, Timestamp, updateDoc } from "firebase/firestore";

// Denied writes are what these tests expect; the SDK would log each one as a warning.
setLogLevel("error");

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-meadow",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});

after(async () => {
  await env.cleanup();
});

/** The public name card. */
const profile = (overrides = {}) => ({
  displayName: "Sam",
  flower: "🌼",
  avatarColor: "leaf",
  photoUrl: null,
  bio: "",
  ...overrides,
});

/** Everything owner-only. */
const account = (overrides = {}) => ({
  approverIds: [],
  approveeIds: [],
  pushIds: ["phone"],
  friendCode: "ABC234",
  strictMode: false,
  privacy: { shareUsage: true },
  pendingRemovals: {},
  ...overrides,
});

const limit = (daily, weekly = 0) => ({ packageName: "com.insta", appName: "Instagram", dailyMinutes: daily, weeklyMinutes: weekly });

/** sam is looked after by bee (and bee2); stranger has nothing to do with them. */
const EVERYTHING = { shareLimits: true, shareTime: true, shareApps: true, shareProgress: true };

async function seed({ samHasBee = true, privacy = EVERYTHING } = {}) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users/sam"), profile());
    await setDoc(doc(db, "users/sam/private/account"), account({ approverIds: samHasBee ? ["bee", "bee2"] : [], privacy }));
    for (const bee of ["bee", "bee2"]) {
      await setDoc(doc(db, `users/${bee}`), profile({ displayName: bee }));
      await setDoc(doc(db, `users/${bee}/private/account`), account({ approveeIds: samHasBee ? ["sam"] : [] }));
    }
    await setDoc(doc(db, "users/stranger"), profile({ displayName: "Stranger" }));
    await setDoc(doc(db, "users/stranger/private/account"), account());
    await setDoc(doc(db, "users/sam/usage/2026-10-12"), { dayKey: "2026-10-12", totalMinutes: 42 });
    await setDoc(doc(db, "users/sam/appUsage/2026-10-12"), { dayKey: "2026-10-12", apps: [] });
    await setDoc(doc(db, "users/sam/progress/current"), { weekChange: -10, calmerWeeks: 1, keptDays: 5, limitDays: 7 });
    await setDoc(doc(db, "users/sam/limits/com.insta"), limit(30, 300));
    await setDoc(doc(db, "users/sam/grants/g1"), { packageName: "com.insta", minutes: 15, dayKey: "2026-10-12" });
    await setDoc(doc(db, "requests/r1"), { requesterId: "sam", approverIds: ["bee", "bee2"], status: "pending" });
    await setDoc(doc(db, "users/bee/inbox/r1"), { requesterId: "sam", status: "pending" });
    await setDoc(doc(db, "heartbeats/sam"), { at: Timestamp.now(), staleNotified: false });
    await setDoc(doc(db, "chats/bee_sam"), { participants: ["bee", "sam"], names: {}, flowers: {} });
    await setDoc(doc(db, "inviteLinks/AbCdEfGh12345678"), { ownerUid: "sam" });
  });
}

const as = (uid) => env.authenticatedContext(uid).firestore();

describe("profiles", () => {
  beforeEach(() => seed());

  test("you, your Bumblebees and the people you look after can read a profile", async () => {
    await assertSucceeds(getDoc(doc(as("sam"), "users/sam")));
    await assertSucceeds(getDoc(doc(as("bee"), "users/sam")));
    await assertSucceeds(getDoc(doc(as("sam"), "users/bee")));
    await assertFails(getDoc(doc(as("stranger"), "users/sam")));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "users/sam")));
  });

  test("you can edit your name card within limits, and it holds nothing else", async () => {
    const me = doc(as("sam"), "users/sam");
    await assertSucceeds(updateDoc(me, { displayName: "Sammy", flower: "🐝", avatarColor: "honey", bio: "Growing calmer days" }));
    await assertSucceeds(updateDoc(me, { photoUrl: null }));
    await assertFails(updateDoc(me, { displayName: "" }));
    await assertFails(updateDoc(me, { bio: "x".repeat(81) }));
    // Settings and status can't be put where the circle could read them.
    for (const field of ["strictMode", "privacy", "protection", "lastHeartbeat", "friendCode", "emergencyUntil"]) {
      await assertFails(updateDoc(me, { [field]: true }));
    }
    await assertFails(updateDoc(doc(as("stranger"), "users/sam"), { displayName: "Hacked" }));
  });

  test("a photo can only be your own upload, so no other server learns when your circle sees it", async () => {
    const me = doc(as("sam"), "users/sam");
    // This project's default bucket, under either name Firebase gives it.
    const storage = "https://firebasestorage.googleapis.com/v0/b/demo-meadow.firebasestorage.app/o";
    await assertSucceeds(updateDoc(me, { photoUrl: `${storage}/avatars%2Fsam?alt=media&token=0a1b-2c3d#v=1760000000000` }));
    await assertSucceeds(updateDoc(me, { photoUrl: "https://firebasestorage.googleapis.com/v0/b/demo-meadow.appspot.com/o/avatars%2Fsam?alt=media" }));
    await assertSucceeds(updateDoc(me, { photoUrl: "http://10.0.2.2:9199/v0/b/demo-meadow.appspot.com/o/avatars%2Fsam?alt=media&token=1f" }));
    for (const url of [
      "https://example.com/a.jpg",
      // Another project's bucket would log every view for whoever owns it.
      "https://firebasestorage.googleapis.com/v0/b/evil-project.firebasestorage.app/o/avatars%2Fsam?alt=media",
      "https://firebasestorage.googleapis.com/v0/b/evil-project.appspot.com/o/avatars%2Fsam?alt=media",
      "https://firebasestorage.googleapis.com/v0/b/demo-meadow-evil.appspot.com/o/avatars%2Fsam?alt=media",
      "https://firebasestorage.googleapis.com/v0/b/evil.demo-meadow.appspot.com/o/avatars%2Fsam?alt=media",
      "https://firebasestorage.googleapis.com/v0/b/demo-meadow.evil.app/o/avatars%2Fsam?alt=media",
      "http://127.0.0.1:9199/v0/b/evil-project.appspot.com/o/avatars%2Fsam?alt=media",
      `${storage}/avatars%2Fbee?alt=media&token=0a1b`,
      `${storage}/exports%2Fsam%2Fmeadow-1.json?alt=media`,
      `https://firebasestorage.googleapis.com.evil.dev/v0/b/x/o/avatars%2Fsam?alt=media`,
      `http://192.168.1.20:9199/v0/b/x/o/avatars%2Fsam?alt=media`,
      `${storage}/avatars%2Fsam?alt=media&token=0a1b&callback=https://evil.dev`,
    ]) {
      await assertFails(updateDoc(me, { photoUrl: url }));
    }
  });

  test("profiles and Bumblebee lists are only created by the backend", async () => {
    await assertFails(setDoc(doc(as("new"), "users/new"), profile()));
    await assertFails(deleteDoc(doc(as("sam"), "users/sam")));
    await assertFails(updateDoc(doc(as("sam"), "users/sam/private/account"), { approverIds: [] }));
    await assertFails(updateDoc(doc(as("stranger"), "users/stranger/private/account"), { approveeIds: ["sam"] }));
    await assertFails(setDoc(doc(as("new"), "users/new/private/account"), account()));
  });

  test("who looks after whom stays private", async () => {
    await assertSucceeds(getDoc(doc(as("sam"), "users/sam/private/account")));
    await assertFails(getDoc(doc(as("bee"), "users/sam/private/account")));
    await assertFails(getDoc(doc(as("sam"), "users/bee/private/account")));
    // Bumblebees of the same person can't see each other.
    await assertFails(getDoc(doc(as("bee"), "users/bee2")));
  });

  test("you can update your own settings, nothing the backend keeps", async () => {
    const mine = doc(as("sam"), "users/sam/private/account");
    await assertSucceeds(updateDoc(mine, { pushIds: ["a", "b"], theme: "lavender", darkMode: "system" }));
    const choices = { shareLimits: true, shareTime: false, shareApps: true, shareProgress: false };
    await assertSucceeds(updateDoc(mine, { privacy: choices }));
    // As written by app versions from before the choices were split.
    await assertSucceeds(updateDoc(mine, { privacy: { shareUsage: false } }));
    await assertFails(updateDoc(mine, { privacy: { ...choices, sneaky: true } }));
    await assertFails(updateDoc(mine, { privacy: { ...choices, shareTime: "yes" } }));
    await assertFails(updateDoc(mine, { privacy: "everything" }));
    await assertFails(updateDoc(mine, { pushIds: Array.from({ length: 11 }, (_, i) => `t${i}`) }));
    for (const field of ["friendCode", "pendingRemovals", "emergencyUses", "lastAlertAt"]) {
      await assertFails(updateDoc(mine, { [field]: field === "friendCode" ? "MINE11" : { sneaky: 1 } }));
    }
    await assertFails(updateDoc(doc(as("bee"), "users/sam/private/account"), { pushIds: [] }));
  });

  test("strict mode goes on and off without asking anyone, and nobody else can see it", async () => {
    const mine = doc(as("sam"), "users/sam/private/account");
    await assertSucceeds(updateDoc(mine, { strictMode: true }));
    await assertSucceeds(updateDoc(mine, { strictMode: false }));
    await assertFails(updateDoc(mine, { strictMode: "off" }));
    await assertFails(getDoc(doc(as("bee"), "users/sam/private/account")));
  });

  test("an emergency unlock can be ended from the phone but never started or extended", async () => {
    const mine = doc(as("sam"), "users/sam/private/account");
    await assertSucceeds(updateDoc(mine, { emergencyUntil: serverTimestamp() }));
    const future = Timestamp.fromMillis(Date.now() + 60 * 60_000);
    await assertFails(updateDoc(mine, { emergencyUntil: future }));
    await assertFails(updateDoc(mine, { emergencyUses: [] }));
  });

  test("a heartbeat is always the present moment, and only the backend reads it", async () => {
    const sam = doc(as("sam"), "heartbeats/sam");
    await assertSucceeds(updateDoc(sam, { at: serverTimestamp() }));
    await assertFails(updateDoc(sam, { at: Timestamp.fromMillis(Date.UTC(2100, 0, 1)) }));
    await assertFails(updateDoc(sam, { at: serverTimestamp(), staleNotified: true }));
    await assertFails(getDoc(sam));
    await assertFails(getDoc(doc(as("bee"), "heartbeats/sam")));
    await assertFails(updateDoc(doc(as("bee"), "heartbeats/sam"), { at: serverTimestamp() }));
    await assertSucceeds(setDoc(doc(as("bee"), "heartbeats/bee"), { at: serverTimestamp(), staleNotified: false }));
    await assertFails(setDoc(doc(as("bee2"), "heartbeats/bee2"), { at: serverTimestamp(), staleNotified: true }));
  });
});

describe("limits", () => {
  test("tightening is always allowed", async () => {
    await seed();
    const ref = doc(as("sam"), "users/sam/limits/com.insta");
    await assertSucceeds(setDoc(ref, limit(20, 300)));
    await assertSucceeds(setDoc(ref, limit(20, 200)));
  });

  test("loosening or removing needs a Bumblebee's yes", async () => {
    await seed();
    const ref = doc(as("sam"), "users/sam/limits/com.insta");
    await assertFails(setDoc(ref, limit(45, 300)));
    await assertFails(setDoc(ref, limit(30, 0)));
    await assertFails(setDoc(ref, limit(0, 300)));
    await assertFails(deleteDoc(ref));
  });

  test("with Bumblebees an app keeps its name, so a request can't pass it off as another", async () => {
    await seed();
    const ref = doc(as("sam"), "users/sam/limits/com.insta");
    // Even a stricter (or unchanged) limit can't rename the app.
    await assertFails(setDoc(ref, { ...limit(20, 300), appName: "Calculator" }));
    await assertFails(setDoc(ref, { ...limit(30, 300), appName: "Calculator" }));
    await assertFails(updateDoc(ref, { appName: "Calculator" }));
    await assertSucceeds(setDoc(ref, limit(20, 300)));
  });

  test("limits hold nothing but the limit", async () => {
    await seed();
    const ref = doc(as("sam"), "users/sam/limits/com.insta");
    await assertSucceeds(setDoc(ref, { ...limit(20, 300), updatedAt: serverTimestamp() }));
    await assertFails(setDoc(ref, { ...limit(20, 300), label: "Calculator" }));
    await assertFails(updateDoc(ref, { label: "Calculator" }));
    const tok = doc(as("sam"), "users/sam/limits/com.tok");
    await assertFails(setDoc(tok, { ...limit(15), packageName: "com.tok", label: "Calculator" }));
    await assertSucceeds(setDoc(tok, { ...limit(15), packageName: "com.tok", createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await seed({ samHasBee: false });
    await assertFails(setDoc(ref, { ...limit(90, 0), label: "Calculator" }));
  });

  test("without Bumblebees you can change anything", async () => {
    await seed({ samHasBee: false });
    const ref = doc(as("sam"), "users/sam/limits/com.insta");
    await assertSucceeds(setDoc(ref, { ...limit(30, 300), appName: "Insta" }));
    await assertSucceeds(setDoc(ref, limit(90, 0)));
    await assertSucceeds(deleteDoc(ref));
  });

  test("new limits must be valid and filed under their package", async () => {
    await seed();
    await assertSucceeds(setDoc(doc(as("sam"), "users/sam/limits/com.tok"), { ...limit(15), packageName: "com.tok" }));
    await assertFails(setDoc(doc(as("sam"), "users/sam/limits/com.tok"), { ...limit(0, 0), packageName: "com.tok" }));
    await assertFails(setDoc(doc(as("sam"), "users/sam/limits/com.tok"), limit(15)));
    await assertFails(setDoc(doc(as("bee"), "users/sam/limits/com.tok"), { ...limit(15), packageName: "com.tok" }));
  });

  test("allowed hours can be set on a new limit, but with Bumblebees only change through the backend", async () => {
    await seed();
    const evenings = { 1: [{ from: 600, to: 1320 }] };
    await assertSucceeds(setDoc(doc(as("sam"), "users/sam/limits/com.tok"), { ...limit(0), packageName: "com.tok", schedule: evenings }));
    await assertFails(setDoc(doc(as("sam"), "users/sam/limits/com.tok"), { ...limit(0), packageName: "com.tok", schedule: { 9: [] } }));
    const ref = doc(as("sam"), "users/sam/limits/com.insta");
    await assertFails(setDoc(ref, { ...limit(30, 300), schedule: evenings }));
    await assertSucceeds(setDoc(doc(as("sam"), "users/sam/limits/com.tok"), { ...limit(10), packageName: "com.tok", schedule: evenings }));
    await seed({ samHasBee: false });
    await assertSucceeds(setDoc(ref, { ...limit(30, 300), schedule: evenings }));
  });

  test("Bumblebees can see what's shared; strangers can't, and extra time stays private", async () => {
    await seed();
    await assertSucceeds(getDoc(doc(as("bee"), "users/sam/appUsage/2026-10-12")));
    await assertSucceeds(getDoc(doc(as("bee"), "users/sam/progress/current")));
    await assertFails(getDoc(doc(as("stranger"), "users/sam/appUsage/2026-10-12")));
    await assertFails(getDoc(doc(as("stranger"), "users/sam/progress/current")));
    await assertSucceeds(getDoc(doc(as("bee"), "users/sam/limits/com.insta")));
    await assertFails(getDoc(doc(as("bee"), "users/sam/grants/g1")));
    await assertSucceeds(getDoc(doc(as("sam"), "users/sam/grants/g1")));
    await assertSucceeds(getDoc(doc(as("bee"), "users/sam/usage/2026-10-12")));
    await assertFails(getDoc(doc(as("stranger"), "users/sam/limits/com.insta")));
    await assertFails(getDoc(doc(as("stranger"), "users/sam/usage/2026-10-12")));
  });

  // Each shared part, and the choice that keeps it private.
  const PARTS = [
    ["shareLimits", "users/sam/limits/com.insta"],
    ["shareTime", "users/sam/usage/2026-10-12"],
    ["shareApps", "users/sam/appUsage/2026-10-12"],
    ["shareProgress", "users/sam/progress/current"],
  ];

  test("each part is kept private on its own; the rest stay shared", async () => {
    for (const [choice, hidden] of PARTS) {
      await seed({ privacy: { ...EVERYTHING, [choice]: false } });
      for (const [other, path] of PARTS) {
        const read = getDoc(doc(as("bee"), path));
        await (other === choice ? assertFails(read) : assertSucceeds(read));
        await assertSucceeds(getDoc(doc(as("sam"), path)));
      }
      await assertFails(getDocs(collection(as("bee"), hidden.split("/").slice(0, -1).join("/"))));
    }
  });

  test("a choice from before the split still holds for today's time and apps; progress waits to be chosen", async () => {
    for (const shareUsage of [true, false]) {
      await seed({ privacy: { shareUsage } });
      await assertSucceeds(getDoc(doc(as("bee"), "users/sam/limits/com.insta")));
      for (const path of ["users/sam/usage/2026-10-12", "users/sam/appUsage/2026-10-12"]) {
        const read = getDoc(doc(as("bee"), path));
        await (shareUsage ? assertSucceeds(read) : assertFails(read));
      }
      await assertFails(getDoc(doc(as("bee"), "users/sam/progress/current")));
      await assertSucceeds(getDoc(doc(as("sam"), "users/sam/progress/current")));
    }
  });

  test("each shared part holds only what it's for, and only its owner writes or removes it", async () => {
    await seed();
    const sam = as("sam");
    await assertSucceeds(setDoc(doc(sam, "users/sam/usage/2026-10-13"), { dayKey: "2026-10-13", totalMinutes: 3, updatedAt: serverTimestamp() }));
    // Today's apps can't ride along with the total: they have a choice of their own.
    await assertFails(setDoc(doc(sam, "users/sam/usage/2026-10-13"), { dayKey: "2026-10-13", totalMinutes: 3, apps: [] }));
    await assertFails(setDoc(doc(sam, "users/sam/usage/2026-10-13"), { dayKey: "2026-10-13", totalMinutes: "3" }));
    const apps = Array.from({ length: 15 }, (_, i) => ({ packageName: `p${i}`, appName: "App", minutes: 1, limitMinutes: 0 }));
    await assertSucceeds(setDoc(doc(sam, "users/sam/appUsage/2026-10-13"), { dayKey: "2026-10-13", apps, updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(sam, "users/sam/appUsage/2026-10-13"), { dayKey: "2026-10-13", apps: [...apps, apps[0]] }));
    await assertFails(setDoc(doc(sam, "users/sam/appUsage/2026-10-13"), { dayKey: "2026-10-13", apps, totalMinutes: 3 }));
    const progress = { weekChange: null, calmerWeeks: 0, keptDays: 0, limitDays: 0, updatedAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(sam, "users/sam/progress/current"), progress));
    await assertFails(setDoc(doc(sam, "users/sam/progress/current"), { ...progress, totalMinutes: 3 }));
    await assertFails(setDoc(doc(sam, "users/sam/progress/current"), { ...progress, weekChange: "-10" }));
    await assertFails(setDoc(doc(sam, "users/sam/progress/other"), progress));
    for (const path of ["users/sam/usage/2026-10-13", "users/sam/appUsage/2026-10-13", "users/sam/progress/current"]) {
      await assertFails(deleteDoc(doc(as("bee"), path)));
      await assertSucceeds(deleteDoc(doc(sam, path)));
    }
  });

  test("extra time can't be granted from a phone", async () => {
    await seed();
    await assertFails(setDoc(doc(as("sam"), "users/sam/grants/mine"), { packageName: "com.insta", minutes: 600, dayKey: "2026-10-12" }));
    await assertFails(setDoc(doc(as("bee"), "users/sam/grants/mine"), { packageName: "com.insta", minutes: 600, dayKey: "2026-10-12" }));
  });
});

describe("requests, invites and links", () => {
  beforeEach(() => seed());

  test("a request is read by the asker; each Bumblebee reads only their own copy", async () => {
    await assertSucceeds(getDoc(doc(as("sam"), "requests/r1")));
    // The full request lists every Bumblebee asked, so Bumblebees never read it.
    await assertFails(getDoc(doc(as("bee"), "requests/r1")));
    await assertFails(getDoc(doc(as("stranger"), "requests/r1")));
    await assertSucceeds(getDoc(doc(as("bee"), "users/bee/inbox/r1")));
    await assertFails(getDoc(doc(as("bee2"), "users/bee/inbox/r1")));
    await assertFails(getDoc(doc(as("sam"), "users/bee/inbox/r1")));
    await assertFails(updateDoc(doc(as("bee"), "users/bee/inbox/r1"), { status: "approved" }));
    await assertFails(updateDoc(doc(as("bee"), "requests/r1"), { status: "approved" }));
    await assertFails(updateDoc(doc(as("sam"), "requests/r1"), { status: "approved" }));
  });

  test("removals and rate limits are the backend's alone", async () => {
    await assertFails(getDoc(doc(as("sam"), "removals/sam_bee")));
    await assertFails(setDoc(doc(as("sam"), "removals/sam_bee"), { ownerUid: "sam", beeUid: "bee" }));
    await assertFails(getDoc(doc(as("sam"), "rateLimits/sam")));
    await assertFails(setDoc(doc(as("sam"), "rateLimits/sam"), {}));
  });

  test("invite links, friend codes and the test outbox are hidden", async () => {
    await assertFails(getDoc(doc(as("sam"), "inviteLinks/AbCdEfGh12345678")));
    await assertFails(getDoc(doc(as("stranger"), "inviteLinks/AbCdEfGh12345678")));
    await assertFails(getDoc(doc(as("stranger"), "friendCodes/ABC234")));
    await assertFails(getDoc(doc(as("sam"), "_testOutbox/x")));
  });

  test("protection alerts carry no detail about which setting changed", async () => {
    const alerts = collection(as("sam"), "users/sam/alerts");
    await assertSucceeds(addDoc(alerts, { kind: "protection_off", createdAt: serverTimestamp() }));
    await assertFails(addDoc(alerts, { kind: "protection_off", lost: ["accessibility"], createdAt: serverTimestamp() }));
    await assertFails(addDoc(alerts, { kind: "anything", createdAt: serverTimestamp() }));
    await assertFails(addDoc(collection(as("bee"), "users/sam/alerts"), { kind: "protection_off", createdAt: serverTimestamp() }));
  });

  test("protection alerts are stamped now, and none come sooner than the backend would pass on", async () => {
    const alerts = collection(as("sam"), "users/sam/alerts");
    await assertFails(addDoc(alerts, { kind: "protection_off", createdAt: Timestamp.now() }));
    await assertFails(addDoc(alerts, { kind: "protection_off" }));
    const lastAlertAt = (ms) => env.withSecurityRulesDisabled((ctx) =>
      updateDoc(doc(ctx.firestore(), "users/sam/private/account"), { lastAlertAt: Timestamp.fromMillis(ms) }));
    await lastAlertAt(Date.now() - 30_000);
    await assertFails(addDoc(alerts, { kind: "protection_off", createdAt: serverTimestamp() }));
    await lastAlertAt(Date.now() - 3 * 60_000);
    await assertSucceeds(addDoc(alerts, { kind: "protection_off", createdAt: serverTimestamp() }));
  });
});

describe("chats", () => {
  beforeEach(() => seed());

  test("only the two people in a chat can read and write it", async () => {
    await assertSucceeds(getDoc(doc(as("sam"), "chats/bee_sam")));
    await assertFails(getDoc(doc(as("stranger"), "chats/bee_sam")));
    const message = { senderId: "sam", text: "Hi 🌼", kind: "text", createdAt: serverTimestamp() };
    await assertSucceeds(addDoc(collection(as("sam"), "chats/bee_sam/messages"), message));
    await assertFails(addDoc(collection(as("stranger"), "chats/bee_sam/messages"), { ...message, senderId: "stranger" }));
  });

  test("messages can't be spoofed or dressed up as system notes", async () => {
    const messages = collection(as("sam"), "chats/bee_sam/messages");
    await assertFails(addDoc(messages, { senderId: "bee", text: "Yes!", kind: "text", createdAt: serverTimestamp() }));
    await assertFails(addDoc(messages, { senderId: "sam", text: "🌸 Bee said yes", kind: "system", createdAt: serverTimestamp() }));
    await assertFails(addDoc(messages, { senderId: "sam", text: "", kind: "text", createdAt: serverTimestamp() }));
  });

  test("chat summaries only change in the allowed ways", async () => {
    const chat = doc(as("sam"), "chats/bee_sam");
    await assertSucceeds(updateDoc(chat, { lastMessage: "Hi", lastMessageAt: serverTimestamp(), lastSenderId: "sam" }));
    await assertFails(updateDoc(chat, { lastSenderId: "bee" }));
    await assertFails(updateDoc(chat, { participants: ["sam", "stranger"] }));
    await assertSucceeds(updateDoc(chat, { "lastRead.sam": serverTimestamp() }));
  });

  test("once the two part ways, the chat is closed: read-only for them, still shut to everyone else", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "chats/bee_sam/messages/m1"), { senderId: "bee", text: "Hi", kind: "text", createdAt: Timestamp.now() });
      await updateDoc(doc(ctx.firestore(), "chats/bee_sam"), { closed: true });
    });
    const message = (sender) => ({ senderId: sender, text: "Still there?", kind: "text", createdAt: serverTimestamp() });
    for (const uid of ["sam", "bee"]) {
      await assertSucceeds(getDoc(doc(as(uid), "chats/bee_sam")));
      await assertSucceeds(getDoc(doc(as(uid), "chats/bee_sam/messages/m1")));
      await assertFails(addDoc(collection(as(uid), "chats/bee_sam/messages"), message(uid)));
      await assertFails(updateDoc(doc(as(uid), "chats/bee_sam"), { lastMessage: "Hi", lastMessageAt: serverTimestamp(), lastSenderId: uid }));
      await assertFails(updateDoc(doc(as(uid), "chats/bee_sam"), { [`lastRead.${uid}`]: serverTimestamp() }));
      await assertFails(updateDoc(doc(as(uid), "chats/bee_sam"), { closed: false }));
    }
    await assertFails(getDoc(doc(as("stranger"), "chats/bee_sam")));
    await assertFails(getDoc(doc(as("stranger"), "chats/bee_sam/messages/m1")));

    // Joining up again opens it.
    await env.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), "chats/bee_sam"), { closed: false }));
    await assertSucceeds(addDoc(collection(as("bee"), "chats/bee_sam/messages"), message("bee")));
  });

  test("you can't put words in the other person's mouth or mark their messages read", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      updateDoc(doc(ctx.firestore(), "chats/bee_sam"), { lastMessage: "Hello", lastSenderId: "bee", lastMessageAt: Timestamp.now() }));
    const chat = doc(as("sam"), "chats/bee_sam");
    await assertFails(updateDoc(chat, { lastMessage: "Sure, take the whole day off" }));
    await assertFails(updateDoc(chat, { "lastRead.bee": serverTimestamp() }));
    await assertFails(updateDoc(chat, { lastMessage: "x".repeat(141), lastMessageAt: serverTimestamp(), lastSenderId: "sam" }));
    await assertFails(updateDoc(chat, { lastMessage: "Hi", lastMessageAt: Timestamp.fromMillis(Date.now() + 86_400_000), lastSenderId: "sam" }));
    const messages = collection(as("sam"), "chats/bee_sam/messages");
    await assertFails(addDoc(messages, { senderId: "sam", text: "Back-dated", kind: "text", createdAt: Timestamp.fromMillis(0) }));
  });
});
