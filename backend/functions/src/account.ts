/** Data rights: download everything Meadow holds about you, or delete it for good. */
import { getAuth } from "firebase-admin/auth";
import { DocumentData, FieldValue, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { pushes } from "./copy";
import {
  accountRef, chats, consumeRate, db, friendCodes, heartbeats, inboxRef, inviteLinks, invites, loadPerson, notify, rateLimits,
  removals, requests, requireAuth, users,
} from "./db";

const EXPORTS_PER_DAY = 5;
export const exportPath = (uid: string, at: number) => `exports/${uid}/meadow-${at}.json`;

/** The app deletes an export once it has it; one it never collected is deleted after this long. */
export const EXPORT_TTL_MS = 24 * 60 * 60_000;

/** Whether the export at [path] is older than [EXPORT_TTL_MS]. A name that isn't an export's counts as stale. */
export function isStaleExport(path: string, now: number): boolean {
  const at = Number(/\/meadow-(\d+)\.json$/.exec(path)?.[1]);
  return !Number.isFinite(at) || now - at > EXPORT_TTL_MS;
}

/** Deletes exports nobody collected, so personal data never lingers in Storage. */
export async function deleteStaleExports(now: number): Promise<number> {
  const [files] = await getStorage().bucket().getFiles({ prefix: "exports/" });
  const stale = files.filter((f) => isStaleExport(f.name, now));
  await Promise.all(stale.map((f) => f.delete({ ignoreNotFound: true })));
  return stale.length;
}

/** Converts Firestore values (timestamps, nested maps) into plain JSON. */
function plain(value: unknown): unknown {
  if (value instanceof Timestamp) return value.toDate().toISOString();
  if (Array.isArray(value)) return value.map(plain);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value as DocumentData).map(([k, v]) => [k, plain(v)]));
  }
  return value;
}

const docs = async (query: FirebaseFirestore.Query) =>
  (await query.get()).docs.map((d) => ({ id: d.id, ...(plain(d.data()) as object) }));

/**
 * Everything Meadow stores about the caller, as one JSON file in their private Storage folder.
 * A file rather than a reply, so a long history can't outgrow the response size limit. The
 * app downloads it and then deletes it.
 */
export const exportMyData = onCall({ timeoutSeconds: 120, memory: "512MiB" }, async (req) => {
  const uid = requireAuth(req);
  await db().runTransaction(async (tx) => (await consumeRate(tx, uid, "export", EXPORTS_PER_DAY, 24 * 60 * 60_000))());
  const userRef = users().doc(uid);
  const [profile, account] = await Promise.all([userRef.get(), accountRef(uid).get()]);
  // Phone IDs are plumbing, not the person's data (fcmTokens: from app versions before pushIds).
  const { pushIds: _ids, fcmTokens: _tokens, ...accountData } = account.data() ?? {};
  const myChats = await chats().where("participants", "array-contains", uid).get();
  const conversations = await Promise.all(
    myChats.docs.map(async (chat) => ({
      id: chat.id,
      with: (chat.get("participants") as string[]).filter((p) => p !== uid),
      messages: await docs(chat.ref.collection("messages").orderBy("createdAt")),
    })),
  );
  const data = {
    exportedAt: new Date().toISOString(),
    profile: plain(profile.data() ?? {}),
    account: plain(accountData),
    limits: await docs(userRef.collection("limits")),
    extraTime: await docs(userRef.collection("grants")),
    sharedUsage: await docs(userRef.collection("usage")),
    emergencyUnlocks: await docs(userRef.collection("emergencies")),
    requestsYouMade: await docs(requests().where("requesterId", "==", uid)),
    requestsYouWereAsked: await docs(userRef.collection("inbox")),
    invitesYouSent: await docs(invites().where("fromUid", "==", uid)),
    invitesYouGot: await docs(invites().where("toUid", "==", uid)),
    conversations,
  };
  const path = exportPath(uid, Date.now());
  const json = JSON.stringify(data, null, 2);
  await getStorage().bucket().file(path).save(json, { contentType: "application/json", resumable: false });
  return { path, bytes: Buffer.byteLength(json) };
});

