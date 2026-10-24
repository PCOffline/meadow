// Cloud Functions integration tests: real callables and triggers on the Auth, Firestore,
// Functions and Storage emulators. Run with `npm run test:integration`.
//
// Pushes can't be delivered in the emulator, so `notify()` also records each one in
// `_testOutbox`. That lets these tests check exactly who was told what, and that nobody
// else was.
import { after, before, describe, test } from "node:test";
import assert from "node:assert/strict";
import { initializeApp, deleteApp } from "firebase/app";
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth, signInAnonymously, signInWithEmailAndPassword } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { arrayUnion, collection, doc, getDoc, getDocs, query, setDoc, updateDoc, addDoc, serverTimestamp, Timestamp, where } from "firebase/firestore";
import { getBytes, getDownloadURL, ref as storageRef, uploadBytes } from "firebase/storage";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";

const PROJECT = "demo-meadow";
// The emulator serves the functions in the region set in functions/.env.
const REGION = readFileSync(new URL("../functions/.env", import.meta.url), "utf8").match(/^MEADOW_REGION=(.+)$/m)?.[1].trim();
if (!REGION) throw new Error("Set MEADOW_REGION in backend/functions/.env");
const apps = [];
let env;
let counter = 0;

before(async () => {
  env = await initializeTestEnvironment({ projectId: PROJECT });
});

after(async () => {
  await Promise.all(apps.map((a) => deleteApp(a)));
  await env.cleanup();
});

/** Reads and writes with security rules off, like the Admin SDK would. */
async function admin(fn) {
  // withSecurityRulesDisabled resolves to nothing, so the result is carried out by hand.
  let result;
  await env.withSecurityRulesDisabled(async (ctx) => {
    result = await fn(ctx.firestore());
  });
  return result;
}
const read = (path) => admin(async (db) => (await getDoc(doc(db, path))).data());
const friendCodeOf = async (p) => (await read(`users/${p.uid}/private/account`)).friendCode;
/**
 * Reads a Storage file with rules off. The Functions emulator's default bucket depends on the
 * firebase-tools version, so both default bucket names are tried.
 */
async function readFile(path) {
  let text;
  let lastError;
  await env.withSecurityRulesDisabled(async (ctx) => {
    for (const bucket of [`${PROJECT}.appspot.com`, `${PROJECT}.firebasestorage.app`]) {
      try {
        text = new TextDecoder().decode(await getBytes(storageRef(ctx.storage(`gs://${bucket}`), path)));
        return;
      } catch (e) {
        lastError = e;
      }
    }
  });
  if (text === undefined) throw lastError;
  return text;
}

const BUCKETS = [`${PROJECT}.appspot.com`, `${PROJECT}.firebasestorage.app`];

/**
 * Gives `uid` a profile photo, as the app does: uploaded to avatars/{uid}, with its download
 * link on the profile. Uploaded to both possible default buckets (see readFile); returns each
 * bucket's download token.
 */
async function uploadPhoto(uid) {
  const tokens = {};
  await env.withSecurityRulesDisabled(async (ctx) => {
    for (const bucket of BUCKETS) {
      const ref = storageRef(ctx.storage(`gs://${bucket}`), `avatars/${uid}`);
      await uploadBytes(ref, new Uint8Array([137, 80, 78, 71]), { contentType: "image/png" });
      const url = await getDownloadURL(ref);
      tokens[bucket] = new URL(url).searchParams.get("token");
      if (bucket === BUCKETS[0]) await updateDoc(doc(ctx.firestore(), `users/${uid}`), { photoUrl: `${url}#v=1` });
    }
  });
  return tokens;
}

/**
 * Every download token of `uid`'s photo, per bucket. Read straight from the Storage emulator,
 * since the SDK only ever shows the first one.
 */
async function photoTokens(uid) {
  const tokens = {};
  for (const bucket of BUCKETS) {
    const res = await fetch(`http://127.0.0.1:9199/v0/b/${bucket}/o/${encodeURIComponent(`avatars/${uid}`)}`, {
      headers: { Authorization: "Bearer owner" },
    });
    tokens[bucket] = res.ok ? String((await res.json()).downloadTokens ?? "").split(",") : [];
  }
  return tokens;
}

const tokenIn = (url) => new URL(url).searchParams.get("token");

/** A signed-in person with their own app instance, able to call functions. */
async function person(name, { anonymous = false, profile = true, flower = "🌼" } = {}) {
  const app = initializeApp({ projectId: PROJECT, apiKey: "fake-api-key" }, `${name}-${++counter}`);
  apps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const functions = getFunctions(app, REGION);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
  const email = `${name.toLowerCase()}-${counter}-${Date.now()}@example.com`;
  const cred = anonymous ? await signInAnonymously(auth) : await createUserWithEmailAndPassword(auth, email, "petals-and-bees");
  const call = async (fn, data = {}) => (await httpsCallable(functions, fn)(data)).data;
  const me = { name, uid: cred.user.uid, email, auth, call };
  if (profile) await call("createProfile", { displayName: name, flower, termsVersion: 1 });
  return me;
}

/** Makes `bee` a Bumblebee of `owner` through a real invite link. */
async function link(owner, bee) {
  const { token } = await owner.call("createInviteLink");
  await bee.call("acceptInviteLink", { token });
}

/** Every push recorded for `uid`, optionally of one type. */
async function outbox(uid, type) {
  return admin(async (db) => {
    const filters = [where("to", "==", uid)];
    if (type) filters.push(where("type", "==", type));
    return (await getDocs(query(collection(db, "_testOutbox"), ...filters))).docs.map((d) => d.data());
  });
}

