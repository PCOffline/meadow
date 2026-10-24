import { FieldValue, Timestamp, type Transaction } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { chatNotes, errors, pushes } from "./copy";
import {
  accountRef, consumeRate, db, ensureChat, friendCodes, heartbeats, inviteLinks, invites, loadPerson, notify, parse, type Person,
  postToChat, projectId, removals, requireAuth, requireEmailAccount, users,
} from "./db";
import { chatIdFor, inviteUrl, randomFriendCode, randomInviteToken } from "./format";
import {
  CancelRemovalInput, InviteByCodeInput, InviteIdInput, InviteTokenInput, ProfileInput, RemoveBumblebeeInput,
  RespondInviteInput, StepDownInput,
} from "./schemas";

const INVITE_LINK_TTL_MS = 7 * 24 * 60 * 60_000;
/** Removing a Bumblebee waits a day, so a weak moment can't undo every commitment at once. */
export const REMOVAL_DELAY_MS = 24 * 60 * 60_000;
const DAY_MS = 24 * 60 * 60_000;
/** Invites by code and invite links together. */
const INVITES_PER_DAY = 20;
const REMOVALS_PER_DAY = 10;

/** Creates the caller's profile and claims a unique friend code. Safe to call twice. */
export const createProfile = onCall(async (req) => {
  const uid = requireAuth(req);
  const { displayName, flower, termsVersion } = parse(ProfileInput, req.data);
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
    // The public profile is only a name card; everything else is owner-only.
    tx.set(userRef, {
      displayName,
      flower,
      avatarColor: "leaf",
      photoUrl: null,
      bio: "",
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.set(accountRef(uid), {
      approverIds: [],
      approveeIds: [],
      pushIds: [],
      friendCode: code,
      strictMode: false,
      privacy: { shareUsage: true },
      pendingRemovals: {},
      ...(termsVersion ? { acceptedTerms: { version: termsVersion, at: FieldValue.serverTimestamp() } } : {}),
    });
    tx.set(heartbeats().doc(uid), { at: FieldValue.serverTimestamp(), staleNotified: false });
  });
  return { ok: true };
});

/** Makes `bee` a Bumblebee of `owner`. Runs inside a transaction; returns the chat id. */
function joinUp(tx: Transaction, owner: Person, bee: Person): string {
  tx.set(accountRef(owner.uid), { approverIds: FieldValue.arrayUnion(bee.uid) }, { merge: true });
  tx.set(accountRef(bee.uid), { approveeIds: FieldValue.arrayUnion(owner.uid) }, { merge: true });
  const chatId = chatIdFor(owner.uid, bee.uid);
  ensureChat(tx, owner, bee, chatId);
  return chatId;
}

async function announceNewBumblebee(chatId: string, owner: Person, bee: Person) {
  await postToChat(chatId, { senderId: bee.uid, kind: "system", text: chatNotes.newBumblebee(bee.profile, owner.profile) });
  await notify([owner.uid], { type: "invite_accepted", ...pushes.inviteAccepted(bee.profile) });
}

/**
 * Creates a one-time invite link: whoever opens it and says yes becomes the caller's Bumblebee.
 * Links last a week.
 */
