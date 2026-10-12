import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { pushes } from "./copy";
import { chats, db, notify, requests, userOf, users } from "./db";

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
  const flowers = (chat.get("flowers") as Record<string, string> | undefined) ?? {};
  const sender = String(message.senderId);
  const person = { displayName: names[sender] ?? "Friend", flower: flowers[sender] ?? "🌼" };
  await notify(participants.filter((p) => p !== sender), {
    type: "chat",
    chatId: event.params.chatId,
    ...pushes.chat(person, String(message.text ?? "").slice(0, 300)),
  });
});

/** Tells Bumblebees when someone switches a protection off. Rate-limited per person. */
export const onProtectionAlert = onDocumentCreated("users/{uid}/alerts/{alertId}", async (event) => {
  const alert = event.data?.data();
  if (!alert) return;
  const userRef = users().doc(event.params.uid);
  const shouldSend = await db().runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    const last = (snap.get("lastAlertAt") as Timestamp | undefined)?.toMillis() ?? 0;
    if (Date.now() - last < ALERT_COOLDOWN_MS) return false;
    tx.update(userRef, { lastAlertAt: FieldValue.serverTimestamp() });
    return true;
  });
  if (!shouldSend) return;
  const user = userOf(await userRef.get());
  const lost = Array.isArray(alert.lost) ? alert.lost.filter((k: unknown): k is string => typeof k === "string") : [];
  await notify(user.approverIds, { type: "alert", uid: event.params.uid, ...pushes.protectionOff(user, lost) });
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
    if ((await doc.ref.collection("limits").limit(1).get()).empty) continue;
    await doc.ref.update({ staleNotified: true });
    await notify(user.approverIds, { type: "alert", ...pushes.quietPhone(user) });
  }

  const recovered = await users().where("staleNotified", "==", true).limit(400).get();
  const back = recovered.docs.filter((d) => ((d.get("lastHeartbeat") as Timestamp | undefined)?.toMillis() ?? 0) >= cutoff.toMillis());
  if (back.length) {
    const batch = db().batch();
    back.forEach((d) => batch.update(d.ref, { staleNotified: false }));
    await batch.commit();
  }
  logger.info("Hourly care done", { expired: expired.size, quiet: quiet.size, recovered: back.length });
});