/** How many alerts were sent about `uid`, to anyone. */
async function alertsAbout(uid) {
  return admin(async (db) => (await getDocs(query(collection(db, "_testOutbox"), where("type", "==", "alert"), where("uid", "==", uid)))).size);
}

/** Waits for a background trigger to have its effect. */
async function eventually(check, { timeout = 15_000 } = {}) {
  const start = Date.now();
  for (;;) {
    try {
      return await check();
    } catch (e) {
      if (Date.now() - start > timeout) throw e;
      await new Promise((r) => setTimeout(r, 250));
    }
  }
}

async function rejects(promise, code, messagePart) {
  await assert.rejects(promise, (e) => {
    assert.equal(e.code, `functions/${code}`, e.message);
    if (messagePart) assert.match(e.message, messagePart);
    return true;
  });
}

const today = () => new Date().toISOString().slice(0, 10);

describe("profiles", () => {
  test("createProfile writes a name card, a private account and a friend code", async () => {
    const sam = await person("Sam", { flower: "🐝" });
    const profile = await read(`users/${sam.uid}`);
    assert.equal(profile.displayName, "Sam");
    assert.equal(profile.flower, "🐝");
    // The public profile is only a name card.
    assert.deepEqual(Object.keys(profile).toSorted(), ["avatarColor", "bio", "createdAt", "displayName", "flower", "photoUrl"]);

    const account = await read(`users/${sam.uid}/private/account`);
    assert.deepEqual(account.approverIds, []);
    assert.equal(account.acceptedTerms.version, 1);
    assert.equal(account.strictMode, false);
    assert.deepEqual(account.privacy, { shareUsage: true });
    assert.deepEqual(await read(`friendCodes/${account.friendCode}`), { uid: sam.uid });
    assert.equal((await read(`heartbeats/${sam.uid}`)).staleNotified, false);
  });

  test("createProfile again only renames, keeping the friend code", async () => {
    const sam = await person("Sam");
    const code = await friendCodeOf(sam);
    await sam.call("createProfile", { displayName: "Samantha", flower: "🌷" });
    assert.equal((await read(`users/${sam.uid}`)).displayName, "Samantha");
    assert.equal(await friendCodeOf(sam), code);
  });

  test("names are required and unknown flowers fall back to a daisy", async () => {
    const sam = await person("Sam", { profile: false });
    await rejects(sam.call("createProfile", { displayName: "  " }), "invalid-argument");
    await sam.call("createProfile", { displayName: "Sam", flower: "<script>" });
    assert.equal((await read(`users/${sam.uid}`)).flower, "🌼");
  });

  test("calls without signing in are refused", async () => {
    const app = initializeApp({ projectId: PROJECT, apiKey: "fake-api-key" }, `nobody-${++counter}`);
    apps.push(app);
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, "127.0.0.1", 5001);
    await rejects(httpsCallable(functions, "createInviteLink")({}), "unauthenticated");
  });

  test("profile edits reach chat headers through the trigger", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    await admin((db) => updateDoc(doc(db, `users/${sam.uid}`), { displayName: "Sammy", flower: "🦔", avatarColor: "honey" }));
    const chatId = [sam.uid, bee.uid].toSorted().join("_");
    await eventually(async () => {
      const chat = await read(`chats/${chatId}`);
      assert.equal(chat.names[sam.uid], "Sammy");
      assert.equal(chat.avatars[sam.uid].flower, "🦔");
      assert.equal(chat.avatars[sam.uid].color, "honey");
    });
  });
});

describe("invite links", () => {
  test("accepting a link makes a Bumblebee, opens a chat and tells only the owner", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    const { token, url, expiresAt } = await sam.call("createInviteLink");
    assert.match(token, /^[A-Za-z0-9]{16}$/);
    assert.ok(url.endsWith(`/i/${token}`));
    assert.ok(expiresAt > Date.now() + 6 * 24 * 3600_000);

    assert.deepEqual(await bee.call("previewInviteLink", { token }), { ownerName: "Sam", ownerFlower: "🌼", status: "ok" });
    assert.equal((await sam.call("previewInviteLink", { token })).status, "own");

    assert.deepEqual(await bee.call("acceptInviteLink", { token }), { ownerName: "Sam" });
    assert.deepEqual((await read(`users/${sam.uid}/private/account`)).approverIds, [bee.uid]);
    assert.deepEqual((await read(`users/${bee.uid}/private/account`)).approveeIds, [sam.uid]);
    const chat = await read(`chats/${[sam.uid, bee.uid].toSorted().join("_")}`);
    assert.deepEqual(chat.participants.toSorted(), [sam.uid, bee.uid].toSorted());

    assert.equal((await outbox(sam.uid, "invite_accepted")).length, 1);
    assert.equal((await outbox(bee.uid)).length, 0);
  });

  test("links work once, and never for their owner", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    const late = await person("Late");
    const { token } = await sam.call("createInviteLink");
    await rejects(sam.call("acceptInviteLink", { token }), "failed-precondition");
    await bee.call("acceptInviteLink", { token });
    assert.equal((await late.call("previewInviteLink", { token })).status, "used");
    await rejects(late.call("acceptInviteLink", { token }), "failed-precondition");
  });

  test("expired and made-up links are refused", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    const { token } = await sam.call("createInviteLink");
    await admin(async (db) => {
      const { Timestamp } = await import("firebase/firestore");
      await updateDoc(doc(db, `inviteLinks/${token}`), { expiresAt: Timestamp.fromMillis(Date.now() - 1000) });
    });
    assert.equal((await bee.call("previewInviteLink", { token })).status, "expired");
    await rejects(bee.call("acceptInviteLink", { token }), "failed-precondition");
    await rejects(bee.call("acceptInviteLink", { token: "AAAAAAAAAAAAAAAA" }), "not-found");
    await rejects(bee.call("acceptInviteLink", { token: "short" }), "invalid-argument");
  });

  test("gardens without an email can't invite yet", async () => {
    const anon = await person("Anon", { anonymous: true });
    await rejects(anon.call("createInviteLink"), "failed-precondition", /email/i);
  });
});

