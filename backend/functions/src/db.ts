/** Firestore and FCM plumbing shared by every function. */
import { getFirestore, FieldValue, DocumentSnapshot, DocumentReference, SetOptions, DocumentData, Transaction } from "firebase-admin/firestore";
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
/** Owner-only document holding who looks after whom, and push tokens. */
export const accountRef = (uid: string) => users().doc(uid).collection("private").doc("account");

export const isEmulator = () => process.env.FUNCTIONS_EMULATOR === "true";

/** What other people in someone's circle may see. */
export interface Profile {
  displayName: string;
  flower: string;
  avatarColor: string;
  photoUrl: string | null;
  strictMode: boolean;
}

/**
 * Private to the owner. In code a Bumblebee is an "approver"; the people they look after are
 * "approvees". Nobody else can read these lists: managing Bumblebees is your own business.
 */
export interface Account {
  approverIds: string[];
  approveeIds: string[];
  fcmTokens: string[];
}

export interface Person {
  uid: string;
  profile: Profile;
  account: Account;
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

const list = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);

export function profileOf(snap: DocumentSnapshot): Profile {
  if (!snap.exists) throw new HttpsError("not-found", errors.noProfile);
  const d = snap.data() ?? {};
  return {
    displayName: typeof d.displayName === "string" ? d.displayName : "Friend",
    flower: typeof d.flower === "string" ? d.flower : "🌼",
    avatarColor: typeof d.avatarColor === "string" ? d.avatarColor : "leaf",
    photoUrl: typeof d.photoUrl === "string" ? d.photoUrl : null,
    strictMode: d.strictMode === true,
  };
}

export function accountOf(snap: DocumentSnapshot): Account {
  const d = snap.data() ?? {};
  return { approverIds: list(d.approverIds), approveeIds: list(d.approveeIds), fcmTokens: list(d.fcmTokens) };
}

/** Reads someone's profile and account, inside a transaction when given one. */
export async function loadPerson(uid: string, tx?: Transaction): Promise<Person> {
  const read = (ref: DocumentReference) => (tx ? tx.get(ref) : ref.get());
  const [profile, account] = await Promise.all([read(users().doc(uid)), read(accountRef(uid))]);
  return { uid, profile: profileOf(profile), account: accountOf(account) };
}

export function projectId(): string {
  return process.env.GCLOUD_PROJECT ?? JSON.parse(process.env.FIREBASE_CONFIG ?? "{}").projectId ?? "meadow";
}

type Writer = { set(ref: DocumentReference, data: DocumentData, options: SetOptions): unknown };

/** Creates or refreshes the 1:1 chat between two people, with their names and avatars. */
export function ensureChat(writer: Writer, a: Person, b: Person, id: string): void {
  const avatar = (p: Person) => ({ flower: p.profile.flower, color: p.profile.avatarColor, photo: p.profile.photoUrl });
  writer.set(
    chats().doc(id),
    {
      participants: [a.uid, b.uid].sort(),
      names: { [a.uid]: a.profile.displayName, [b.uid]: b.profile.displayName },
      avatars: { [a.uid]: avatar(a), [b.uid]: avatar(b) },
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
 *
 * In the emulator every push is also written to `_testOutbox`, so integration tests can check
 * exactly who was told what.
 */
export async function notify(uids: string[], data: Record<string, string>): Promise<void> {
  await Promise.all(
    [...new Set(uids)].map(async (uid) => {
      try {
        if (isEmulator()) await db().collection("_testOutbox").add({ to: uid, ...data, at: FieldValue.serverTimestamp() });
        const tokens = accountOf(await accountRef(uid).get()).fcmTokens;
        if (tokens.length === 0) return;
        const result = await getMessaging().sendEachForMulticast({ tokens, data, android: { priority: "high" } });
        const dead = tokens.filter((_, i) => {
          const code = result.responses[i]?.error?.code;
          return code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token";
        });
        if (dead.length) await accountRef(uid).update({ fcmTokens: FieldValue.arrayRemove(...dead) });
      } catch (e) {
        logger.warn("Push failed", { uid, error: String(e) });
      }
    }),
  );
}