export const createInviteLink = onCall(async (req) => {
  const uid = requireAuth(req);
  requireEmailAccount(req);
  const token = randomInviteToken();
  const expiresAt = Date.now() + INVITE_LINK_TTL_MS;
  await db().runTransaction(async (tx) => {
    const me = await loadPerson(uid, tx);
    const commitRate = await consumeRate(tx, uid, "invite", INVITES_PER_DAY, DAY_MS);
    commitRate();
    tx.set(inviteLinks().doc(token), {
      ownerUid: uid,
      ownerName: me.profile.displayName,
      ownerFlower: me.profile.flower,
      createdAt: FieldValue.serverTimestamp(),
      expiresAt: Timestamp.fromMillis(expiresAt),
      usedBy: null,
    });
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
  const owner = await loadPerson(ownerUid);
  const status: LinkStatus =
    ownerUid === uid ? "own"
      : link.get("usedBy") ? "used"
        : (link.get("expiresAt") as Timestamp).toMillis() < Date.now() ? "expired"
          : owner.account.approverIds.includes(uid) ? "already"
            : "ok";
  return { ownerName: owner.profile.displayName, ownerFlower: owner.profile.flower, status };
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
    const [owner, bee] = await Promise.all([loadPerson(ownerUid, tx), loadPerson(uid, tx)]);
    if (owner.account.approverIds.includes(uid)) {
      throw new HttpsError("already-exists", errors.alreadyTheirBumblebee(owner.profile.displayName));
    }
    tx.update(linkRef, { usedBy: uid, usedAt: FieldValue.serverTimestamp() });
    return { chatId: joinUp(tx, owner, bee), owner, bee };
  });

  await announceNewBumblebee(result.chatId, result.owner, result.bee);
  return { ownerName: result.owner.profile.displayName };
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

  const ref = invites().doc();
  const me = await db().runTransaction(async (tx) => {
    const [me, target] = await Promise.all([loadPerson(uid, tx), loadPerson(targetUid, tx)]);
    if (me.account.approverIds.includes(targetUid)) {
      throw new HttpsError("already-exists", errors.alreadyBumblebee(target.profile.displayName));
    }
    const commitRate = await consumeRate(tx, uid, "invite", INVITES_PER_DAY, DAY_MS);
    // Checked in the same transaction as the rate counter, so a double tap invites once.
    const pending = await tx.get(invites()
      .where("fromUid", "==", uid).where("toUid", "==", targetUid).where("status", "==", "pending").limit(1));
    if (!pending.empty) throw new HttpsError("already-exists", errors.alreadyInvited(target.profile.displayName));
    commitRate();
    tx.set(ref, {
      fromUid: uid,
      fromName: me.profile.displayName,
      fromFlower: me.profile.flower,
      toUid: targetUid,
      toName: target.profile.displayName,
      status: "pending",
      createdAt: FieldValue.serverTimestamp(),
    });
    return me;
  });
  await notify([targetUid], { type: "invite", inviteId: ref.id, ...pushes.invite(me.profile) });
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
    const [owner, bee] = await Promise.all([loadPerson(invite.get("fromUid") as string, tx), loadPerson(uid, tx)]);
    tx.update(inviteRef, { status: accept ? "accepted" : "declined", answeredAt: FieldValue.serverTimestamp() });
    if (!accept) return null;
    return { chatId: joinUp(tx, owner, bee), owner, bee };
  });

  if (result) await announceNewBumblebee(result.chatId, result.owner, result.bee);
  return { ok: true };
});

export const cancelInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  const { inviteId } = parse(InviteIdInput, req.data);
  const inviteRef = invites().doc(inviteId);
  // In a transaction, so an invite accepted a moment ago isn't marked cancelled.
  await db().runTransaction(async (tx) => {
    const invite = await tx.get(inviteRef);
    if (!invite.exists || invite.get("fromUid") !== uid) throw new HttpsError("not-found", errors.inviteGone);
    if (invite.get("status") === "pending") tx.update(inviteRef, { status: "cancelled" });
  });
  return { ok: true };
});

/**
 * Ends a Bumblebee relationship. Only the person on the other side is told: who looks after
 * whom is nobody else's business.
 */
export async function partWays(ownerUid: string, beeUid: string, endedBy: "owner" | "bee"): Promise<void> {
  const [owner, bee] = await Promise.all([loadPerson(ownerUid), loadPerson(beeUid)]);
  if (!owner.account.approverIds.includes(beeUid)) throw new HttpsError("failed-precondition", errors.notBumblebees);

  const batch = db().batch();
  batch.update(accountRef(ownerUid), {
    approverIds: FieldValue.arrayRemove(beeUid),
    [`pendingRemovals.${beeUid}`]: FieldValue.delete(),
  });
  batch.update(accountRef(beeUid), { approveeIds: FieldValue.arrayRemove(ownerUid) });
  batch.delete(removals().doc(removalId(ownerUid, beeUid)));
  await batch.commit();

  const chatId = chatIdFor(ownerUid, beeUid);
  if (endedBy === "owner") {
    await postToChat(chatId, { senderId: ownerUid, kind: "system", text: chatNotes.removedBumblebee(owner.profile, bee.profile) });
    await notify([beeUid], { type: "approver_left", uid: ownerUid, ...pushes.removedBumblebee(owner.profile) });
  } else {
    const remaining = owner.account.approverIds.filter((id) => id !== beeUid).length;
    await postToChat(chatId, { senderId: beeUid, kind: "system", text: chatNotes.steppedDown(bee.profile, owner.profile) });
    await notify([ownerUid], { type: "approver_left", ...pushes.steppedDown(bee.profile, remaining) });
  }
}

