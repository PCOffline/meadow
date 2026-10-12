/** Data rights: download everything Meadow holds about you, or delete it for good. */
import { getAuth } from "firebase-admin/auth";
import { DocumentData, FieldValue, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { pushes } from "./copy";
import { accountRef, chats, db, friendCodes, inviteLinks, invites, loadPerson, notify, requests, requireAuth, users } from "./db";

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

/** Everything Meadow stores about the caller, as one JSON document. */
export const exportMyData = onCall({ timeoutSeconds: 120 }, async (req) => {
  const uid = requireAuth(req);
  const userRef = users().doc(uid);
  const [profile, account] = await Promise.all([userRef.get(), accountRef(uid).get()]);
  const { fcmTokens: _tokens, ...accountData } = account.data() ?? {};
  const myChats = await chats().where("participants", "array-contains", uid).get();
  const conversations = await Promise.all(
    myChats.docs.map(async (chat) => ({
      id: chat.id,
      with: (chat.get("participants") as string[]).filter((p) => p !== uid),
      messages: await docs(chat.ref.collection("messages").orderBy("createdAt")),
    })),
  );
  return {
    exportedAt: new Date().toISOString(),
    profile: plain(profile.data() ?? {}),
    account: plain(accountData),
    limits: await docs(userRef.collection("limits")),
    extraTime: await docs(userRef.collection("grants")),
    sharedUsage: await docs(userRef.collection("usage")),
    emergencyUnlocks: await docs(userRef.collection("emergencies")),
    requestsYouMade: await docs(requests().where("requesterId", "==", uid)),
    invitesYouSent: await docs(invites().where("fromUid", "==", uid)),
    invitesYouGot: await docs(invites().where("toUid", "==", uid)),
    conversations,
  };
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
  for (const query of [inviteLinks().where("ownerUid", "==", uid), inviteLinks().where("usedBy", "==", uid)]) {
    (await query.get()).docs.forEach((d) => void cleanup.delete(d.ref).catch(() => undefined));
  }
  await cleanup.flush();

  if (me) {
    for (const beeUid of me.account.approverIds) {
      void cleanup.update(accountRef(beeUid), { approveeIds: FieldValue.arrayRemove(uid) }).catch(() => undefined);
    }
    for (const ownerUid of me.account.approveeIds) {
      void cleanup.update(accountRef(ownerUid), { approverIds: FieldValue.arrayRemove(uid) }).catch(() => undefined);
    }
    await cleanup.flush();
    await notify(me.account.approverIds, { type: "approver_left", ...pushes.gardenClosed(me.profile) });
    await notify(me.account.approveeIds, { type: "approver_left", ...pushes.bumblebeeLeftMeadow(me.profile) });
  }

  const pendingAsBee = await requests().where("approverIds", "array-contains", uid).get();
  pendingAsBee.docs.forEach((d) => void cleanup.update(d.ref, { approverIds: FieldValue.arrayRemove(uid) }).catch(() => undefined));
  for (const query of [
    requests().where("requesterId", "==", uid),
    invites().where("fromUid", "==", uid),
    invites().where("toUid", "==", uid),
  ]) {
    (await query.get()).docs.forEach((d) => void cleanup.delete(d.ref).catch(() => undefined));
  }
  const code = (await userRef.get()).get("friendCode");
  if (typeof code === "string" && code) void cleanup.delete(friendCodes().doc(code)).catch(() => undefined);
  await cleanup.close();

  for (const chat of (await chats().where("participants", "array-contains", uid).get()).docs) {
    await db().recursiveDelete(chat.ref);
  }
  await db().recursiveDelete(userRef);
  await getStorage().bucket().file(`avatars/${uid}`).delete({ ignoreNotFound: true })
    .catch((e) => logger.warn("Avatar delete failed", { uid, error: String(e) }));
  await getAuth().deleteUser(uid).catch((e) => logger.warn("Auth delete failed", { uid, error: String(e) }));
  return { ok: true };
});
