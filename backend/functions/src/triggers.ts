import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { pushes } from "./copy";
import { accountOf, accountRef, chats, db, heartbeats, inboxRef, loadPerson, notify, profileOf, requests, users } from "./db";
import { finishDueRemovals } from "./social";

const ALERT_COOLDOWN_MS = 2 * 60_000;
const REQUEST_TTL_MS = 24 * 60 * 60_000;
const STALE_HEARTBEAT_MS = 6 * 60 * 60_000;

/** Pushes new chat messages to the other person. */
export const onChatMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message || message.kind !== "text") return;
  const chat = await chats().doc(event.params.chatId).get();
  if (!chat.exists) return;
  const participants = (chat.get("participants") as string[] | undefined) ?? [];
  const names = (chat.get("names") as Record<string, string> | undefined) ?? {};
  const avatars = (chat.get("avatars") as Record<string, { flower?: string }> | undefined) ?? {};
  const sender = String(message.senderId);
  const person = { displayName: names[sender] ?? "Friend", flower: avatars[sender]?.flower ?? "🌼" };
  await notify(participants.filter((p) => p !== sender), {
    type: "chat",
    chatId: event.params.chatId,
    ...pushes.chat(person, String(message.text ?? "").slice(0, 300)),
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

/** Keeps names and avatars in chats up to date when someone edits their profile. */
export const onProfileUpdated = onDocumentUpdated("users/{uid}", async (event) => {
  const before = event.data?.before;
  const after = event.data?.after;
  if (!before?.exists || !after?.exists) return;
  const fields = ["displayName", "flower", "avatarColor", "photoUrl"];
  if (fields.every((f) => before.get(f) === after.get(f))) return;
  const uid = event.params.uid;
  const profile = profileOf(after);
  const writer = db().bulkWriter();
  for (const chat of (await chats().where("participants", "array-contains", uid).get()).docs) {
    // A chat deleted meanwhile (its other person left Meadow) needs no update.
    void writer
      .update(chat.ref, {
        [`names.${uid}`]: profile.displayName,
        [`avatars.${uid}`]: { flower: profile.flower, color: profile.avatarColor, photo: profile.photoUrl },
      })
      .catch((e) => logger.warn("Chat profile update failed", { chat: chat.id, error: String(e) }));
  }
  await writer.close();
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
    moved.privacy = { shareUsage: d.privacy?.shareUsage !== false };
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

/** Hourly housekeeping: expire old requests, finish Bumblebee removals, notice quiet phones. */
export const hourlyCare = onSchedule("every 60 minutes", async () => {
  const now = Date.now();
  const migrated = await migrateProfiles();

  const expired = await requests()
    .where("status", "==", "pending")
    .where("createdAt", "<", Timestamp.fromMillis(now - REQUEST_TTL_MS))
    .limit(400)
    .get();
  if (!expired.empty) {
    const writer = db().bulkWriter();
    for (const d of expired.docs) {
      void writer.update(d.ref, { status: "expired" }).catch(() => undefined);
      for (const beeUid of (d.get("approverIds") as string[] | undefined) ?? []) {
        // Requests from before inboxes have no copies; those updates just fail quietly.
        void writer.update(inboxRef(beeUid, d.id), { status: "expired", answeredByMe: false }).catch(() => undefined);
      }
    }
    await writer.close();
  }

  const removed = await finishDueRemovals(now);

  // Only phones not yet reported, so ones that stay quiet for good don't crowd out new ones.
  const cutoff = Timestamp.fromMillis(now - STALE_HEARTBEAT_MS);
  const quiet = await heartbeats().where("staleNotified", "==", false).where("at", "<", cutoff).limit(400).get();
  for (const doc of quiet.docs) {
    const approverIds = accountOf(await accountRef(doc.id).get()).approverIds;
    if (approverIds.length === 0) continue;
    if ((await users().doc(doc.id).collection("limits").limit(1).get()).empty) continue;
    const profile = await users().doc(doc.id).get();
    if (!profile.exists) continue;
    await doc.ref.update({ staleNotified: true });
    await notify(approverIds, { type: "alert", uid: doc.id, ...pushes.quietPhone(profileOf(profile)) });
  }

  const back = await heartbeats().where("staleNotified", "==", true).where("at", ">=", cutoff).limit(400).get();
  if (!back.empty) {
    const batch = db().batch();
    back.docs.forEach((d) => batch.update(d.ref, { staleNotified: false }));
    await batch.commit();
  }
  logger.info("Hourly care done", { migrated, expired: expired.size, removed, quiet: quiet.size, recovered: back.size });
});
