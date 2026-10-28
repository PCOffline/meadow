import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onObjectFinalized } from "firebase-functions/v2/storage";
import { logger } from "firebase-functions/v2";
import { inChunks } from "./chunks";
import { chatOfPicture, isChatPicture, uploadId } from "./format";
import { chatMedia, pushes } from "./copy";
import { accountOf, accountRef, chats, chatUploads, db, heartbeats, invites, isEmulator, loadPerson, notify, profileOf, users } from "./db";
import { expireStaleRequests } from "./requests";
import { finishDueRemovals } from "./social";

const ALERT_COOLDOWN_MS = 2 * 60_000;
const STALE_HEARTBEAT_MS = 6 * 60 * 60_000;

const STALE_UPLOAD_MS = 60 * 60_000;

/**
 * Notes each chat picture as it's uploaded. A picture is sent by a message naming it, which the
 * app writes once the upload is done; one never sent (the message was refused, the app was
 * stopped, or the uploads weren't the app's) is deleted by [sweepUnsentPictures], so nobody can
 * fill Storage with pictures nobody sees.
 */
export const onChatUpload = onObjectFinalized(async (event) => {
  const path = event.data.name;
  if (!isChatPicture(path)) return;
  await chatUploads().doc(uploadId(path)).set({ path, at: FieldValue.serverTimestamp() });
});

/**
 * Deletes chat pictures uploaded over an hour before [now] that no message sent; sent ones are
 * only crossed off. Pictures of chats that closed meanwhile are gone already.
 */
export async function sweepUnsentPictures(now: number): Promise<number> {
  const due = await chatUploads().where("at", "<", Timestamp.fromMillis(now - STALE_UPLOAD_MS)).limit(400).get();
  let deleted = 0;
  await inChunks(due.docs, async (record) => {
    const path = record.get("path") as string;
    try {
      const sent = await chats().doc(chatOfPicture(path)).collection("messages").where("path", "==", path).limit(1).get();
      if (sent.empty) {
        await getStorage().bucket().file(path).delete({ ignoreNotFound: true });
        deleted++;
      }
      await record.ref.delete();
    } catch (e) {
      // Left for the next run.
      logger.warn("Unsent picture not swept", { path, error: String(e) });
    }
  });
  return deleted;
}

/** Pushes new chat messages, words or pictures, to the other person, while the chat is open. */
export const onChatMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (message?.kind !== "text" && message?.kind !== "image") return;
  // Sent: the picture stays. (The record may only be written after this; the sweep checks again.)
  if (message.kind === "image" && typeof message.path === "string") {
    await chatUploads().doc(uploadId(message.path)).delete().catch(() => undefined);
  }
  const text = message.kind === "image" ? (message.gif === true ? chatMedia.gif : chatMedia.photo) : String(message.text ?? "");
  const chat = await chats().doc(event.params.chatId).get();
  if (!chat.exists || chat.get("closed") === true) return;
  const participants = (chat.get("participants") as string[] | undefined) ?? [];
  const names = (chat.get("names") as Record<string, string> | undefined) ?? {};
  const avatars = (chat.get("avatars") as Record<string, { flower?: string; photo?: string | null }> | undefined) ?? {};
  const sender = String(message.senderId);
  const person = { displayName: names[sender] ?? "Friend", flower: avatars[sender]?.flower ?? "🌼", photoUrl: avatars[sender]?.photo ?? null };
  await notify(participants.filter((p) => p !== sender), {
    type: "chat",
    chatId: event.params.chatId,
    ...pushes.chat(person, text.slice(0, 300)),
  });
});

/**
 * Tells Bumblebees, vaguely, when Meadow may have stopped protecting someone's garden. Which
 * setting changed stays private. Rate-limited per person.
 */
export const onProtectionAlert = onDocumentCreated("users/{uid}/alerts/{alertId}", async (event) => {
  if (!event.data?.exists) return;
  const stateRef = accountRef(event.params.uid);
  const shouldSend = await db().runTransaction(async (tx) => {
    const snap = await tx.get(stateRef);
    const last = (snap.get("lastAlertAt") as Timestamp | undefined)?.toMillis() ?? 0;
    if (Date.now() - last < ALERT_COOLDOWN_MS) return false;
    tx.update(stateRef, { lastAlertAt: FieldValue.serverTimestamp() });
    return true;
  });
  // The alert itself carries nothing worth keeping.
  await event.data.ref.delete().catch(() => undefined);
  if (!shouldSend) return;
  const person = await loadPerson(event.params.uid);
  await notify(person.account.approverIds, { type: "alert", uid: person.uid, ...pushes.protectionOff(person.profile) });
});

/**
 * Keeps names and avatars in open chats, and in invites still waiting, up to date when someone
 * edits their profile. A closed chat (the two parted ways) never gets them again.
 */
