// Demo people for the Firebase emulators, so a debug build has a garden, a circle, requests and
// a chat to show straight away. Everything goes through the real callables, or through the
// security rules as that person, so the data always matches what the app itself would make.
//
// Run by `npm run emulators` on a fresh start, or on its own while the emulators run:
//   node emulator-seed.mjs
// Sign in on the app with any of the emails below and the password "meadow-demo".
import { deleteApp, initializeApp } from "firebase/app";
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth, signInWithEmailAndPassword } from "firebase/auth";
import { addDoc, collection, connectFirestoreEmulator, doc, getFirestore, serverTimestamp, setDoc } from "firebase/firestore";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

export const PROJECT = "demo-meadow";
export const PASSWORD = "meadow-demo";
const REGION = readFileSync(new URL("functions/.env", import.meta.url), "utf8").match(/^MEADOW_REGION=(.+)$/m)?.[1].trim();

const dayKey = (daysAgo) => new Date(Date.now() - daysAgo * 86_400_000).toISOString().slice(0, 10);
const INSTAGRAM = { packageName: "com.instagram.android", appName: "Instagram" };
const YOUTUBE = { packageName: "com.google.android.youtube", appName: "YouTube" };
const CHESS = { packageName: "com.chess", appName: "Chess" };

/**
 * Creates the demo people. [domain] keeps several seeds apart (tests use their own). Returns
 * each person's uid and email. Throws if the emulators aren't running.
 */
export async function seed({ domain = "meadow.test", host = "127.0.0.1" } = {}) {
  if (!REGION) throw new Error("Set MEADOW_REGION in backend/functions/.env");
  const apps = [];

  async function person(name, flower) {
    const app = initializeApp({ projectId: PROJECT, apiKey: "demo-api-key" }, `seed-${name}-${domain}`);
    apps.push(app);
    const auth = getAuth(app);
    connectAuthEmulator(auth, `http://${host}:9099`, { disableWarnings: true });
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, host, 5001);
    const db = getFirestore(app);
    connectFirestoreEmulator(db, host, 8080);
    const email = `${name.toLowerCase()}@${domain}`;
    const { user } = await createUserWithEmailAndPassword(auth, email, PASSWORD);
    const call = async (fn, data = {}) => (await httpsCallable(functions, fn)(data)).data;
    await call("createProfile", { displayName: name, flower, termsVersion: 1 });
    return { name, email, uid: user.uid, call, db };
  }

  async function lookAfter(owner, bee) {
    const { token } = await owner.call("createInviteLink");
    await bee.call("acceptInviteLink", { token });
  }

  try {
    // Sam has two Bumblebees and looks after Robin. Kai uses Meadow alone.
    const sam = await person("Sam", "🌻");
    const bee = await person("Bee", "🐝");
    const robin = await person("Robin", "🌷");
    const kai = await person("Kai", "🍄");
    await lookAfter(sam, bee);
    await lookAfter(sam, robin);
    await lookAfter(robin, sam);

    // Sam's limits pass the backend's checks for people with Bumblebees, like any new limit.
    await sam.call("saveLimit", { ...INSTAGRAM, dailyMinutes: 30, weeklyMinutes: 0 });
    await sam.call("saveLimit", { ...YOUTUBE, dailyMinutes: 60, weeklyMinutes: 300 });
    await robin.call("saveLimit", { ...CHESS, dailyMinutes: 45, weeklyMinutes: 0 });
    await kai.call("saveLimit", { ...INSTAGRAM, dailyMinutes: 20, weeklyMinutes: 0 });

    // Today's shared screen time and top apps, written by Sam's own phone as the app does: each
    // part in its own document, so each privacy choice can be held by the rules, and only today's.
    const [insta, yt] = [24, 41];
    const day = dayKey(0);
    await setDoc(doc(sam.db, `users/${sam.uid}/usage/today`), { dayKey: day, totalMinutes: insta + yt, updatedAt: serverTimestamp() });
    await setDoc(doc(sam.db, `users/${sam.uid}/appUsage/today`), {
      dayKey: day,
      apps: [
        { ...INSTAGRAM, minutes: insta },
        { ...YOUTUBE, minutes: yt },
      ],
      updatedAt: serverTimestamp(),
    });
    await setDoc(doc(sam.db, `users/${sam.uid}/progress/current`), {
      weekChange: -18, calmerWeeks: 2, keptDays: 5, limitDays: 7, updatedAt: serverTimestamp(),
    });

    // An open question for Bee, and a word in their chat.
    await sam.call("createRequest", {
      type: "extra_time", ...INSTAGRAM, minutes: 15, message: "Finishing a message to my sister", dayKey: dayKey(0),
      approverIds: [bee.uid], usedMinutes: 24, allowedMinutes: 30,
    });
    const chatId = [sam.uid, bee.uid].toSorted().join("_");
    await addDoc(collection(bee.db, `chats/${chatId}/messages`), {
      senderId: bee.uid, kind: "text", text: "Proud of you this week 🌼", createdAt: serverTimestamp(),
    });

    return Object.fromEntries([sam, bee, robin, kai].map((p) => [p.name.toLowerCase(), { uid: p.uid, email: p.email }]));
  } finally {
    await Promise.all(apps.map((a) => deleteApp(a)));
  }
}

/** Whether the demo people already exist (for example, imported from an earlier run). */
export async function isSeeded({ domain = "meadow.test", host = "127.0.0.1" } = {}) {
  const app = initializeApp({ projectId: PROJECT, apiKey: "demo-api-key" }, `seed-check-${domain}`);
  try {
    const auth = getAuth(app);
    connectAuthEmulator(auth, `http://${host}:9099`, { disableWarnings: true });
    await signInWithEmailAndPassword(auth, `sam@${domain}`, PASSWORD);
    return true;
  } catch {
    return false;
  } finally {
    await deleteApp(app);
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (await isSeeded()) {
    console.log("The demo people are already there.");
  } else {
    const people = await seed();
    console.log(`Demo people ready (password "${PASSWORD}"):`);
    for (const [name, { email }] of Object.entries(people)) console.log(`  ${name.padEnd(6)} ${email}`);
  }
  process.exit(0);
}
