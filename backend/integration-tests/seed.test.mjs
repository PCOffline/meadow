// The demo data for `npm run emulators` goes through the real callables and rules; this keeps it
// working as they change, and checks it shows what it should.
import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs } from "firebase/firestore";
import { isSeeded, PROJECT, seed } from "../emulator-seed.mjs";

let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: PROJECT });
});
after(() => env.cleanup());

async function admin(fn) {
  let result;
  await env.withSecurityRulesDisabled(async (ctx) => {
    result = await fn(ctx.firestore());
  });
  return result;
}

test("the demo people are seeded with a circle, limits, usage, a request and a chat", async () => {
  const domain = `seed-${Date.now()}.test`;
  assert.equal(await isSeeded({ domain }), false);
  const { sam, bee, robin, kai } = await seed({ domain });
  assert.equal(await isSeeded({ domain }), true);

  await admin(async (db) => {
    const account = async (p) => (await getDoc(doc(db, `users/${p.uid}/private/account`))).data();
    assert.deepEqual((await account(sam)).approverIds.toSorted(), [bee.uid, robin.uid].toSorted());
    assert.deepEqual((await account(sam)).approveeIds, [robin.uid]);
    assert.deepEqual((await account(kai)).approverIds, []);

    const limits = (p) => getDocs(collection(db, `users/${p.uid}/limits`)).then((s) => s.docs.map((d) => d.id).toSorted());
    assert.deepEqual(await limits(sam), ["com.google.android.youtube", "com.instagram.android"]);
    assert.equal((await getDoc(doc(db, `users/${sam.uid}/usage/today`))).get("totalMinutes"), 65);
    assert.equal((await getDoc(doc(db, `users/${sam.uid}/appUsage/today`))).get("apps").length, 2);
    assert.equal((await getDoc(doc(db, `users/${sam.uid}/progress/current`))).get("calmerWeeks"), 2);

    const inbox = (await getDocs(collection(db, `users/${bee.uid}/inbox`))).docs.map((d) => d.data());
    assert.equal(inbox.length, 1);
    assert.equal(inbox[0].status, "pending");
    // Robin wasn't asked, so Robin's inbox is empty.
    assert.equal((await getDocs(collection(db, `users/${robin.uid}/inbox`))).size, 0);

    const chatId = [sam.uid, bee.uid].toSorted().join("_");
    const kinds = (await getDocs(collection(db, `chats/${chatId}/messages`))).docs.map((d) => d.get("kind")).toSorted();
    assert.deepEqual(kinds, ["request", "system", "text"]); // "Bee is now your Bumblebee", the ask, and a word.
  });
});