export const onProfileUpdated = onDocumentUpdated("users/{uid}", async (event) => {
  const before = event.data?.before;
  const after = event.data?.after;
  if (!before?.exists || !after?.exists) return;
  const fields = ["displayName", "flower", "avatarColor", "photoUrl"];
  if (fields.every((f) => before.get(f) === after.get(f))) return;
  const uid = event.params.uid;
  const profile = profileOf(after);
  const chatsOfTheirs = (await chats().where("participants", "array-contains", uid).get()).docs;
  // Each in a transaction, so a chat closing meanwhile isn't handed the new name and picture.
  await Promise.all(chatsOfTheirs.map((chat) => db().runTransaction(async (tx) => {
    const fresh = await tx.get(chat.ref);
    // A chat deleted meanwhile (its other person left Meadow) needs no update.
    if (!fresh.exists || fresh.get("closed") === true) return;
    tx.update(chat.ref, {
      [`names.${uid}`]: profile.displayName,
      [`avatars.${uid}`]: { flower: profile.flower, color: profile.avatarColor, photo: profile.photoUrl },
    });
  }).catch((e) => logger.warn("Chat profile update failed", { chat: chat.id, error: String(e) }))));
  // Invites still waiting for an answer show the names and flower they were sent with.
  const [sent, got] = await Promise.all([
    invites().where("fromUid", "==", uid).where("status", "==", "pending").get(),
    invites().where("toUid", "==", uid).where("status", "==", "pending").get(),
  ]);
  if (sent.empty && got.empty) return;
  const batch = db().batch();
  for (const invite of sent.docs) batch.update(invite.ref, { fromName: profile.displayName, fromFlower: profile.flower });
  for (const invite of got.docs) batch.update(invite.ref, { toName: profile.displayName });
  await batch.commit().catch((e) => logger.warn("Invite profile update failed", { error: String(e) }));
});

/**
 * Profiles from before the name-card split kept settings and status on the public profile.
 * Moves them to the owner-only account. Safe to run again.
 */
const PRIVATE_FIELDS = [
  "friendCode", "strictMode", "emergencyUntil", "emergencyUses", "emergencyPackage", "offlineEmergencyAt", "lastAlertAt",
] as const;
const RETIRED_FIELDS = ["protection", "privacy", "lastHeartbeat", "staleNotified"] as const;

export async function migrateProfiles(limit = 200): Promise<number> {
  // Only documents that still have a friend code on the public profile are listed.
  const legacy = await users().orderBy("friendCode").limit(limit).get();
  for (const doc of legacy.docs) {
    const d = doc.data();
    const moved: Record<string, unknown> = {};
    for (const f of PRIVATE_FIELDS) if (d[f] !== undefined) moved[f] = d[f];
    const usage = d.privacy?.shareUsage !== false;
    moved.privacy = { shareLimits: true, shareTime: usage, shareApps: usage, shareProgress: false };
    moved.pendingRemovals = {};
    const batch = db().batch();
    batch.set(accountRef(doc.id), moved, { merge: true });
    batch.set(heartbeats().doc(doc.id), {
      at: d.lastHeartbeat ?? FieldValue.serverTimestamp(),
      staleNotified: d.staleNotified === true,
    }, { merge: true });
    batch.update(doc.ref, Object.fromEntries([...PRIVATE_FIELDS, ...RETIRED_FIELDS].map((f) => [f, FieldValue.delete()])));
    await batch.commit();
  }
  return legacy.size;
}

/** Requests are short-lived: settle unanswered ones soon after they expire. */
export const expireRequests = onSchedule("every 5 minutes", async () => {
  const expired = await expireStaleRequests(Date.now());
  if (expired > 0) logger.info("Expired requests", { expired });
});

/** Hourly housekeeping: finish Bumblebee removals, delete unsent chat pictures, notice quiet phones. */
export async function housekeeping(now: number) {
  const migrated = await migrateProfiles();

  const removed = await finishDueRemovals(now);
  const unsentPictures = await sweepUnsentPictures(now);

  // Only phones not yet looked at, oldest first. Every one looked at is marked, even a garden
  // nobody looks after, so quiet phones that are never reported don't crowd out new ones. The
  // mark is cleared below once the phone checks in again.
  const cutoff = Timestamp.fromMillis(now - STALE_HEARTBEAT_MS);
  const quiet = await heartbeats().where("staleNotified", "==", false).where("at", "<", cutoff).limit(400).get();
  await inChunks(quiet.docs, async (doc) => {
    const [account, limits, profile] = await Promise.all([
      accountRef(doc.id).get(),
      users().doc(doc.id).collection("limits").limit(1).get(),
      users().doc(doc.id).get(),
    ]);
    await doc.ref.update({ staleNotified: true });
    const approverIds = accountOf(account).approverIds;
    if (approverIds.length === 0 || limits.empty || !profile.exists) return;
    await notify(approverIds, { type: "alert", uid: doc.id, ...pushes.quietPhone(profileOf(profile)) });
  });

  const back = await heartbeats().where("staleNotified", "==", true).where("at", ">=", cutoff).limit(400).get();
  if (!back.empty) {
    const batch = db().batch();
    for (const d of back.docs) batch.update(d.ref, { staleNotified: false });
    await batch.commit();
  }
  const done = { migrated, removed, unsentPictures, quiet: quiet.size, recovered: back.size };
  logger.info("Hourly care done", done);
  return done;
}

export const hourlyCare = onSchedule({ schedule: "every 60 minutes", timeoutSeconds: 540 }, async () => {
  await housekeeping(Date.now());
});

/**
 * Emulator only, for integration tests: the emulators don't run schedules, so this runs the
 * scheduled jobs on demand, as if it were [now]. It doesn't exist in a deployed project.
 */
export const runSchedulesForTests = isEmulator()
  ? onCall(async (req) => {
    const now = typeof req.data?.now === "number" ? req.data.now : Date.now();
    return { expired: await expireStaleRequests(now), ...(await housekeeping(now)) };
  })
  : undefined;
