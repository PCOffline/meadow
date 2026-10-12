// Storage rules for profile photos. Run with `npm run test:rules` (Firestore and Storage emulators).
import { after, before, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { deleteObject, getBytes, ref, uploadBytes } from "firebase/storage";

let env;
const jpeg = (size = 1024) => new Uint8Array(size).fill(7);
const image = { contentType: "image/jpeg" };

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-meadow",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
    storage: { rules: readFileSync(new URL("../storage.rules", import.meta.url), "utf8") },
  });
  await env.withSecurityRulesDisabled(async (ctx) => {
    await uploadBytes(ref(ctx.storage(), "avatars/sam"), jpeg(), image);
  });
});

after(async () => {
  await env.cleanup();
});

const storageOf = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).storage();

test("you can set and remove your own photo", async () => {
  await assertSucceeds(uploadBytes(ref(storageOf("sam"), "avatars/sam"), jpeg(), image));
  await assertSucceeds(getBytes(ref(storageOf("sam"), "avatars/sam")));
  await assertSucceeds(uploadBytes(ref(storageOf("lee"), "avatars/lee"), jpeg(), image));
  await assertSucceeds(deleteObject(ref(storageOf("lee"), "avatars/lee")));
});

test("nobody else can change your photo", async () => {
  await assertFails(uploadBytes(ref(storageOf("bee"), "avatars/sam"), jpeg(), image));
  await assertFails(deleteObject(ref(storageOf("bee"), "avatars/sam")));
  await assertFails(uploadBytes(ref(storageOf(null), "avatars/sam"), jpeg(), image));
});

test("only images under 2 MB are accepted", async () => {
  await assertFails(uploadBytes(ref(storageOf("sam"), "avatars/sam"), jpeg(2 * 1024 * 1024 + 1), image));
  await assertFails(uploadBytes(ref(storageOf("sam"), "avatars/sam"), jpeg(), { contentType: "text/html" }));
});

test("your circle can see your photo; strangers and signed-out visitors can't", async () => {
  // Who is in your circle lives in Firestore; the Storage rules look it up.
  await env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), "users/sam/private/account"), { approverIds: ["bee"], approveeIds: ["friend"], fcmTokens: [] }));
  await assertSucceeds(getBytes(ref(storageOf("bee"), "avatars/sam")));
  await assertSucceeds(getBytes(ref(storageOf("friend"), "avatars/sam")));
  await assertFails(getBytes(ref(storageOf("stranger"), "avatars/sam")));
  await assertFails(getBytes(ref(storageOf(null), "avatars/sam")));
});

test("nothing outside avatars is reachable", async () => {
  await assertFails(uploadBytes(ref(storageOf("sam"), "other/sam"), jpeg(), image));
  await assertFails(getBytes(ref(storageOf("sam"), "other/thing")));
});