describe("friend codes", () => {
  test("an invite by code tells only the invited person, and accepting tells only the sender", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    const code = await friendCodeOf(bee);
    await sam.call("sendInvite", { friendCode: code.toLowerCase() });
    await rejects(sam.call("sendInvite", { friendCode: code }), "already-exists");
    const [invite] = await outbox(bee.uid, "invite");
    assert.ok(invite?.inviteId);
    assert.equal((await outbox(sam.uid)).length, 0);

    await bee.call("respondInvite", { inviteId: invite.inviteId, accept: true });
    assert.deepEqual((await read(`users/${sam.uid}/private/account`)).approverIds, [bee.uid]);
    assert.equal((await outbox(sam.uid, "invite_accepted")).length, 1);
    await rejects(bee.call("respondInvite", { inviteId: invite.inviteId, accept: true }), "failed-precondition");
  });

  test("a double tap invites once", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    const code = await friendCodeOf(bee);
    const results = await Promise.allSettled([sam.call("sendInvite", { friendCode: code }), sam.call("sendInvite", { friendCode: code })]);
    assert.equal(results.filter((r) => r.status === "fulfilled").length, 1);
    assert.equal((await outbox(bee.uid, "invite")).length, 1);
  });

  test("invites are rate limited", async () => {
    const sam = await person("Sam");
    for (let i = 0; i < 20; i++) await sam.call("createInviteLink");
    await rejects(sam.call("createInviteLink"), "resource-exhausted");
  });

  test("own and unknown codes are refused", async () => {
    const sam = await person("Sam");
    await rejects(sam.call("sendInvite", { friendCode: await friendCodeOf(sam) }), "invalid-argument");
    await rejects(sam.call("sendInvite", { friendCode: "ZZZZZZ" }), "not-found");
  });
});

describe("managing Bumblebees is your own business", () => {
  test("removing a Bumblebee waits a day, tells only that Bumblebee straight away, and can be undone", async () => {
    const sam = await person("Sam");
    const [bee, other] = [await person("Bee"), await person("Other")];
    await link(sam, bee);
    await link(sam, other);

    const { effectiveAt } = await sam.call("removeApprover", { approverUid: bee.uid });
    assert.ok(effectiveAt > Date.now() + 23 * 3600_000);
    const account = await read(`users/${sam.uid}/private/account`);
    assert.deepEqual(account.approverIds, [bee.uid, other.uid], "still a Bumblebee during the wait");
    assert.ok(account.pendingRemovals[bee.uid]);
    assert.equal((await read(`removals/${sam.uid}_${bee.uid}`)).beeUid, bee.uid);
    const told = await outbox(bee.uid, "approver_leaving");
    assert.equal(told.length, 1);
    assert.equal(told[0].uid, sam.uid);
    assert.equal((await outbox(other.uid)).length, 0, "other Bumblebees are not told");
    await rejects(sam.call("removeApprover", { approverUid: bee.uid }), "already-exists");

    await sam.call("cancelRemoval", { approverUid: bee.uid });
    assert.equal((await read(`users/${sam.uid}/private/account`)).pendingRemovals[bee.uid], undefined);
    assert.equal(await read(`removals/${sam.uid}_${bee.uid}`), undefined);
    assert.equal((await outbox(bee.uid, "approver_kept")).length, 1);
    await rejects(sam.call("cancelRemoval", { approverUid: bee.uid }), "failed-precondition");
  });

  test("stepping down tells only the owner", async () => {
    const sam = await person("Sam");
    const [bee, other] = [await person("Bee"), await person("Other")];
    await link(sam, bee);
    await link(sam, other);
    await bee.call("stepDown", { approveeUid: sam.uid });
    assert.equal((await outbox(sam.uid, "approver_left")).length, 1);
    assert.equal((await outbox(other.uid)).length, 0);
    await rejects(bee.call("stepDown", { approveeUid: sam.uid }), "failed-precondition");
  });

  test("nobody can remove someone else's Bumblebee", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    const stranger = await person("Stranger");
    await link(sam, bee);
    await rejects(stranger.call("removeApprover", { approverUid: bee.uid }), "failed-precondition");
    assert.deepEqual((await read(`users/${sam.uid}/private/account`)).approverIds, [bee.uid]);
  });
});

