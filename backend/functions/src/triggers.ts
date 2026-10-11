import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { chats, db, notify, requests, users, userOf } from "./shared";

const ALERT_COOLDOWN_MS = 2 * 60_000;
const REQUEST_TTL_MS = 24 * 60 * 60_000;
const STALE_HEARTBEAT_MS = 6 * 60 * 60_000;

/** Pushes new chat messages to the other participant. */
export const onChatMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message || message.kind !== "text") return;
  const chat = await chats().doc(event.params.chatId).get();
  if (!chat.exists) return;
  const participants = (chat.get("participants") as string[]) ?? [];
  const names = (chat.get("names") as Record<string, string>) ?? {};
  const flowers = (chat.get("flowers") as Record<string, string>) ?? {};
  const sender = message.senderId as string;
  await notify(participants.filter((p) => p !== sender), {
    type: "chat",
    chatId: event.params.chatId,
    title: `${flowers[sender] ?? "🌼"} ${names[sender] ?? "Friend"}`,
    body: String(message.text ?? "").slice(0, 300),
  });
});

/** Tells approvers when someone switches a protection off. */
export const onProtectionAlert = onDocumentCreated("users/{uid}/alerts/{alertId}", async (event) => {
  const alert = event.data?.data();
  if (!alert) return;
  const uid = event.params.uid;
  const userRef = users().doc(uid);
  const shouldSend = await db().runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    const last = (snap.get("lastAlertAt") as Timestamp | undefined)?.toMillis() ?? 0;
    if (Date.now() - last < ALERT_COOLDOWN_MS) return false;
    tx.update(userRef, { lastAlertAt: FieldValue.serverTimestamp() });
    return true;
  });
  if (!shouldSend) return;
  const user = userOf(await userRef.get());
  await notify(user.approverIds, {
    type: "alert",
    title: `⚠️ ${user.displayName} ${String(alert.detail ?? "changed their protections")}`,
    body: "Their limits may not be enforced right now. Maybe check in with them?",
  });
});

/** Hourly housekeeping: expire old requests, flag phones that went quiet. */
export const hourlyCare = onSchedule("every 60 minutes", async () => {
  const now = Date.now();

  const expired = await requests()
    .where("status", "==", "pending")
    .where("createdAt", "<", Timestamp.fromMillis(now - REQUEST_TTL_MS))
    .limit(400)
    .get();
  if (!expired.empty) {
    const batch = db().batch();
    expired.docs.forEach((d) => batch.update(d.ref, { status: "expired" }));
    await batch.commit();
  }

  const cutoff = Timestamp.fromMillis(now - STALE_HEARTBEAT_MS);
  const quiet = await users().where("lastHeartbeat", "<", cutoff).limit(400).get();
  for (const doc of quiet.docs) {
    if (doc.get("staleNotified") === true) continue;
    const user = userOf(doc);
    if (user.approverIds.length === 0) continue;
    const hasLimits = !(await doc.ref.collection("limits").limit(1).get()).empty;
    if (!hasLimits) continue;
    await doc.ref.update({ staleNotified: true });
    await notify(user.approverIds, {
      type: "alert",
      title: `🌙 ${user.displayName}'s phone has gone quiet`,
      body: "Meadow hasn't heard from their phone in over 6 hours. It may be off, or Meadow may have been removed.",
    });
  }

  const recovered = await users().where("staleNotified", "==", true).limit(400).get();
  const batch = db().batch();
  let changes = 0;
  recovered.docs.forEach((d) => {
    const beat = (d.get("lastHeartbeat") as Timestamp | undefined)?.toMillis() ?? 0;
    if (beat >= cutoff.toMillis()) {
      batch.update(d.ref, { staleNotified: false });
      changes++;
    }
  });
  if (changes) await batch.commit();
  logger.info("Hourly care done", { expired: expired.size, quiet: quiet.size, recovered: changes });
});
