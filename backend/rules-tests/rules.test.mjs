// Security rules tests. Run with `npm run test:rules` (starts the Firestore emulator).
import { after, before, beforeEach, describe, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { addDoc, collection, deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from "firebase/firestore";

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

const profile = (overrides = {}) => ({
  displayName: "Sam",
  flower: "🌼",
  friendCode: "ABC234",
  approverIds: [],
  approveeIds: [],
  strictMode: false,
  fcmTokens: [],
  protection: {},
  ...overrides,
});

const limit = (daily, weekly = 0) => ({ packageName: "com.insta", appName: "Instagram", dailyMinutes: daily, weeklyMinutes: weekly });

/** sam is guarded by bee; stranger has nothing to do with either. */
async function seed({ samHasBee = true } = {}) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users/sam"), profile({ approverIds: samHasBee ? ["bee"] : [] }));
    await setDoc(doc(db, "users/bee"), profile({ displayName: "Bee", approveeIds: samHasBee ? ["sam"] : [] }));
    await setDoc(doc(db, "users/stranger"), profile({ displayName: "Stranger" }));
    await setDoc(doc(db, "users/sam/limits/com.insta"), limit(30, 300));
    await setDoc(doc(db, "users/sam/grants/g1"), { packageName: "com.insta", minutes: 15, dayKey: "2026-10-12" });
    await setDoc(doc(db, "requests/r1"), { requesterId: "sam", approverIds: ["bee"], status: "pending" });
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

  test("you can change your name and flower", async () => {
    await assertSucceeds(updateDoc(doc(as("sam"), "users/sam"), { displayName: "Sammy", flower: "🌷" }));
    await assertFails(updateDoc(doc(as("sam"), "users/sam"), { displayName: "" }));
    await assertFails(updateDoc(doc(as("stranger"), "users/sam"), { displayName: "Hacked" }));
  });

  test("Bumblebee lists and profiles are only written by the backend", async () => {
    await assertFails(updateDoc(doc(as("sam"), "users/sam"), { approverIds: [] }));
    await assertFails(updateDoc(doc(as("stranger"), "users/stranger"), { approveeIds: ["sam"] }));
    await assertFails(setDoc(doc(as("new"), "users/new"), profile()));
    await assertFails(deleteDoc(doc(as("sam"), "users/sam")));
  });

  test("strict mode can always go on, but only off without Bumblebees", async () => {
    await assertSucceeds(updateDoc(doc(as("sam"), "users/sam"), { strictMode: true }));
    await assertFails(updateDoc(doc(as("sam"), "users/sam"), { strictMode: false }));
    await seed({ samHasBee: false });
    await assertSucceeds(updateDoc(doc(as("sam"), "users/sam"), { strictMode: true }));
    await assertSucceeds(updateDoc(doc(as("sam"), "users/sam"), { strictMode: false }));
  });

  test("an emergency unlock can be ended from the phone but never started or extended", async () => {
    await assertSucceeds(updateDoc(doc(as("sam"), "users/sam"), { emergencyUntil: serverTimestamp() }));
    const future = Timestamp.fromMillis(Date.now() + 60 * 60_000);
    await assertFails(updateDoc(doc(as("sam"), "users/sam"), { emergencyUntil: future }));
    await assertFails(updateDoc(doc(as("sam"), "users/sam"), { emergencyUses: [] }));
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

  test("without Bumblebees you can change anything", async () => {
    await seed({ samHasBee: false });
    const ref = doc(as("sam"), "users/sam/limits/com.insta");
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

  test("Bumblebees can see limits, usage and extra time; strangers can't", async () => {
    await seed();
    await assertSucceeds(getDoc(doc(as("bee"), "users/sam/limits/com.insta")));
    await assertSucceeds(getDoc(doc(as("bee"), "users/sam/grants/g1")));
    await assertFails(getDoc(doc(as("stranger"), "users/sam/limits/com.insta")));
    await assertFails(getDoc(doc(as("stranger"), "users/sam/usage/2026-10-12")));
  });

  test("extra time can't be granted from a phone", async () => {
    await seed();
    await assertFails(setDoc(doc(as("sam"), "users/sam/grants/mine"), { packageName: "com.insta", minutes: 600, dayKey: "2026-10-12" }));
    await assertFails(setDoc(doc(as("bee"), "users/sam/grants/mine"), { packageName: "com.insta", minutes: 600, dayKey: "2026-10-12" }));
  });
});

describe("requests, invites and links", () => {
  beforeEach(() => seed());

  test("requests are visible to the asker and their Bumblebees, and only the backend writes them", async () => {
    await assertSucceeds(getDoc(doc(as("sam"), "requests/r1")));
    await assertSucceeds(getDoc(doc(as("bee"), "requests/r1")));
    await assertFails(getDoc(doc(as("stranger"), "requests/r1")));
    await assertFails(updateDoc(doc(as("bee"), "requests/r1"), { status: "approved" }));
    await assertFails(updateDoc(doc(as("sam"), "requests/r1"), { status: "approved" }));
  });

  test("invite links and friend codes are hidden", async () => {
    await assertFails(getDoc(doc(as("sam"), "inviteLinks/AbCdEfGh12345678")));
    await assertFails(getDoc(doc(as("stranger"), "inviteLinks/AbCdEfGh12345678")));
    await assertFails(getDoc(doc(as("stranger"), "friendCodes/ABC234")));
  });

  test("protection alerts carry only codes", async () => {
    const alerts = collection(as("sam"), "users/sam/alerts");
    await assertSucceeds(addDoc(alerts, { kind: "protection_off", lost: ["accessibility"], createdAt: serverTimestamp() }));
    await assertFails(addDoc(alerts, { kind: "anything", lost: [], createdAt: serverTimestamp() }));
    await assertFails(addDoc(alerts, { kind: "protection_off", detail: "spoofed text", createdAt: serverTimestamp() }));
    await assertFails(addDoc(collection(as("bee"), "users/sam/alerts"), { kind: "protection_off", lost: [], createdAt: serverTimestamp() }));
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
  });
});