export const removalId = (ownerUid: string, beeUid: string) => `${ownerUid}_${beeUid}`;

/**
 * The garden's owner removes one of their Bumblebees. It takes effect after a day, and only
 * that Bumblebee is told, straight away, so they can check in meanwhile. No one else needs to
 * agree or is told.
 */
export const removeApprover = onCall(async (req) => {
  const uid = requireAuth(req);
  const { approverUid } = parse(RemoveBumblebeeInput, req.data);
  const effectiveAt = Date.now() + REMOVAL_DELAY_MS;
  const { owner, bee } = await db().runTransaction(async (tx) => {
    const [owner, bee] = await Promise.all([loadPerson(uid, tx), loadPerson(approverUid, tx)]);
    if (!owner.account.approverIds.includes(approverUid)) throw new HttpsError("failed-precondition", errors.notBumblebees);
    const existing = await tx.get(removals().doc(removalId(uid, approverUid)));
    if (existing.exists) throw new HttpsError("already-exists", errors.removalPending);
    const commitRate = await consumeRate(tx, uid, "removal", REMOVALS_PER_DAY, DAY_MS);
    commitRate();
    tx.set(removals().doc(removalId(uid, approverUid)), {
      ownerUid: uid, beeUid: approverUid, effectiveAt: Timestamp.fromMillis(effectiveAt),
    });
    tx.update(accountRef(uid), { [`pendingRemovals.${approverUid}`]: Timestamp.fromMillis(effectiveAt) });
    return { owner, bee };
  });
  await postToChat(chatIdFor(uid, approverUid), { senderId: uid, kind: "system", text: chatNotes.removalScheduled(owner.profile, bee.profile) });
  await notify([approverUid], { type: "approver_leaving", uid, ...pushes.removalScheduled(owner.profile) });
  return { effectiveAt };
});

/** Keeps a Bumblebee whose removal is still waiting. */
export const cancelRemoval = onCall(async (req) => {
  const uid = requireAuth(req);
  const { approverUid } = parse(CancelRemovalInput, req.data);
  const { owner, bee } = await db().runTransaction(async (tx) => {
    const [owner, bee] = await Promise.all([loadPerson(uid, tx), loadPerson(approverUid, tx)]);
    const ref = removals().doc(removalId(uid, approverUid));
    if (!(await tx.get(ref)).exists) throw new HttpsError("failed-precondition", errors.noRemoval);
    tx.delete(ref);
    tx.update(accountRef(uid), { [`pendingRemovals.${approverUid}`]: FieldValue.delete() });
    return { owner, bee };
  });
  await postToChat(chatIdFor(uid, approverUid), { senderId: uid, kind: "system", text: chatNotes.removalCancelled(owner.profile, bee.profile) });
  await notify([approverUid], { type: "approver_kept", uid, ...pushes.removalCancelled(owner.profile) });
  return { ok: true };
});

/** Ends the removals whose day has passed. Called by the hourly housekeeping. */
export async function finishDueRemovals(now: number): Promise<number> {
  const due = await removals().where("effectiveAt", "<=", Timestamp.fromMillis(now)).limit(200).get();
  for (const doc of due.docs) {
    const ownerUid = doc.get("ownerUid") as string;
    const beeUid = doc.get("beeUid") as string;
    try {
      await partWays(ownerUid, beeUid, "owner");
    } catch {
      // Already parted another way (stepped down, account deleted): just tidy up.
      await doc.ref.delete();
      await accountRef(ownerUid).update({ [`pendingRemovals.${beeUid}`]: FieldValue.delete() }).catch(() => undefined);
    }
  }
  return due.size;
}

/** A Bumblebee stops looking after someone's garden. */
export const stepDown = onCall(async (req) => {
  const uid = requireAuth(req);
  const { approveeUid } = parse(StepDownInput, req.data);
  await partWays(approveeUid, uid, "bee");
  return { ok: true };
});
