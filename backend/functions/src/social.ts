import { FieldValue, Timestamp, Transaction } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { chatNotes, errors, pushes } from "./copy";
import {
  db, ensureChat, friendCodes, inviteLinks, invites, notify, parse, postToChat, projectId, requireAuth,
  requireEmailAccount, userOf, users, UserDoc,
} from "./db";
import { chatIdFor, inviteUrl, randomFriendCode, randomInviteToken } from "./format";
import {
  InviteByCodeInput, InviteIdInput, InviteTokenInput, ProfileInput, RemoveBumblebeeInput, RespondInviteInput,
  StepDownInput,
} from "./schemas";

const INVITE_LINK_TTL_MS = 7 * 24 * 60 * 60_000;

/** Creates the caller's profile and claims a unique friend code. Safe to call twice. */
export const createProfile = onCall(async (req) => {
  const uid = requireAuth(req);
  const { displayName, flower } = parse(ProfileInput, req.data);
  const userRef = users().doc(uid);

  await db().runTransaction(async (tx) => {
    const existing = await tx.get(userRef);
    if (existing.exists) {
      tx.update(userRef, { displayName, flower });
      return;
    }
    let code: string | null = null;
    for (let attempt = 0; attempt < 8 && !code; attempt++) {
      const candidate = randomFriendCode();
      if (!(await tx.get(friendCodes().doc(candidate))).exists) code = candidate;
    }
    if (!code) throw new HttpsError("unavailable", errors.noFriendCode);
    tx.set(friendCodes().doc(code), { uid });
    tx.set(userRef, {
      displayName,
      flower,
      friendCode: code,
      approverIds: [],
      approveeIds: [],
      strictMode: false,
      fcmTokens: [],
      protection: {},
      createdAt: FieldValue.serverTimestamp(),
    });
  });
  return { ok: true };
});

/** Makes `bee` a Bumblebee of `owner`. Runs inside a transaction; returns the chat id. */
function joinUp(tx: Transaction, owner: { uid: string; user: UserDoc }, bee: { uid: string; user: UserDoc }): string {
  tx.update(users().doc(owner.uid), { approverIds: FieldValue.arrayUnion(bee.uid) });
  tx.update(users().doc(bee.uid), { approveeIds: FieldValue.arrayUnion(owner.uid) });
  const chatId = chatIdFor(owner.uid, bee.uid);
  ensureChat(tx, owner, bee, chatId);
  return chatId;
}

async function announceNewBumblebee(chatId: string, owner: { uid: string; user: UserDoc }, bee: { uid: string; user: UserDoc }) {
  await postToChat(chatId, { senderId: bee.uid, kind: "system", text: chatNotes.newBumblebee(bee.user, owner.user) });
  await notify([owner.uid], { type: "invite_accepted", ...pushes.inviteAccepted(bee.user) });
}

/**
 * Creates a one-time invite link: whoever opens it and says yes becomes the caller's Bumblebee.
 * Links last a week.
 */
export const createInviteLink = onCall(async (req) => {
  const uid = requireAuth(req);
  requireEmailAccount(req);
  const me = userOf(await users().doc(uid).get());
  const token = randomInviteToken();
  const expiresAt = Date.now() + INVITE_LINK_TTL_MS;
  await inviteLinks().doc(token).set({
    ownerUid: uid,
    ownerName: me.displayName,
    ownerFlower: me.flower,
    createdAt: FieldValue.serverTimestamp(),
    expiresAt: Timestamp.fromMillis(expiresAt),
    usedBy: null,
  });
  return { token, url: inviteUrl(projectId(), token), expiresAt };
});

type LinkStatus = "ok" | "expired" | "used" | "own" | "already";

/** Tells the person who opened a link who sent it and whether they can still accept it. */
export const previewInviteLink = onCall(async (req) => {
  const uid = requireAuth(req);
  const { token } = parse(InviteTokenInput, req.data);
  const link = await inviteLinks().doc(token).get();
  if (!link.exists) throw new HttpsError("not-found", errors.linkGone);
  const ownerUid = link.get("ownerUid") as string;
  const owner = await users().doc(ownerUid).get();
  const status: LinkStatus =
    ownerUid === uid ? "own"
      : link.get("usedBy") ? "used"
        : (link.get("expiresAt") as Timestamp).toMillis() < Date.now() ? "expired"
          : userOf(owner).approverIds.includes(uid) ? "already"
            : "ok";
  return { ownerName: link.get("ownerName"), ownerFlower: link.get("ownerFlower"), status };
});

/** The caller becomes the Bumblebee of whoever created the link. */
export const acceptInviteLink = onCall(async (req) => {
  const uid = requireAuth(req);
  const { token } = parse(InviteTokenInput, req.data);
  const linkRef = inviteLinks().doc(token);

  const result = await db().runTransaction(async (tx) => {
    const link = await tx.get(linkRef);
    if (!link.exists) throw new HttpsError("not-found", errors.linkGone);
    const ownerUid = link.get("ownerUid") as string;
    if (ownerUid === uid) throw new HttpsError("failed-precondition", errors.ownLink);
    if (link.get("usedBy")) throw new HttpsError("failed-precondition", errors.linkUsed);
    if ((link.get("expiresAt") as Timestamp).toMillis() < Date.now()) throw new HttpsError("failed-precondition", errors.linkExpired);
    const [ownerSnap, meSnap] = await Promise.all([tx.get(users().doc(ownerUid)), tx.get(users().doc(uid))]);
    const owner = { uid: ownerUid, user: userOf(ownerSnap) };
    const bee = { uid, user: userOf(meSnap) };
    if (owner.user.approverIds.includes(uid)) throw new HttpsError("already-exists", errors.alreadyTheirBumblebee(owner.user.displayName));
    tx.update(linkRef, { usedBy: uid, usedAt: FieldValue.serverTimestamp() });
    return { chatId: joinUp(tx, owner, bee), owner, bee };
  });

  await announceNewBumblebee(result.chatId, result.owner, result.bee);
  return { ownerName: result.owner.user.displayName };
});

