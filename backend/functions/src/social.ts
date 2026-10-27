import { randomUUID } from "node:crypto";
import { FieldValue, Timestamp, type Transaction } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { chatNotes, errors, pushes } from "./copy";
import {
  accountOf, accountRef, chats, consumeRate, db, ensureChat, friendCodes, heartbeats, inviteLinks, invites, loadPerson, notify, parse, type Person,
  postToChat, projectId, removals, requireAuth, users,
} from "./db";
import { chatIdFor, inviteUrl, randomFriendCode, randomInviteToken, withPhotoToken } from "./format";
import {
  CancelRemovalInput, InviteByCodeInput, InviteFromChatInput, InviteIdInput, InviteTokenInput, ProfileInput, RemoveBumblebeeInput,
  RespondInviteInput, StepDownInput,
} from "./schemas";

const INVITE_LINK_TTL_MS = 7 * 24 * 60 * 60_000;
/**
 * What a new garden shares with its Bumblebees, each part a choice of its own (the app's Privacy).
 * Progress sums up earlier days, so it waits to be chosen.
 */
export const DEFAULT_PRIVACY = { shareLimits: true, shareTime: true, shareApps: true, shareProgress: false } as const;
/** Removing a Bumblebee waits a day, so a weak moment can't undo every commitment at once. */
export const REMOVAL_DELAY_MS = 24 * 60 * 60_000;
const DAY_MS = 24 * 60 * 60_000;
/** Invites by code and invite links together. */
const INVITES_PER_DAY = 20;
const REMOVALS_PER_DAY = 10;
/** Friend codes looked up, found or not, so codes can't be guessed by trying them all. */
export const CODE_LOOKUPS_PER_DAY = 30;
/**
 * Unanswered invites one person can have waiting. Rate limits count per account, and guest
 * accounts cost nothing to make, so this is what keeps anyone from being flooded with them.
 */
export const PENDING_INVITES_PER_PERSON = 10;

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
      privacy: { ...DEFAULT_PRIVACY },
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

/** Notes the new Bumblebee in their chat, and tells whoever asked ([asker]) that it worked. */
async function announceNewBumblebee(chatId: string, owner: Person, bee: Person, asker: "owner" | "bee" = "owner") {
  await postToChat(chatId, { senderId: bee.uid, kind: "system", text: chatNotes.newBumblebee(bee.profile, owner.profile) });
  if (asker === "owner") await notify([owner.uid], { type: "invite_accepted", ...pushes.inviteAccepted(bee.profile) });
  else await notify([bee.uid], { type: "invite_accepted", ...pushes.offerAccepted(owner.profile) });
}

/**
 * Creates a one-time invite link: whoever opens it and says yes becomes the caller's Bumblebee.
 * Links last a week.
 */