describe("parting ways closes the chat", () => {
  const chatIdOf = (a, b) => [a.uid, b.uid].sort().join("_");
  const pause = (ms) => new Promise((r) => setTimeout(r, ms));

  test("the chat keeps its messages but loses names and pictures, and nothing new reaches it", async () => {
    const sam = await person("Sam");
    const [bee, other] = [await person("Bee"), await person("Other")];
    await link(sam, bee);
    await link(sam, other);
    await bee.call("stepDown", { approveeUid: sam.uid });

    const chatId = chatIdOf(sam, bee);
    const chat = await read(`chats/${chatId}`);
    assert.equal(chat.closed, true);
    assert.deepEqual([...chat.participants].sort(), [sam.uid, bee.uid].sort(), "both can still read their history");
    assert.equal(chat.names, undefined);
    assert.equal(chat.avatars, undefined);
    const notes = await admin(async (db) => (await getDocs(collection(db, `chats/${chatId}/messages`))).docs.map((d) => d.data()));
    assert.ok(notes.some((m) => m.kind === "system" && m.senderId === bee.uid), "the parting note is still posted");

    // A message slipped in by other means isn't pushed.
    await admin((db) => addDoc(collection(db, `chats/${chatId}/messages`), {
      senderId: sam.uid, kind: "text", text: "Still there?", createdAt: serverTimestamp(),
    }));
    // A new name reaches the chats that are still open, but not the closed one.
    await admin((db) => updateDoc(doc(db, `users/${sam.uid}`), { displayName: "Sammy", flower: "🦔" }));
    await eventually(async () => assert.equal((await read(`chats/${chatIdOf(sam, other)}`)).names[sam.uid], "Sammy"));
    await pause(2_000);
    const closed = await read(`chats/${chatId}`);
    assert.equal(closed.names, undefined);
    assert.equal(closed.avatars, undefined);
    assert.equal((await outbox(bee.uid, "chat")).length, 0);
  });

  test("becoming a Bumblebee again opens it again", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    await sam.call("removeApprover", { approverUid: bee.uid });
    await sam.call("runSchedulesForTests", { now: Date.now() + 25 * 3600_000 });
    assert.equal((await read(`chats/${chatIdOf(sam, bee)}`)).closed, true);

    await link(sam, bee);
    const chat = await read(`chats/${chatIdOf(sam, bee)}`);
    assert.equal(chat.closed, false);
    assert.equal(chat.names[sam.uid], "Sam");
    assert.equal(chat.names[bee.uid], "Bee");
  });

  test("people who still look after each other the other way keep their chat", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    await link(bee, sam);
    await bee.call("stepDown", { approveeUid: sam.uid });
    const chat = await read(`chats/${chatIdOf(sam, bee)}`);
    assert.equal(chat.closed, false);
    assert.equal(chat.names[sam.uid], "Sam");
  });

  test("each person's photo gets a new link, so the one the other kept stops working", async () => {
    const sam = await person("Sam");
    const [bee, other] = [await person("Bee"), await person("Other")];
    await link(sam, bee);
    await link(sam, other);
    const [samBefore, beeBefore] = [await uploadPhoto(sam.uid), await uploadPhoto(bee.uid)];
    const samUrl = (await read(`users/${sam.uid}`)).photoUrl;
    await eventually(async () => assert.equal((await read(`chats/${chatIdOf(sam, other)}`)).avatars[sam.uid].photo, samUrl));

    await bee.call("stepDown", { approveeUid: sam.uid });

    // In production the new token replaces the old one. The Storage emulator adds it alongside
    // instead, so only the new one is checked here.
    for (const [who, before] of [[sam, samBefore], [bee, beeBefore]]) {
      const profileUrl = (await read(`users/${who.uid}`)).photoUrl;
      const token = tokenIn(profileUrl);
      assert.ok(!Object.values(before).includes(token), `${who.name}'s profile links a new token`);
      const tokens = await photoTokens(who.uid);
      assert.ok(BUCKETS.some((b) => tokens[b].includes(token)), `${who.name}'s photo has the new token`);
      assert.ok(profileUrl.endsWith("#v=1"));
    }
    // Sam's open chat with Other gets the new link; the closed one never had it.
    const renewedUrl = (await read(`users/${sam.uid}`)).photoUrl;
    await eventually(async () => assert.equal((await read(`chats/${chatIdOf(sam, other)}`)).avatars[sam.uid].photo, renewedUrl));
    assert.equal((await read(`chats/${chatIdOf(sam, bee)}`)).avatars, undefined);
  });

  test("parting ways without a photo leaves the profile alone", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    await bee.call("stepDown", { approveeUid: sam.uid });
    assert.equal((await read(`users/${sam.uid}`)).photoUrl, null);
    assert.equal((await read(`users/${bee.uid}`)).photoUrl, null);
  });
});

