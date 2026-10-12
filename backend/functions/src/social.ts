import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import {
  chatIdFor, createInvite, db, ensureChat, friendCodes, invites, notify, parse, postToChat, randomCode, requireAuth,
  userOf, users,
} from "./shared";

const FLOWERS = ["🌼", "🌸", "🌻", "🌷", "🌹", "🪻", "🌺", "🍀", "🌿", "🍄"] as const;
const id = z.string().trim().min(1).max(128);

const ProfileInput = z.object({
  displayName: z.string().trim().min(1, "Please choose a name.").max(30, "Names can be up to 30 characters."),
  flower: z.enum(FLOWERS).catch("🌼"),
});

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
      const candidate = randomCode();
      const taken = await tx.get(friendCodes().doc(candidate));
      if (!taken.exists) code = candidate;
    }
    if (!code) throw new HttpsError("unavailable", "Couldn't find a free friend code, please try again.");
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

const InviteInput = z.object({
  friendCode: z.string().trim().toUpperCase().min(4, "Enter your friend's code.").max(12),
});

/** Asks the person with this friend code to become the caller's Petal Pal. */
export const sendInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  // Commitments must survive a reinstall, so an account needs an email before it gets Petal Pals.
  if (req.auth?.token.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError("failed-precondition", "Add an email to your account first, so your garden can't be lost.");
  }
  const { friendCode } = parse(InviteInput, req.data);

  const codeSnap = await friendCodes().doc(friendCode).get();
  if (!codeSnap.exists) throw new HttpsError("not-found", "No one has that code. Double-check it?");
  const targetUid = codeSnap.get("uid") as string;
  if (targetUid === uid) throw new HttpsError("invalid-argument", "That's your own code 🌼");

  const [meSnap, targetSnap] = await Promise.all([users().doc(uid).get(), users().doc(targetUid).get()]);
  const me = userOf(meSnap);
  const target = userOf(targetSnap);
  if (me.approverIds.includes(targetUid)) {
    throw new HttpsError("already-exists", `${target.displayName} is already your Petal Pal.`);
  }
  const pending = await invites()
    .where("fromUid", "==", uid).where("toUid", "==", targetUid).where("status", "==", "pending").limit(1).get();
  if (!pending.empty) throw new HttpsError("already-exists", `You've already invited ${target.displayName}.`);

  await createInvite(uid, me, targetUid, target);
  return { status: "invited" };
});

const RespondInput = z.object({ inviteId: id, accept: z.boolean() });

export const respondInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  const { inviteId, accept } = parse(RespondInput, req.data);
  const inviteRef = invites().doc(inviteId);

  const result = await db().runTransaction(async (tx) => {
    const invite = await tx.get(inviteRef);
    if (!invite.exists || invite.get("toUid") !== uid) throw new HttpsError("not-found", "That invite is gone.");
    if (invite.get("status") !== "pending") throw new HttpsError("failed-precondition", "That invite was already answered.");
    const fromUid = invite.get("fromUid") as string;
    const [fromSnap, meSnap] = await Promise.all([tx.get(users().doc(fromUid)), tx.get(users().doc(uid))]);
    const from = userOf(fromSnap);
    const me = userOf(meSnap);
    tx.update(inviteRef, { status: accept ? "accepted" : "declined", answeredAt: FieldValue.serverTimestamp() });
    if (!accept) return null;
    tx.update(users().doc(fromUid), { approverIds: FieldValue.arrayUnion(uid) });
    tx.update(users().doc(uid), { approveeIds: FieldValue.arrayUnion(fromUid) });
    const chatId = ensureChat(tx, { uid: fromUid, user: from }, { uid, user: me });
    return { fromUid, from, me, chatId };
  });

  if (result) {
    await postToChat(result.chatId, {
      senderId: uid,
      kind: "system",
      text: `${result.me.flower} ${result.me.displayName} is now ${result.from.displayName}'s Petal Pal`,
    });
    await notify([result.fromUid], {
      type: "invite_accepted",
      title: `${result.me.flower} ${result.me.displayName} is your Petal Pal now`,
      body: "Your limits are locked in. Only your Petal Pals can give you more time.",
    });
  }
  return { ok: true };
});

const CancelInviteInput = z.object({ inviteId: id });

export const cancelInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  const { inviteId } = parse(CancelInviteInput, req.data);
  const inviteRef = invites().doc(inviteId);
  const invite = await inviteRef.get();
  if (!invite.exists || invite.get("fromUid") !== uid) throw new HttpsError("not-found", "That invite is gone.");
  if (invite.get("status") === "pending") await inviteRef.update({ status: "cancelled" });
  return { ok: true };
});

/** Ends a Petal Pal relationship between `owner` (whose garden it is) and `pal`, and tells everyone involved. */
async function endPartnership(ownerUid: string, palUid: string, endedBy: "owner" | "pal"): Promise<void> {
  const [ownerSnap, palSnap] = await Promise.all([users().doc(ownerUid).get(), users().doc(palUid).get()]);
  const owner = userOf(ownerSnap);
  const pal = userOf(palSnap);
  if (!owner.approverIds.includes(palUid)) throw new HttpsError("failed-precondition", "They're not Petal Pals.");

  const batch = db().batch();
  batch.update(users().doc(ownerUid), { approverIds: FieldValue.arrayRemove(palUid) });
  batch.update(users().doc(palUid), { approveeIds: FieldValue.arrayRemove(ownerUid) });
  await batch.commit();

  const remaining = owner.approverIds.filter((p) => p !== palUid);
  await postToChat(chatIdFor(ownerUid, palUid), {
    senderId: endedBy === "owner" ? ownerUid : palUid,
    kind: "system",
    text: endedBy === "owner"
      ? `${owner.displayName} removed ${pal.displayName} as a Petal Pal`
      : `${pal.displayName} stepped down as ${owner.displayName}'s Petal Pal`,
  });
  if (endedBy === "owner") {
    // Everyone who looks after this garden hears about it, so a pal can't be dropped quietly.
    await notify([palUid, ...remaining], {
      type: "approver_left",
      uid: ownerUid,
      title: `${owner.flower} ${owner.displayName} removed ${pal.displayName} as a Petal Pal`,
      body: remaining.length > 0
        ? `${owner.displayName} has ${remaining.length} Petal Pal${remaining.length === 1 ? "" : "s"} left.`
        : `${owner.displayName} has no Petal Pals left, so their limits are no longer locked in.`,
    });
  } else {
    await notify([ownerUid], {
      type: "approver_left",
      title: `${pal.flower} ${pal.displayName} stepped down as your Petal Pal`,
      body: remaining.length > 0
        ? "Your other Petal Pals are still looking after your garden."
        : "Invite someone new so your limits stay locked in.",
    });
  }
}

/** The garden owner removes one of their Petal Pals. No one else needs to agree. */
export const removeApprover = onCall(async (req) => {
  const uid = requireAuth(req);
  const { approverUid } = parse(z.object({ approverUid: id }), req.data);
  await endPartnership(uid, approverUid, "owner");
  return { ok: true };
});

/** A Petal Pal stops looking after someone's garden. */
export const stepDown = onCall(async (req) => {
  const uid = requireAuth(req);
  const { approveeUid } = parse(z.object({ approveeUid: id }), req.data);
  await endPartnership(approveeUid, uid, "pal");
  return { ok: true };
});
