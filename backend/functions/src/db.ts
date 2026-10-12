/** Firestore and FCM plumbing shared by every function. */
import { getFirestore, FieldValue, DocumentSnapshot, DocumentReference, SetOptions, DocumentData } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { z } from "zod";
import { errors } from "./copy";

export const db = () => getFirestore();
export const users = () => db().collection("users");
export const invites = () => db().collection("invites");
export const inviteLinks = () => db().collection("inviteLinks");
export const requests = () => db().collection("requests");
export const chats = () => db().collection("chats");
export const friendCodes = () => db().collection("friendCodes");

/** A user's profile. In code a Bumblebee is an "approver"; the people they look after are "approvees". */
export interface UserDoc {
  displayName: string;
  flower: string;
  friendCode: string;
  approverIds: string[];
  approveeIds: string[];
  strictMode: boolean;
  fcmTokens: string[];
}

export function requireAuth(req: CallableRequest<unknown>): string {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", errors.signIn);
  return uid;
}

/** Commitments must survive a reinstall, so inviting Bumblebees needs an account with an email. */
export function requireEmailAccount(req: CallableRequest<unknown>): void {
  if (req.auth?.token.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError("failed-precondition", errors.needsEmail);
  }
}

/** Validates callable input, turning the first problem into a friendly error. */
export function parse<T extends z.ZodType>(schema: T, data: unknown): z.infer<T> {
  const result = schema.safeParse(data ?? {});
  if (!result.success) throw new HttpsError("invalid-argument", result.error.issues[0]?.message ?? errors.generic);
  return result.data;
}

export function userOf(snap: DocumentSnapshot): UserDoc {
  if (!snap.exists) throw new HttpsError("not-found", errors.noProfile);
  const d = snap.data() ?? {};
  const list = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);
  return {
    displayName: typeof d.displayName === "string" ? d.displayName : "Friend",
    flower: typeof d.flower === "string" ? d.flower : "🌼",
    friendCode: typeof d.friendCode === "string" ? d.friendCode : "",
    approverIds: list(d.approverIds),
    approveeIds: list(d.approveeIds),
    strictMode: d.strictMode === true,
    fcmTokens: list(d.fcmTokens),
  };
}

export function projectId(): string {
  return process.env.GCLOUD_PROJECT ?? JSON.parse(process.env.FIREBASE_CONFIG ?? "{}").projectId ?? "meadow";
}

type Writer = { set(ref: DocumentReference, data: DocumentData, options: SetOptions): unknown };

/** Creates or refreshes the 1:1 chat between two people. */
export function ensureChat(writer: Writer, a: { uid: string; user: UserDoc }, b: { uid: string; user: UserDoc }, id: string): void {
  writer.set(
    chats().doc(id),
    {
      participants: [a.uid, b.uid].sort(),
      names: { [a.uid]: a.user.displayName, [b.uid]: b.user.displayName },
      flowers: { [a.uid]: a.user.flower, [b.uid]: b.user.flower },
    },
    { merge: true },
  );
}

/** Adds a message to a chat (if it exists) and bumps the chat summary. */
export async function postToChat(
  chatId: string,
  message: { senderId: string; kind: "text" | "request" | "system"; text: string; requestId?: string },
): Promise<void> {
  const chatRef = chats().doc(chatId);
  if (!(await chatRef.get()).exists) return;
  const batch = db().batch();
  batch.set(chatRef.collection("messages").doc(), { ...message, createdAt: FieldValue.serverTimestamp() });
  batch.update(chatRef, {
    lastMessage: message.text.slice(0, 140),
    lastMessageAt: FieldValue.serverTimestamp(),
    lastSenderId: message.senderId,
  });
  await batch.commit();
}

/**
 * Sends a data-only push to every device of each user; the app builds the notification itself
 * so it can add actions like "Say yes". Dead tokens are cleaned up. Never throws.
 */
export async function notify(uids: string[], data: Record<string, string>): Promise<void> {
  await Promise.all(
    [...new Set(uids)].map(async (uid) => {
      try {
        const tokens = userOf(await users().doc(uid).get()).fcmTokens;
        if (tokens.length === 0) return;
        const result = await getMessaging().sendEachForMulticast({ tokens, data, android: { priority: "high" } });
        const dead = tokens.filter((_, i) => {
          const code = result.responses[i]?.error?.code;
          return code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token";
        });
        if (dead.length) await users().doc(uid).update({ fcmTokens: FieldValue.arrayRemove(...dead) });
      } catch (e) {
        logger.warn("Push failed", { uid, error: String(e) });
      }
    }),
  );
}