describe("requests", () => {
  async function gardenWithLimit() {
    const sam = await person("Sam");
    const [bee, other] = [await person("Bee"), await person("Other")];
    await link(sam, bee);
    await link(sam, other);
    await admin((db) => setDoc(doc(db, `users/${sam.uid}/limits/com.insta`), {
      packageName: "com.insta", appName: "Instagram", dailyMinutes: 30, weeklyMinutes: 0,
    }));
    return { sam, bee, other };
  }

  test("extra time: every Bumblebee is asked, one answers, the grant is capped and only the asker is told", async () => {
    const { sam, bee, other } = await gardenWithLimit();
    const { requestId } = await sam.call("createRequest", {
      type: "extra_time", packageName: "com.insta", minutes: 20, message: "One more episode", dayKey: today(),
    });
    assert.equal((await outbox(bee.uid, "request_new")).length, 1);
    assert.equal((await outbox(other.uid, "request_new")).length, 1);
    // Each Bumblebee gets a copy that doesn't say who else was asked.
    const copy = await read(`users/${bee.uid}/inbox/${requestId}`);
    assert.equal(copy.requesterName, "Sam");
    assert.equal(copy.minutes, 20);
    assert.equal(copy.approverIds, undefined);
    assert.ok(await read(`users/${other.uid}/inbox/${requestId}`));
    await rejects(sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() }), "already-exists");

    await rejects(sam.call("decideRequest", { requestId, approve: true }), "permission-denied");
    await bee.call("decideRequest", { requestId, approve: true, minutes: 10, note: "Enjoy" });
    const grant = await read(`users/${sam.uid}/grants/${requestId}`);
    assert.equal(grant.minutes, 10);
    assert.equal(grant.grantedBy, bee.uid);
    assert.equal((await read(`requests/${requestId}`)).status, "approved");
    assert.equal((await outbox(sam.uid, "request_decided")).length, 1);
    await rejects(other.call("decideRequest", { requestId, approve: false }), "failed-precondition");
    // The one who answered sees their answer; the other only that it was answered, not by whom.
    const mine = await read(`users/${bee.uid}/inbox/${requestId}`);
    assert.equal(mine.myDecision, "yes");
    assert.equal(mine.grantedMinutes, 10);
    const theirs = await read(`users/${other.uid}/inbox/${requestId}`);
    assert.equal(theirs.status, "approved");
    assert.equal(theirs.answeredByMe, false);
    assert.equal(theirs.myDecision, null);
    assert.doesNotMatch(JSON.stringify(theirs), new RegExp(bee.uid));
  });

  test("a request can go to just some Bumblebees", async () => {
    const { sam, bee, other } = await gardenWithLimit();
    const stranger = await person("Stranger");
    const { requestId } = await sam.call("createRequest", {
      type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today(), approverIds: [other.uid],
    });
    assert.equal((await outbox(bee.uid, "request_new")).length, 0);
    assert.equal(await read(`users/${bee.uid}/inbox/${requestId}`), undefined);
    await rejects(bee.call("decideRequest", { requestId, approve: true }), "permission-denied");
    await other.call("decideRequest", { requestId, approve: true });
    await rejects(sam.call("createRequest", {
      type: "remove_limit", packageName: "com.insta", dayKey: today(), approverIds: [stranger.uid],
    }), "permission-denied");
  });

  test("a double tap asks once", async () => {
    const { sam, bee } = await gardenWithLimit();
    const ask = () => sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() });
    const results = await Promise.allSettled([ask(), ask()]);
    assert.equal(results.filter((r) => r.status === "fulfilled").length, 1);
    assert.equal((await outbox(bee.uid, "request_new")).length, 1);
  });

  test("a yes to removing a limit removes it; strangers and removed Bumblebees can't answer", async () => {
    const { sam, bee, other } = await gardenWithLimit();
    const stranger = await person("Stranger");
    const { requestId } = await sam.call("createRequest", { type: "remove_limit", packageName: "com.insta", dayKey: today() });
    await rejects(stranger.call("decideRequest", { requestId, approve: true }), "permission-denied");
    await other.call("stepDown", { approveeUid: sam.uid });
    await rejects(other.call("decideRequest", { requestId, approve: true }), "permission-denied");
    await bee.call("decideRequest", { requestId, approve: true });
    assert.equal(await read(`users/${sam.uid}/limits/com.insta`), undefined);
  });

  test("a limit change applies only after a yes, and not today keeps it", async () => {
    const { sam, bee } = await gardenWithLimit();
    const ask = () => sam.call("createRequest", { type: "change_limit", packageName: "com.insta", dailyMinutes: 60, weeklyMinutes: 0, dayKey: today() });
    const first = await ask();
    await bee.call("decideRequest", { requestId: first.requestId, approve: false });
    assert.equal((await read(`users/${sam.uid}/limits/com.insta`)).dailyMinutes, 30);
    const second = await ask();
    await bee.call("decideRequest", { requestId: second.requestId, approve: true });
    assert.equal((await read(`users/${sam.uid}/limits/com.insta`)).dailyMinutes, 60);
  });

  test("allowed hours: a yes applies them, tightening goes through saveLimit, loosening needs a yes", async () => {
    const { sam, bee } = await gardenWithLimit();
    const days = (from, to) => Object.fromEntries(["1", "2", "3", "4", "5", "6", "7"].map((d) => [d, [{ from, to }]]));
    await sam.call("saveLimit", { packageName: "com.insta", dailyMinutes: 30, weeklyMinutes: 0, schedule: days(600, 1320) });
    assert.deepEqual((await read(`users/${sam.uid}/limits/com.insta`)).schedule["1"], [{ from: 600, to: 1320 }]);
    await sam.call("saveLimit", { packageName: "com.insta", dailyMinutes: 20, weeklyMinutes: 0, schedule: days(600, 960) });
    await rejects(sam.call("saveLimit", { packageName: "com.insta", dailyMinutes: 20, weeklyMinutes: 0, schedule: days(480, 960) }), "failed-precondition");
    await rejects(sam.call("saveLimit", { packageName: "com.insta", dailyMinutes: 20, weeklyMinutes: 0, schedule: null }), "failed-precondition");

    const { requestId } = await sam.call("createRequest", {
      type: "change_limit", packageName: "com.insta", dailyMinutes: 20, weeklyMinutes: 0, schedule: null, dayKey: today(),
    });
    await bee.call("decideRequest", { requestId, approve: true });
    const limit = await read(`users/${sam.uid}/limits/com.insta`);
    assert.equal(limit.schedule, undefined);
    assert.equal(limit.dailyMinutes, 20);
  });

  test("asking needs a Bumblebee and an existing limit", async () => {
    const lonely = await person("Lonely");
    await rejects(lonely.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() }), "failed-precondition");
    const { sam } = await gardenWithLimit();
    await rejects(sam.call("createRequest", { type: "extra_time", packageName: "com.other", minutes: 5, dayKey: today() }), "failed-precondition");
    await rejects(sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 999, dayKey: today() }), "invalid-argument");
  });

  test("a request says how the day stands", async () => {
    const { sam, bee } = await gardenWithLimit();
    const { requestId } = await sam.call("createRequest", {
      type: "extra_time", packageName: "com.insta", minutes: 15, dayKey: today(), usedMinutes: 45, allowedMinutes: 60,
    });
    const copy = await read(`users/${bee.uid}/inbox/${requestId}`);
    assert.equal(copy.usedMinutes, 45);
    assert.equal(copy.allowedMinutes, 60);
    assert.equal(copy.minutes, 15);
    const [push] = await outbox(bee.uid, "request_new");
    assert.match(push.body, /Used 45m of 1h today/);
  });

  test("one open request per app, whatever kind", async () => {
    const { sam, bee } = await gardenWithLimit();
    await admin((db) => setDoc(doc(db, `users/${sam.uid}/limits/com.tok`), {
      packageName: "com.tok", appName: "TikTok", dailyMinutes: 30, weeklyMinutes: 0,
    }));
    const { requestId } = await sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() });
    await rejects(sam.call("createRequest", { type: "remove_limit", packageName: "com.insta", dayKey: today() }), "already-exists");
    // Other apps can still be asked about.
    await sam.call("createRequest", { type: "extra_time", packageName: "com.tok", minutes: 5, dayKey: today() });
    await bee.call("decideRequest", { requestId, approve: false });
    await sam.call("createRequest", { type: "remove_limit", packageName: "com.insta", dayKey: today() });
  });

  test("asking about the same app over and over is slowed down", async () => {
    const { sam } = await gardenWithLimit();
    const ask = () => sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() });
    for (let i = 0; i < 3; i++) await sam.call("cancelRequest", await ask());
    await rejects(ask(), "resource-exhausted");
  });

  test("requests expire after half an hour, for the asker and every Bumblebee", async () => {
    const { sam, bee, other } = await gardenWithLimit();
    const { requestId } = await sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() });
    await admin((db) => updateDoc(doc(db, `requests/${requestId}`), { createdAt: Timestamp.fromMillis(Date.now() - 31 * 60_000) }));
    // An expired request doesn't block a new one for the same app.
    const fresh = await sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() });
    await rejects(bee.call("decideRequest", { requestId, approve: true }), "failed-precondition");
    assert.equal((await read(`requests/${requestId}`)).status, "expired");
    assert.equal((await read(`users/${bee.uid}/inbox/${requestId}`)).status, "expired");
    assert.equal((await read(`users/${other.uid}/inbox/${requestId}`)).status, "expired");
    assert.equal(await read(`users/${sam.uid}/grants/${requestId}`), undefined);
    await bee.call("decideRequest", { requestId: fresh.requestId, approve: true });
  });

  test("the asker can cancel, and a cancelled request can't be answered", async () => {
    const { sam, bee } = await gardenWithLimit();
    const { requestId } = await sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() });
    await sam.call("cancelRequest", { requestId });
    await rejects(bee.call("decideRequest", { requestId, approve: true }), "failed-precondition");
  });
});

