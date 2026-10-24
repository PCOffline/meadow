/** Firestore and FCM plumbing shared by every function. */
import { getFirestore, FieldPath, FieldValue, type DocumentSnapshot, type DocumentReference, type SetOptions, type DocumentData, type Transaction } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import type { z } from "zod";
import { errors } from "./copy";

export const db = () => getFirestore();
export const users = () => db().collection("users");
export const invites = () => db().collection("invites");
export const inviteLinks = () => db().collection("inviteLinks");
export const requests = () => db().collection("requests");
export const chats = () => db().collection("chats");
export const friendCodes = () => db().collection("friendCodes");
/** Server-only: when each phone last checked in, so quiet ones can be noticed. */
export const heartbeats = () => db().collection("heartbeats");
/** Server-only: Bumblebee removals waiting out their cool-off. */
export const removals = () => db().collection("removals");
/** Server-only: per-person counters behind the rate limits. */
export const rateLimits = () => db().collection("rateLimits");
/**
 * Owner-only document: who looks after whom, the phones to push to, strict mode, emergency state and
 * other settings. Nothing in it is ever shown to anyone else.
 */
export const accountRef = (uid: string) => users().doc(uid).collection("private").doc("account");
/** A Bumblebee's own copy of a request sent to them, without the other Bumblebees on it. */
export const inboxRef = (beeUid: string, requestId: string) => users().doc(beeUid).collection("inbox").doc(requestId);

export const isEmulator = () => process.env.FUNCTIONS_EMULATOR === "true";

/** The name card: all that the people in someone's circle may see of them. */
export interface Profile {
  displayName: string;
  flower: string;
  avatarColor: string;
  photoUrl: string | null;
}

/**
 * Private to the owner. In code a Bumblebee is an "approver"; the people they look after are
 * "approvees". Nobody else can read these lists: managing Bumblebees is your own business.
 */
export interface Account {
  approverIds: string[];
  approveeIds: string[];
  /** Firebase installation IDs of the person's phones, to push to. */
  pushIds: string[];
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

/** How long a sign-in counts as recent, for steps that can't be undone. */
export const RECENT_SIGN_IN_MS = 5 * 60_000;

/**
 * Steps that can't be undone need a fresh sign-in, so a stolen sign-in token alone can't take
 * them. Accounts without an email have nothing to sign in again with; they're let through, or
 * they could never leave.
 */
export function requireRecentSignIn(req: CallableRequest<unknown>, now = Date.now()): void {
  const token = req.auth?.token;
  if (token?.firebase?.sign_in_provider === "anonymous") return;
  const authTime = token?.auth_time;
  if (typeof authTime !== "number" || now - authTime * 1000 > RECENT_SIGN_IN_MS) {
    throw new HttpsError("unauthenticated", errors.signInAgain);
  }
}

/**
 * Counts one use of `key` for `uid` inside a transaction, refusing once `max` uses fall in the
 * current window (with `message`, or a general "slow down"). Reading the counter also serialises
 * a person's concurrent calls, so duplicate checks made in the same transaction can't be raced by
 * a double tap.
 */
export async function consumeRate(
  tx: Transaction, uid: string, key: string, max: number, windowMs: number, message?: string,
): Promise<() => void> {
  return consumeRates(tx, uid, [{ key, max, windowMs, message }]);
}

/** [consumeRate] for several counters at once, written together. */
export async function consumeRates(
  tx: Transaction,
  uid: string,
  limits: { key: string; max: number; windowMs: number; message?: string }[],
): Promise<() => void> {
  const ref = rateLimits().doc(uid);
  const snap = await tx.get(ref);
  const now = Date.now();
  const next: Record<string, { count: number; since: number }> = {};
  for (const { key, max, windowMs, message } of limits) {
    const entry = snap.get(new FieldPath(key)) as { count?: number; since?: number } | undefined;
    const since = entry?.since;
    const fresh = typeof since !== "number" || now - since >= windowMs;
    const count = fresh ? 0 : (entry?.count ?? 0);
    if (count >= max) throw new HttpsError("resource-exhausted", message ?? errors.slowDown);
    next[key] = { count: count + 1, since: fresh ? now : since };
  }
  // Returned rather than written here, because a transaction must do all its reads first.
  return () => tx.set(ref, next, { merge: true });
}

/** Validates callable input, turning the first problem into a friendly error. */
export function parse<T extends z.ZodType>(schema: T, data: unknown): z.infer<T> {
  // Fields without a message of their own get the generic one, not zod's technical English.
  const result = schema.safeParse(data ?? {}, { error: () => errors.generic });
  if (!result.success) throw new HttpsError("invalid-argument", result.error.issues[0]?.message ?? errors.generic);
  return result.data;
}

const list = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);

/**
 * The neutral name card shown for someone whose profile is gone, such as an account whose
 * deletion only got part of the way.
 */