export const createInviteLink = onCall(async (req) => {
  const uid = requireAuth(req);
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
  const { friendCode } = parse(InviteByCodeInput, req.data);

  // Counted before looking, and kept even when no one has the code.
  const codeSnap = await db().runTransaction(async (tx) => {
    const commitRate = await consumeRate(tx, uid, "codeLookup", CODE_LOOKUPS_PER_DAY, DAY_MS, errors.tooManyCodes);
    const snap = await tx.get(friendCodes().doc(friendCode));
    commitRate();
    return snap;
  });
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
    const waiting = await tx.get(invites().where("toUid", "==", targetUid).where("status", "==", "pending").limit(PENDING_INVITES_PER_PERSON));
    if (waiting.docs.some((d) => d.get("fromUid") === uid)) throw new HttpsError("already-exists", errors.alreadyInvited(target.profile.displayName));
    if (waiting.size >= PENDING_INVITES_PER_PERSON) throw new HttpsError("resource-exhausted", errors.inviteeBusy(target.profile.displayName));
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

/**
 * From a chat: asks the other person to be the caller's Bumblebee, or ([offer]) offers to be
 * theirs. Only between two people who already share an open chat, so it reaches nobody a friend
 * code or link hasn't reached first.
 */
export const inviteFromChat = onCall(async (req) => {
  const uid = requireAuth(req);
  const { uid: otherUid, offer } = parse(InviteFromChatInput, req.data);
  if (otherUid === uid) throw new HttpsError("invalid-argument", errors.noSharedChat);
  const chat = await chats().doc(chatIdFor(uid, otherUid)).get();
  if (!chat.exists || chat.get("closed") === true) throw new HttpsError("failed-precondition", errors.noSharedChat);

  const ref = invites().doc();
  const me = await db().runTransaction(async (tx) => {
    const [me, other] = await Promise.all([loadPerson(uid, tx), loadPerson(otherUid, tx)]);
    if (offer && other.account.approverIds.includes(uid)) {
      throw new HttpsError("already-exists", errors.alreadyTheirBumblebee(other.profile.displayName));
    }
    if (!offer && me.account.approverIds.includes(otherUid)) {
      throw new HttpsError("already-exists", errors.alreadyBumblebee(other.profile.displayName));
    }
    const commitRate = await consumeRate(tx, uid, "invite", INVITES_PER_DAY, DAY_MS);
    const waiting = await tx.get(invites().where("toUid", "==", otherUid).where("status", "==", "pending").limit(PENDING_INVITES_PER_PERSON));
    if (waiting.docs.some((d) => d.get("fromUid") === uid)) throw new HttpsError("already-exists", errors.alreadyInvited(other.profile.displayName));
    if (waiting.size >= PENDING_INVITES_PER_PERSON) throw new HttpsError("resource-exhausted", errors.inviteeBusy(other.profile.displayName));
    commitRate();
    tx.set(ref, {
      fromUid: uid,
      fromName: me.profile.displayName,
      fromFlower: me.profile.flower,
      toUid: otherUid,
      toName: other.profile.displayName,
      status: "pending",
      createdAt: FieldValue.serverTimestamp(),
      // An offer makes whoever sent it the Bumblebee; a plain invite, whoever accepts it.
      offer,
    });
    return me;
  });
  await notify([otherUid], { type: "invite", inviteId: ref.id, ...(offer ? pushes.offer(me.profile) : pushes.invite(me.profile)) });
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
    const offer = invite.get("offer") === true;
    const [sender, me] = await Promise.all([loadPerson(invite.get("fromUid") as string, tx), loadPerson(uid, tx)]);
    const [owner, bee] = offer ? [me, sender] : [sender, me];
    tx.update(inviteRef, { status: accept ? "accepted" : "declined", answeredAt: FieldValue.serverTimestamp() });
    if (!accept) return null;
    return { chatId: joinUp(tx, owner, bee), owner, bee, asker: offer ? "bee" as const : "owner" as const };
  });

  if (result) await announceNewBumblebee(result.chatId, result.owner, result.bee, result.asker);
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
 *
 * Unless they still look after each other the other way round, their chat closes: neither can
 * write in it any more, and it no longer carries their names and pictures, nor gets new ones.
 * Each person's photo gets a new link too, so the one the other kept stops working.
 */
export async function partWays(ownerUid: string, beeUid: string, endedBy: "owner" | "bee"): Promise<void> {
  // Someone whose account was only partly deleted can still be let go of.
  const evenIfGone = { orMissing: true };
  const [owner, bee] = await Promise.all([loadPerson(ownerUid, undefined, evenIfGone), loadPerson(beeUid, undefined, evenIfGone)]);
  if (!owner.account.approverIds.includes(beeUid)) throw new HttpsError("failed-precondition", errors.notBumblebees);
  const parting = !owner.account.approveeIds.includes(beeUid);
  const chatId = chatIdFor(ownerUid, beeUid);
  const chatRef = chats().doc(chatId);

  const batch = db().batch();
  batch.update(accountRef(ownerUid), {
    approverIds: FieldValue.arrayRemove(beeUid),
    [`pendingRemovals.${beeUid}`]: FieldValue.delete(),
  });
  if ((await accountRef(beeUid).get()).exists) batch.update(accountRef(beeUid), { approveeIds: FieldValue.arrayRemove(ownerUid) });
  batch.delete(removals().doc(removalId(ownerUid, beeUid)));
  if (parting && (await chatRef.get()).exists) {
    batch.update(chatRef, { closed: true, names: FieldValue.delete(), avatars: FieldValue.delete() });
  }
  await batch.commit();

  if (parting) await Promise.all([renewPhotoLink(ownerUid), renewPhotoLink(beeUid)]);
  if (endedBy === "owner") {
    await postToChat(chatId, { senderId: ownerUid, kind: "system", text: chatNotes.removedBumblebee(owner.profile, bee.profile) }, { evenIfClosed: true });
    await notify([beeUid], { type: "approver_left", uid: ownerUid, ...pushes.removedBumblebee(owner.profile) });
  } else {
    const remaining = owner.account.approverIds.filter((id) => id !== beeUid).length;
    await postToChat(chatId, { senderId: beeUid, kind: "system", text: chatNotes.steppedDown(bee.profile, owner.profile) }, { evenIfClosed: true });
    await notify([ownerUid], { type: "approver_left", ...pushes.steppedDown(bee.profile, remaining) });
  }
}

/**
 * A photo link carries a download token that works for whoever holds it, whatever the Storage
 * rules say. Gives the photo a new token and puts the new link on the profile, from where it
 * reaches the person's open chats. Never throws: the relationship has already ended.
 */
async function renewPhotoLink(uid: string): Promise<void> {
  try {
    const file = getStorage().bucket().file(`avatars/${uid}`);
    if (!(await file.exists())[0]) return;
    const token = randomUUID();
    await file.setMetadata({ metadata: { firebaseStorageDownloadTokens: token } });
    const profileRef = users().doc(uid);
    await db().runTransaction(async (tx) => {
      const url = (await tx.get(profileRef)).get("photoUrl");
      if (typeof url !== "string") return;
      const renewed = withPhotoToken(url, token);
      if (renewed !== url) tx.update(profileRef, { photoUrl: renewed });
    });
  } catch (e) {
    logger.warn("Photo link renewal failed", { uid, error: String(e) });
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
    const [owner, bee] = await Promise.all([loadPerson(uid, tx), loadPerson(approverUid, tx, { orMissing: true })]);
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
    const [owner, bee] = await Promise.all([loadPerson(uid, tx), loadPerson(approverUid, tx, { orMissing: true })]);
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

/** Whether [partWays] failed because the two had already parted, rather than for some other reason. */
export const alreadyParted = (e: unknown): boolean => e instanceof HttpsError && e.code === "failed-precondition";

/** Ends the removals whose day has passed. Called by the hourly housekeeping. */
export async function finishDueRemovals(now: number): Promise<number> {
  const due = await removals().where("effectiveAt", "<=", Timestamp.fromMillis(now)).limit(200).get();
  for (const doc of due.docs) {
    const ownerUid = doc.get("ownerUid") as string;
    const beeUid = doc.get("beeUid") as string;
    try {
      await partWays(ownerUid, beeUid, "owner");
    } catch (e) {
      if (!alreadyParted(e)) {
        // Most likely a passing problem: the next run tries again, rather than drop the removal.
        logger.warn("Removal not finished yet", { ownerUid, beeUid, error: String(e) });
        continue;
      }
      // Already parted another way (stepped down, account deleted): just tidy up.
      await doc.ref.delete();
      await accountRef(ownerUid).update({ [`pendingRemovals.${beeUid}`]: FieldValue.delete() }).catch(() => undefined);
    }
  }
  return due.size;
}

/**
 * A Bumblebee stops looking after someone's garden. If that person's account is gone (its
 * deletion only got part of the way), they're just taken off the Bumblebee's own list.
 */
export const stepDown = onCall(async (req) => {
  const uid = requireAuth(req);
  const { approveeUid } = parse(StepDownInput, req.data);
  const ownerGone = await db().runTransaction(async (tx) => {
    const [ownerAccount, mine] = await Promise.all([tx.get(accountRef(approveeUid)), tx.get(accountRef(uid))]);
    if (ownerAccount.exists) return false;
    if (!accountOf(mine).approveeIds.includes(approveeUid)) throw new HttpsError("failed-precondition", errors.notBumblebees);
    tx.update(accountRef(uid), { approveeIds: FieldValue.arrayRemove(approveeUid) });
    return true;
  });
  if (!ownerGone) await partWays(approveeUid, uid, "bee");
  return { ok: true };
});