describe("emergency unlocks", () => {
  test("need a reason, tell every Bumblebee and run out after the allowance", async () => {
    const sam = await person("Sam");
    const [bee, other] = [await person("Bee"), await person("Other")];
    await link(sam, bee);
    await link(sam, other);
    await rejects(sam.call("emergencyUnlock", { minutes: 15, reason: "short" }), "invalid-argument");
    await rejects(sam.call("emergencyUnlock", { minutes: 45, reason: "A real emergency here" }), "invalid-argument");

    const first = await sam.call("emergencyUnlock", { minutes: 15, reason: "Need maps to find the vet" });
    assert.equal(first.remaining, 2);
    assert.equal((await outbox(bee.uid, "emergency")).length, 1);
    assert.equal((await outbox(other.uid, "emergency")).length, 1);
    await rejects(sam.call("emergencyUnlock", { minutes: 15, reason: "Need maps to find the vet" }), "failed-precondition");

    // End the active one so the allowance, not the overlap, is what stops the fourth.
    for (let i = 0; i < 2; i++) {
      await admin((db) => updateDoc(doc(db, `users/${sam.uid}/private/account`), { emergencyUntil: null }));
      await sam.call("emergencyUnlock", { minutes: 15, reason: "Another real emergency" });
    }
    await admin((db) => updateDoc(doc(db, `users/${sam.uid}/private/account`), { emergencyUntil: null }));
    await rejects(sam.call("emergencyUnlock", { minutes: 15, reason: "One too many unlocks" }), "resource-exhausted");
    // An unlock taken offline is still recorded, beyond the allowance.
    const offline = await sam.call("emergencyUnlock", { minutes: 15, reason: "Was offline at the time", startedAt: Date.now() - 60_000 });
    assert.equal(offline.remaining, 0);
  });
});

describe("chat and alerts", () => {
  test("a message is pushed to the other person only", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    const chatId = [sam.uid, bee.uid].toSorted().join("_");
    await admin((db) => addDoc(collection(db, `chats/${chatId}/messages`), {
      senderId: sam.uid, kind: "text", text: "Hello bee", createdAt: serverTimestamp(),
    }));
    await eventually(async () => assert.equal((await outbox(bee.uid, "chat")).length, 1));
    assert.equal((await outbox(sam.uid, "chat")).length, 0);
  });

  test("a protection alert reaches every Bumblebee, without saying which setting changed", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    await admin((db) => addDoc(collection(db, `users/${sam.uid}/alerts`), { kind: "protection_off" }));
    const [alert] = await eventually(async () => {
      const pushes = await outbox(bee.uid, "alert");
      assert.equal(pushes.length, 1);
      return pushes;
    });
    assert.doesNotMatch(alert.title + alert.body, /strict|battery|keeper|usage access|uninstall/i);
  });
});