export const MISSING_PROFILE: Readonly<Profile> = { displayName: "Friend", flower: "🌼", avatarColor: "leaf", photoUrl: null };

/** Reads a name card. A missing one is refused, unless [orMissing] asks for the neutral one instead. */
export function profileOf(snap: DocumentSnapshot, { orMissing = false } = {}): Profile {
  if (!snap.exists) {
    if (orMissing) return { ...MISSING_PROFILE };
    throw new HttpsError("not-found", errors.noProfile);
  }
  const d = snap.data() ?? {};
  return {
    displayName: typeof d.displayName === "string" ? d.displayName : "Friend",
    flower: typeof d.flower === "string" ? d.flower : "🌼",
    avatarColor: typeof d.avatarColor === "string" ? d.avatarColor : "leaf",
    photoUrl: typeof d.photoUrl === "string" ? d.photoUrl : null,
  };
}

export function accountOf(snap: DocumentSnapshot): Account {
  const d = snap.data() ?? {};
  return { approverIds: list(d.approverIds), approveeIds: list(d.approveeIds), pushIds: list(d.pushIds) };
}

/**
 * Reads someone's profile and account, inside a transaction when given one. [orMissing] stands
 * in the neutral name card for a missing profile, so someone half gone can still be let go of.
 */
export async function loadPerson(uid: string, tx?: Transaction, { orMissing = false } = {}): Promise<Person> {
  const read = (ref: DocumentReference) => (tx ? tx.get(ref) : ref.get());
  const [profile, account] = await Promise.all([read(users().doc(uid)), read(accountRef(uid))]);
  return { uid, profile: profileOf(profile, { orMissing }), account: accountOf(account) };
}

export function projectId(): string {
  return process.env.GCLOUD_PROJECT ?? JSON.parse(process.env.FIREBASE_CONFIG ?? "{}").projectId ?? "meadow";
}

type Writer = { set(ref: DocumentReference, data: DocumentData, options: SetOptions): unknown };

/**
 * Creates or refreshes the 1:1 chat between two people, with their names and avatars. A chat
 * closed when they parted ways opens again.
 */
export function ensureChat(writer: Writer, a: Person, b: Person, id: string): void {
  const avatar = (p: Person) => ({ flower: p.profile.flower, color: p.profile.avatarColor, photo: p.profile.photoUrl });
  writer.set(
    chats().doc(id),
    {
      participants: [a.uid, b.uid].toSorted(),
      closed: false,
      names: { [a.uid]: a.profile.displayName, [b.uid]: b.profile.displayName },
      avatars: { [a.uid]: avatar(a), [b.uid]: avatar(b) },
    },
    { merge: true },
  );
}

/**
 * Adds a message to a chat (if it exists and is open) and bumps the chat summary. Only the note
 * saying how two people parted ways goes into a closed chat ([evenIfClosed]). Never throws: it
 * runs once what it describes has already happened, which a failure here mustn't make look failed.
 */
export async function postToChat(
  chatId: string,
  message: { senderId: string; kind: "text" | "request" | "system"; text: string; requestId?: string },
  { evenIfClosed = false } = {},
): Promise<void> {
  try {
    const chatRef = chats().doc(chatId);
    const chat = await chatRef.get();
    if (!chat.exists || (chat.get("closed") === true && !evenIfClosed)) return;
    const batch = db().batch();
    batch.set(chatRef.collection("messages").doc(), { ...message, createdAt: FieldValue.serverTimestamp() });
    batch.update(chatRef, {
      lastMessage: message.text.slice(0, 140),
      lastMessageAt: FieldValue.serverTimestamp(),
      lastSenderId: message.senderId,
    });
    await batch.commit();
  } catch (e) {
    logger.warn("Chat note failed", { chatId, error: String(e) });
  }
}

/**
 * Sends a data-only push to every device of each user; the app builds the notification itself
 * so it can add actions like "Say yes". Phones no longer registered are cleaned up. Never throws.
 *
 * In the emulator every push is also written to `_testOutbox`, so integration tests can check
 * exactly who was told what.
 */
export async function notify(uids: string[], data: Record<string, string>): Promise<void> {
  await Promise.all(
    [...new Set(uids)].map(async (uid) => {
      try {
        if (isEmulator()) await db().collection("_testOutbox").add({ to: uid, ...data, at: FieldValue.serverTimestamp() });
        const fids = accountOf(await accountRef(uid).get()).pushIds;
        if (fids.length === 0) return;
        const result = await getMessaging().sendEachForMulticast({ fids, data, android: { priority: "high" } });
        const dead = fids.filter((_, i) => result.responses[i]?.error?.code === "messaging/installation-id-not-registered");
        if (dead.length) await accountRef(uid).update({ pushIds: FieldValue.arrayRemove(...dead) });
      } catch (e) {
        logger.warn("Push failed", { uid, error: String(e) });
      }
    }),
  );
}
