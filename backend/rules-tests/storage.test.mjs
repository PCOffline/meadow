// Storage rules for profile photos. Run with `npm run test:rules` (Firestore and Storage emulators).
import { after, before, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, setLogLevel } from "firebase/firestore";

// Denied writes are what these tests expect; the SDK would log each one as a warning.
setLogLevel("error");
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
    setDoc(doc(ctx.firestore(), "users/sam/private/account"), { approverIds: ["bee"], approveeIds: ["friend"], pushIds: [] }));
  await assertSucceeds(getBytes(ref(storageOf("bee"), "avatars/sam")));
  await assertSucceeds(getBytes(ref(storageOf("friend"), "avatars/sam")));
  await assertFails(getBytes(ref(storageOf("stranger"), "avatars/sam")));
  await assertFails(getBytes(ref(storageOf(null), "avatars/sam")));
});

test("chat photos are seen only by the two in the chat, and added only by each to their own folder", async () => {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), "chats/bee_sam"), { participants: ["bee", "sam"] }));
  const gif = { contentType: "image/gif" };
  await assertSucceeds(uploadBytes(ref(storageOf("sam"), "chats/bee_sam/sam/photo1234.jpg"), jpeg(), image));
  await assertSucceeds(uploadBytes(ref(storageOf("bee"), "chats/bee_sam/bee/funny123.gif"), jpeg(), gif));
  await assertSucceeds(getBytes(ref(storageOf("bee"), "chats/bee_sam/sam/photo1234.jpg")));
  await assertSucceeds(getBytes(ref(storageOf("sam"), "chats/bee_sam/bee/funny123.gif")));
  await assertFails(getBytes(ref(storageOf("stranger"), "chats/bee_sam/sam/photo1234.jpg")));
  await assertFails(getBytes(ref(storageOf(null), "chats/bee_sam/sam/photo1234.jpg")));
  // Not in someone else's folder, not into a chat you're not in, not replaced or removed.
  await assertFails(uploadBytes(ref(storageOf("sam"), "chats/bee_sam/bee/fake1234.jpg"), jpeg(), image));
  await assertFails(uploadBytes(ref(storageOf("stranger"), "chats/bee_sam/stranger/x1234567.jpg"), jpeg(), image));
  await assertFails(uploadBytes(ref(storageOf("sam"), "chats/bee_sam/sam/photo1234.jpg"), jpeg(), image));
  await assertFails(deleteObject(ref(storageOf("sam"), "chats/bee_sam/sam/photo1234.jpg")));
  // Only photos and GIFs under 5 MB.
  await assertFails(uploadBytes(ref(storageOf("sam"), "chats/bee_sam/sam/big12345.jpg"), jpeg(5 * 1024 * 1024 + 1), image));
  await assertFails(uploadBytes(ref(storageOf("sam"), "chats/bee_sam/sam/page1234.jpg"), jpeg(), { contentType: "text/html" }));
});

test("a closed chat takes no new photos", async () => {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), "chats/lee_sam"), { participants: ["lee", "sam"], closed: true }));
  await assertFails(uploadBytes(ref(storageOf("sam"), "chats/lee_sam/sam/photo1234.jpg"), jpeg(), image));
});

test("nothing outside avatars is reachable", async () => {
  await assertFails(uploadBytes(ref(storageOf("sam"), "other/sam"), jpeg(), image));
  await assertFails(getBytes(ref(storageOf("sam"), "other/thing")));
});

test("data exports are read and deleted only by their owner, and only the backend writes them", async () => {
  await env.withSecurityRulesDisabled((ctx) =>
    uploadBytes(ref(ctx.storage(), "exports/sam/meadow-1.json"), new TextEncoder().encode("{}"), { contentType: "application/json" }));
  await assertSucceeds(getBytes(ref(storageOf("sam"), "exports/sam/meadow-1.json")));
  await assertFails(getBytes(ref(storageOf("bee"), "exports/sam/meadow-1.json")));
  await assertFails(uploadBytes(ref(storageOf("sam"), "exports/sam/fake.json"), jpeg(), image));
  await assertFails(deleteObject(ref(storageOf("bee"), "exports/sam/meadow-1.json")));
  await assertSucceeds(deleteObject(ref(storageOf("sam"), "exports/sam/meadow-1.json")));
});