describe("your data", () => {
  test("export includes your garden and conversations but never push tokens", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    await admin(async (db) => {
      await updateDoc(doc(db, `users/${sam.uid}/private/account`), { pushIds: arrayUnion("secret-phone-id"), fcmTokens: ["secret-old-token"] });
      await setDoc(doc(db, `users/${sam.uid}/limits/com.insta`), { packageName: "com.insta", appName: "Instagram", dailyMinutes: 30, weeklyMinutes: 0 });
    });
    const { path } = await sam.call("exportMyData");
    assert.match(path, new RegExp(`^exports/${sam.uid}/`));
    const data = JSON.parse(await readFile(path));
    assert.equal(data.profile.displayName, "Sam");
    assert.deepEqual(data.account.approverIds, [bee.uid]);
    assert.equal(data.limits[0].id, "com.insta");
    assert.equal(data.conversations.length, 1);
    assert.ok(data.conversations[0].messages.length >= 1);
    assert.doesNotMatch(await readFile(path), /secret-phone-id|secret-old-token/);
  });

  test("deleting an account removes it everywhere and tells only the people it affects", async () => {
    const sam = await person("Sam");
    const [bee, friend, bystander] = [await person("Bee"), await person("Friend"), await person("Bystander")];
    await link(sam, bee); // bee looks after sam
    await link(friend, sam); // sam looks after friend
    await link(friend, bystander); // bystander also looks after friend, but has nothing to do with sam
    const code = await friendCodeOf(sam);

    await sam.call("deleteAccount");

    assert.equal(await read(`users/${sam.uid}`), undefined);
    assert.equal(await read(`users/${sam.uid}/private/account`), undefined);
    assert.equal(await read(`friendCodes/${code}`), undefined);
    assert.equal(await read(`heartbeats/${sam.uid}`), undefined);
    assert.equal(await read(`chats/${[sam.uid, bee.uid].toSorted().join("_")}`), undefined);
    assert.deepEqual((await read(`users/${bee.uid}/private/account`)).approveeIds, []);
    assert.deepEqual((await read(`users/${friend.uid}/private/account`)).approverIds, [bystander.uid]);

    assert.equal((await outbox(bee.uid, "approver_left")).length, 1);
    assert.equal((await outbox(friend.uid, "approver_left")).length, 1);
    assert.equal((await outbox(bystander.uid)).length, 0);
    await assert.rejects(signInWithEmailAndPassword(sam.auth, sam.email, "petals-and-bees"));
  });
});

describe("scheduled jobs", () => {
  // The emulators don't run schedules; runSchedulesForTests (emulator only) runs them as if at `now`.
  const hours = (h) => Date.now() + h * 3600_000;
  const runSchedules = (who, now) => who.call("runSchedulesForTests", { now });

  test("a removal takes effect after its day, telling only the removed Bumblebee", async () => {
    const sam = await person("Sam");
    const [bee, other] = [await person("Bee"), await person("Other")];
    await link(sam, bee);
    await link(sam, other);
    await sam.call("removeApprover", { approverUid: bee.uid });
    await runSchedules(sam, hours(23));
    assert.ok((await read(`users/${sam.uid}/private/account`)).approverIds.includes(bee.uid), "not before the day is up");

    await runSchedules(sam, hours(25));
    const account = await read(`users/${sam.uid}/private/account`);
    assert.deepEqual(account.approverIds, [other.uid]);
    assert.equal(account.pendingRemovals?.[bee.uid], undefined);
    assert.ok(!(await read(`users/${bee.uid}/private/account`)).approveeIds.includes(sam.uid));
    assert.equal(await read(`removals/${sam.uid}_${bee.uid}`), undefined);
    assert.equal((await outbox(bee.uid, "approver_left")).length, 1);
    assert.equal((await outbox(other.uid)).length, 0, "other Bumblebees are never told");
  });

  test("a quiet phone is reported once, vaguely, and only to that garden's Bumblebees", async () => {
    const sam = await person("Sam");
    const [bee, stranger] = [await person("Bee"), await person("Stranger")];
    await link(sam, bee);
    await admin(async (db) => {
      await setDoc(doc(db, `users/${sam.uid}/limits/com.insta`), { packageName: "com.insta", appName: "Instagram", dailyMinutes: 30, weeklyMinutes: 0 });
      await setDoc(doc(db, `heartbeats/${sam.uid}`), { at: Timestamp.now(), staleNotified: false });
    });
    await runSchedules(sam, hours(5));
    assert.equal((await outbox(bee.uid, "alert")).length, 0, "a few quiet hours are normal");

    await runSchedules(sam, hours(7));
    const alerts = (await outbox(bee.uid, "alert")).filter((a) => a.uid === sam.uid);
    assert.equal(alerts.length, 1);
    assert.doesNotMatch(alerts[0].title + alerts[0].body, /strict|battery|keeper|usage access|uninstall|accessibility/i);
    assert.equal((await outbox(stranger.uid)).length, 0);

    await runSchedules(sam, hours(8));
    assert.equal((await outbox(bee.uid, "alert")).filter((a) => a.uid === sam.uid).length, 1, "reported once");

    // Back in touch: the next silence is reported again.
    await admin((db) => setDoc(doc(db, `heartbeats/${sam.uid}`), { at: Timestamp.fromMillis(hours(9)), staleNotified: true }));
    await runSchedules(sam, hours(10));
    assert.equal((await read(`heartbeats/${sam.uid}`)).staleNotified, false);
  });

  test("a garden without Bumblebees or limits raises no alarm, and stops being looked at", async () => {
    const sam = await person("Sam");
    await admin((db) => setDoc(doc(db, `heartbeats/${sam.uid}`), { at: Timestamp.now(), staleNotified: false }));
    await runSchedules(sam, hours(7));
    assert.equal(await alertsAbout(sam.uid), 0);
    // Marked as looked at, so it leaves the quiet list instead of coming back every hour.
    assert.equal((await read(`heartbeats/${sam.uid}`)).staleNotified, true);

    // Back in touch: it may be looked at again next time it goes quiet.
    await admin((db) => setDoc(doc(db, `heartbeats/${sam.uid}`), { at: Timestamp.fromMillis(hours(9)), staleNotified: true }));
    await runSchedules(sam, hours(10));
    assert.equal((await read(`heartbeats/${sam.uid}`)).staleNotified, false);
  });

  test("quiet phones nobody looks after don't crowd out a garden with Bumblebees", async () => {
    // Older than Sam's, so the oldest-first scan reaches them first: no Bumblebees, no limits,
    // or no profile at all (anyone signed in may write their own heartbeat).
    const [loner, unlimited] = [await person("Loner"), await person("Unlimited")];
    await link(unlimited, await person("Bee"));
    const nobody = `nobody-${Date.now()}`;
    const earlier = Timestamp.fromMillis(Date.now() - 3600_000);
    await admin(async (db) => {
      for (const uid of [loner.uid, unlimited.uid, nobody]) {
        await setDoc(doc(db, `heartbeats/${uid}`), { at: earlier, staleNotified: false });
      }
    });
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    await admin(async (db) => {
      await setDoc(doc(db, `users/${sam.uid}/limits/com.insta`), { packageName: "com.insta", appName: "Instagram", dailyMinutes: 30, weeklyMinutes: 0 });
      await setDoc(doc(db, `heartbeats/${sam.uid}`), { at: Timestamp.now(), staleNotified: false });
    });

    await runSchedules(sam, hours(7));
    for (const uid of [loner.uid, unlimited.uid, nobody]) {
      assert.equal((await read(`heartbeats/${uid}`)).staleNotified, true, `${uid} leaves the quiet list`);
      assert.equal(await alertsAbout(uid), 0);
    }
    assert.equal((await outbox(bee.uid, "alert")).filter((a) => a.uid === sam.uid).length, 1);
  });

  test("unanswered requests expire on schedule, even if nobody looks", async () => {
    const sam = await person("Sam");
    const bee = await person("Bee");
    await link(sam, bee);
    await admin((db) => setDoc(doc(db, `users/${sam.uid}/limits/com.insta`), {
      packageName: "com.insta", appName: "Instagram", dailyMinutes: 30, weeklyMinutes: 0,
    }));
    const { requestId } = await sam.call("createRequest", { type: "extra_time", packageName: "com.insta", minutes: 5, dayKey: today() });
    await runSchedules(sam, Date.now() + 31 * 60_000);
    assert.equal((await read(`requests/${requestId}`)).status, "expired");
    assert.equal((await read(`users/${bee.uid}/inbox/${requestId}`)).status, "expired");
  });
});