/**
 * Deletes the caller's account and everything tied to it. The people directly affected (their
 * Bumblebees, and the friends they looked after) are told; no one else is.
 */
export const deleteAccount = onCall({ timeoutSeconds: 300 }, async (req) => {
  const uid = requireAuth(req);
  const me = await loadPerson(uid).catch(() => null);
  const userRef = users().doc(uid);

  // Links first, so nobody can join up with this account while it's being taken apart.
  const cleanup = db().bulkWriter();
  cleanup.onWriteError((error) => {
    // A document someone else removed meanwhile is already as clean as it gets.
    if (error.code === 5 /* NOT_FOUND */) return false;
    return error.failedAttempts < 3;
  });
  for (const query of [
    inviteLinks().where("ownerUid", "==", uid),
    inviteLinks().where("usedBy", "==", uid),
    removals().where("ownerUid", "==", uid),
    removals().where("beeUid", "==", uid),
  ]) {
    (await query.get()).docs.forEach((d) => void cleanup.delete(d.ref).catch(() => undefined));
  }
  await cleanup.flush();

  if (me) {
    for (const beeUid of me.account.approverIds) {
      void cleanup.update(accountRef(beeUid), { approveeIds: FieldValue.arrayRemove(uid) }).catch(() => undefined);
    }
    for (const ownerUid of me.account.approveeIds) {
      void cleanup.update(accountRef(ownerUid), {
        approverIds: FieldValue.arrayRemove(uid),
        [`pendingRemovals.${uid}`]: FieldValue.delete(),
      }).catch(() => undefined);
    }
    await cleanup.flush();
    await notify(me.account.approverIds, { type: "approver_left", ...pushes.gardenClosed(me.profile) });
    await notify(me.account.approveeIds, { type: "approver_left", ...pushes.bumblebeeLeftMeadow(me.profile) });
  }

  const pendingAsBee = await requests().where("approverIds", "array-contains", uid).get();
  pendingAsBee.docs.forEach((d) => void cleanup.update(d.ref, { approverIds: FieldValue.arrayRemove(uid) }).catch(() => undefined));
  // Their own requests, and every Bumblebee's copy of them.
  for (const d of (await requests().where("requesterId", "==", uid).get()).docs) {
    for (const beeUid of (d.get("approverIds") as string[] | undefined) ?? []) {
      void cleanup.delete(inboxRef(beeUid, d.id)).catch(() => undefined);
    }
    void cleanup.delete(d.ref).catch(() => undefined);
  }
  for (const query of [invites().where("fromUid", "==", uid), invites().where("toUid", "==", uid)]) {
    (await query.get()).docs.forEach((d) => void cleanup.delete(d.ref).catch(() => undefined));
  }
  // The friend code lives on the account now, or on the profile for one not yet migrated.
  const code = (await accountRef(uid).get()).get("friendCode") ?? (await userRef.get()).get("friendCode");
  if (typeof code === "string" && code) void cleanup.delete(friendCodes().doc(code)).catch(() => undefined);
  void cleanup.delete(heartbeats().doc(uid)).catch(() => undefined);
  void cleanup.delete(rateLimits().doc(uid)).catch(() => undefined);
  await cleanup.close();

  for (const chat of (await chats().where("participants", "array-contains", uid).get()).docs) {
    await db().recursiveDelete(chat.ref);
  }
  await db().recursiveDelete(userRef);
  await getStorage().bucket().file(`avatars/${uid}`).delete({ ignoreNotFound: true })
    .catch((e) => logger.warn("Avatar delete failed", { uid, error: String(e) }));
  await getStorage().bucket().deleteFiles({ prefix: `exports/${uid}/` })
    .catch((e) => logger.warn("Export delete failed", { uid, error: String(e) }));
  await getAuth().deleteUser(uid).catch((e) => logger.warn("Auth delete failed", { uid, error: String(e) }));
  return { ok: true };
});
