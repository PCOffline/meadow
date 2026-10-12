import { randomInt } from "node:crypto";
import { getFirestore, FieldValue, DocumentSnapshot } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { z } from "zod";
import { logger } from "firebase-functions/v2";

export const db = () => getFirestore();
export const users = () => db().collection("users");
export const invites = () => db().collection("invites");
export const requests = () => db().collection("requests");
export const chats = () => db().collection("chats");
export const friendCodes = () => db().collection("friendCodes");

export interface UserDoc {
  displayName: string;
  flower: string;
  friendCode: string;
  approverIds: string[];
  approveeIds: string[];
  strictMode: boolean;
  fcmTokens?: string[];
}

export type RequestType = "extra_time" | "change_limit" | "remove_limit" | "disable_strict_mode";

export interface RequestDoc {
  requesterId: string;
  requesterName: string;
  type: RequestType;
  packageName: string | null;
  appName: string | null;
  minutes: number;
  dailyMinutes: number;
  weeklyMinutes: number;
  message: string;
  status: "pending" | "approved" | "denied" | "cancelled" | "expired";
  approverIds: string[];
  dayKey: string;
}

export function requireAuth(req: CallableRequest<unknown>): string {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Please sign in first.");
  return uid;
}

/** Validates callable input with a zod schema, turning the first problem into a friendly error. */
export function parse<T extends z.ZodType>(schema: T, data: unknown): z.infer<T> {
  const result = schema.safeParse(data ?? {});
  if (!result.success) {
    throw new HttpsError("invalid-argument", result.error.issues[0]?.message ?? "Something in that request wasn't right.");
  }
  return result.data;
}

export function userOf(snap: DocumentSnapshot): UserDoc {
  if (!snap.exists) throw new HttpsError("not-found", "That person doesn't have a Meadow profile.");
  const d = snap.data() ?? {};
  return {
    displayName: d.displayName ?? "Friend",
    flower: d.flower ?? "🌼",
    friendCode: d.friendCode ?? "",
    approverIds: Array.isArray(d.approverIds) ? d.approverIds : [],
    approveeIds: Array.isArray(d.approveeIds) ? d.approveeIds : [],
    strictMode: d.strictMode === true,
    fcmTokens: Array.isArray(d.fcmTokens) ? d.fcmTokens : [],
  };
}

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function randomCode(length = 6): string {
  let code = "";
  for (let i = 0; i < length; i++) code += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  return code;
}

export function chatIdFor(a: string, b: string): string {
  return [a, b].sort().join("_");
}

export function formatMinutes(minutes: number): string {
  if (minutes <= 0) return "0m";
  if (minutes < 60) return `${minutes}m`;
  if (minutes % 60 === 0) return `${minutes / 60}h`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

export function limitText(daily: number, weekly: number): string {
  const parts: string[] = [];
  if (daily > 0) parts.push(`${formatMinutes(daily)} a day`);
  if (weekly > 0) parts.push(`${formatMinutes(weekly)} a week`);
  return parts.length ? parts.join(" · ") : "no limit";
}

export function summarize(r: RequestDoc): string {
  const app = r.appName ?? "an app";
  switch (r.type) {
    case "extra_time":
      return `${r.minutes} more minutes on ${app}`;
    case "change_limit":
      return `change ${app} to ${limitText(r.dailyMinutes, r.weeklyMinutes)}`;
    case "remove_limit":
      return `remove the limit on ${app}`;
    case "disable_strict_mode":
      return "turn off strict mode";
  }
}

/** Ensures a 1:1 chat exists between two people, refreshing names and flowers. */
export function ensureChat(
  writer: { set: (ref: FirebaseFirestore.DocumentReference, data: FirebaseFirestore.DocumentData, options: FirebaseFirestore.SetOptions) => unknown },
  a: { uid: string; user: UserDoc },
  b: { uid: string; user: UserDoc },
): string {
  const id = chatIdFor(a.uid, b.uid);
  writer.set(
    chats().doc(id),
    {
      participants: [a.uid, b.uid].sort(),
      names: { [a.uid]: a.user.displayName, [b.uid]: b.user.displayName },
      flowers: { [a.uid]: a.user.flower, [b.uid]: b.user.flower },
    },
    { merge: true },
  );
  return id;
}

/** Adds a message to a chat (if it exists) and bumps the chat summary. */
export async function postToChat(
  chatId: string,
  message: { senderId: string; kind: "text" | "request" | "system"; text: string; requestId?: string },
): Promise<void> {
  const chatRef = chats().doc(chatId);
  const chat = await chatRef.get();
  if (!chat.exists) return;
  const batch = db().batch();
  batch.set(chatRef.collection("messages").doc(), {
    ...message,
    createdAt: FieldValue.serverTimestamp(),
  });
  batch.update(chatRef, {
    lastMessage: message.text.slice(0, 140),
    lastMessageAt: FieldValue.serverTimestamp(),
    lastSenderId: message.senderId,
  });
  await batch.commit();
}

/**
 * Sends a data-only push to every device of each user. The app builds the notification
 * itself so it can add actions like "Say yes". Dead tokens are cleaned up.
 */
export async function notify(uids: string[], data: Record<string, string>): Promise<void> {
  await Promise.all(
    [...new Set(uids)].map(async (uid) => {
      const snap = await users().doc(uid).get();
      const tokens: string[] = (snap.get("fcmTokens") as string[] | undefined) ?? [];
      if (tokens.length === 0) return;
      try {
        const result = await getMessaging().sendEachForMulticast({
          tokens,
          data,
          android: { priority: "high" },
        });
        const dead = result.responses
          .map((r, i) => ({ r, token: tokens[i] }))
          .filter(({ r }) => {
            const code = r.error?.code;
            return code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token";
          })
          .map(({ token }) => token);
        if (dead.length) await users().doc(uid).update({ fcmTokens: FieldValue.arrayRemove(...dead) });
      } catch (e) {
        logger.warn("Push failed", { uid, error: String(e) });
      }
    }),
  );
}

/** Writes an invite asking `to` to approve for `from`, and lets them know. */
export async function createInvite(fromUid: string, from: UserDoc, toUid: string, to: UserDoc): Promise<string> {
  const ref = invites().doc();
  await ref.set({
    fromUid,
    fromName: from.displayName,
    fromFlower: from.flower,
    toUid,
    toName: to.displayName,
    status: "pending",
    createdAt: FieldValue.serverTimestamp(),
  });
  await notify([toUid], {
    type: "invite",
    inviteId: ref.id,
    title: `${from.flower} ${from.displayName} would like you as their Petal Pal`,
    body: "Open Meadow to accept. You'll get to say yes or no when they ask for more time.",
  });
  return ref.id;
}