/** Asks the person with this friend code to be the caller's Bumblebee. */
export const sendInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  requireEmailAccount(req);
  const { friendCode } = parse(InviteByCodeInput, req.data);

  const codeSnap = await friendCodes().doc(friendCode).get();
  if (!codeSnap.exists) throw new HttpsError("not-found", errors.codeUnknown);
  const targetUid = codeSnap.get("uid") as string;
  if (targetUid === uid) throw new HttpsError("invalid-argument", errors.ownCode);

  const [meSnap, targetSnap] = await Promise.all([users().doc(uid).get(), users().doc(targetUid).get()]);
  const me = userOf(meSnap);
  const target = userOf(targetSnap);
  if (me.approverIds.includes(targetUid)) throw new HttpsError("already-exists", errors.alreadyBumblebee(target.displayName));
  const pending = await invites()
    .where("fromUid", "==", uid).where("toUid", "==", targetUid).where("status", "==", "pending").limit(1).get();
  if (!pending.empty) throw new HttpsError("already-exists", errors.alreadyInvited(target.displayName));

  const ref = invites().doc();
  await ref.set({
    fromUid: uid,
    fromName: me.displayName,
    fromFlower: me.flower,
    toUid: targetUid,
    toName: target.displayName,
    status: "pending",
    createdAt: FieldValue.serverTimestamp(),
  });
  await notify([targetUid], { type: "invite", inviteId: ref.id, ...pushes.invite(me) });
  return { ok: true };
});

export const respondInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  const { inviteId, accept } = parse(RespondInviteInput, req.data);
  const inviteRef = invites().doc(inviteId);

  const result = await db().runTransaction(async (tx) => {
    const invite = await tx.get(inviteRef);
    if (!invite.exists || invite.get("toUid") !== uid) throw new HttpsError("not-found", errors.inviteGone);
    if (invite.get("status") !== "pending") throw new HttpsError("failed-precondition", errors.inviteAnswered);
    const ownerUid = invite.get("fromUid") as string;
    const [ownerSnap, meSnap] = await Promise.all([tx.get(users().doc(ownerUid)), tx.get(users().doc(uid))]);
    tx.update(inviteRef, { status: accept ? "accepted" : "declined", answeredAt: FieldValue.serverTimestamp() });
    if (!accept) return null;
    const owner = { uid: ownerUid, user: userOf(ownerSnap) };
    const bee = { uid, user: userOf(meSnap) };
    return { chatId: joinUp(tx, owner, bee), owner, bee };
  });

  if (result) await announceNewBumblebee(result.chatId, result.owner, result.bee);
  return { ok: true };
});

export const cancelInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  const { inviteId } = parse(InviteIdInput, req.data);
  const inviteRef = invites().doc(inviteId);
  const invite = await inviteRef.get();
  if (!invite.exists || invite.get("fromUid") !== uid) throw new HttpsError("not-found", errors.inviteGone);
  if (invite.get("status") === "pending") await inviteRef.update({ status: "cancelled" });
  return { ok: true };
});

/** Ends a Bumblebee relationship and tells everyone involved, so nobody is dropped quietly. */
async function partWays(ownerUid: string, beeUid: string, endedBy: "owner" | "bee"): Promise<void> {
  const [ownerSnap, beeSnap] = await Promise.all([users().doc(ownerUid).get(), users().doc(beeUid).get()]);
  const owner = userOf(ownerSnap);
  const bee = userOf(beeSnap);
  if (!owner.approverIds.includes(beeUid)) throw new HttpsError("failed-precondition", errors.notBumblebees);

  const batch = db().batch();
  batch.update(users().doc(ownerUid), { approverIds: FieldValue.arrayRemove(beeUid) });
  batch.update(users().doc(beeUid), { approveeIds: FieldValue.arrayRemove(ownerUid) });
  await batch.commit();

  const remaining = owner.approverIds.filter((id) => id !== beeUid);
  const chatId = chatIdFor(ownerUid, beeUid);
  if (endedBy === "owner") {
    await postToChat(chatId, { senderId: ownerUid, kind: "system", text: chatNotes.removedBumblebee(owner, bee) });
    await notify([beeUid, ...remaining], { type: "approver_left", uid: ownerUid, ...pushes.removedBumblebee(owner, bee, remaining.length) });
  } else {
    await postToChat(chatId, { senderId: beeUid, kind: "system", text: chatNotes.steppedDown(bee, owner) });
    await notify([ownerUid], { type: "approver_left", ...pushes.steppedDown(bee, remaining.length) });
  }
}

/** The garden's owner removes one of their Bumblebees. No one else needs to agree. */
export const removeApprover = onCall(async (req) => {
  const uid = requireAuth(req);
  const { approverUid } = parse(RemoveBumblebeeInput, req.data);
  await partWays(uid, approverUid, "owner");
  return { ok: true };
});

/** A Bumblebee stops looking after someone's garden. */
export const stepDown = onCall(async (req) => {
  const uid = requireAuth(req);
  const { approveeUid } = parse(StepDownInput, req.data);
  await partWays(approveeUid, uid, "bee");
  return { ok: true };
});