describe("invites by code can be withdrawn", () => {
  test("only the sender can cancel, and a cancelled invite can't be accepted", async () => {
    const sam = await person("Sam");
    const [bee, other] = [await person("Bee"), await person("Other")];
    await sam.call("sendInvite", { friendCode: await friendCodeOf(bee) });
    const [invite] = await outbox(bee.uid, "invite");
    await rejects(other.call("cancelInvite", { inviteId: invite.inviteId }), "not-found");
    await rejects(bee.call("cancelInvite", { inviteId: invite.inviteId }), "not-found");
    await sam.call("cancelInvite", { inviteId: invite.inviteId });
    assert.equal((await read(`invites/${invite.inviteId}`)).status, "cancelled");
    await rejects(bee.call("respondInvite", { inviteId: invite.inviteId, accept: true }), "failed-precondition");
    assert.deepEqual((await read(`users/${sam.uid}/private/account`)).approverIds, []);
  });
});

describe("every callable is safe to call wrongly", () => {
  const CALLABLES = [
    "createProfile", "createInviteLink", "previewInviteLink", "acceptInviteLink", "sendInvite", "respondInvite", "cancelInvite",
    "removeApprover", "cancelRemoval", "stepDown", "createRequest", "decideRequest", "cancelRequest", "saveLimit",
    "emergencyUnlock", "exportMyData", "deleteAccount",
  ];

  test("nothing works without signing in", async () => {
    const app = initializeApp({ projectId: PROJECT, apiKey: "fake-api-key" }, `signed-out-${++counter}`);
    apps.push(app);
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, "127.0.0.1", 5001);
    for (const fn of CALLABLES) {
      await rejects(httpsCallable(functions, fn)({}), "unauthenticated");
    }
  });

  test("junk input is refused politely, never with an internal error", async () => {
    const junk = [null, 42, "text", [], { approverUid: { $gt: "" } }, { requestId: "../../users/x", approve: "yes" },
      { requestId: "../../users/x" }, { inviteId: "a/b", accept: true }, { approverUid: "../x" }, { approveeUid: "." },
      { friendCode: "AB/CD" }, { token: "../../x" }, { packageName: "a/b", dailyMinutes: 10, weeklyMinutes: 0 },
      { type: "extra_time", packageName: "../x", minutes: 15, dayKey: "2026-10-13" }, { minutes: -1e9, type: "x".repeat(5000) }];
    const failures = [];
    for (const fn of CALLABLES.filter((f) => f !== "deleteAccount")) {
      const someone = await person(`Junk ${fn}`);
      for (const data of junk) {
        try {
          await someone.call(fn, data);
        } catch (e) {
          if (e.code === "functions/internal" || e.code === "functions/unknown") failures.push(`${fn}(${JSON.stringify(data).slice(0, 40)}): ${e.code} ${e.message}`);
        }
      }
    }
    assert.deepEqual(failures, []);
  });

  test("deleting an account twice is harmless", async () => {
    const sam = await person("Sam");
    await sam.call("deleteAccount");
    // The old sign-in still works for a moment; a second delete finds nothing and changes nothing.
    await sam.call("deleteAccount").catch((e) => assert.notEqual(e.code, "functions/internal"));
    assert.equal(await read(`users/${sam.uid}`), undefined);
    assert.equal(await read(`users/${sam.uid}/private/account`), undefined);
  });
});
