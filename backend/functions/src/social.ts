import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { createApprovalRequest } from "./requests";
import {
  chatIdFor, createInvite, db, ensureChat, friendCodes, input, invites, notify, postToChat, randomCode, requests,
  requireAuth, text, userOf, users,
} from "./shared";

const FLOWERS = new Set(["🌼", "🌸", "🌻", "🌷", "🌹", "🪻", "🌺", "🍀", "🌿", "🍄"]);

/** Creates the caller's profile and claims a unique friend code. Safe to call twice. */
export const createProfile = onCall(async (req) => {
  const uid = requireAuth(req);
  const data = input(req);
  const displayName = text(data.displayName, 30);
  if (!displayName) throw new HttpsError("invalid-argument", "Please choose a name.");
  const flower = FLOWERS.has(String(data.flower)) ? String(data.flower) : "🌼";
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

/**
 * Invites someone by friend code. Once the caller already has approvers, adding a new one
 * becomes a request those approvers must agree to.
 */
export const sendInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  // Commitments must survive a reinstall, so an account needs an email before it gets approvers.
  if (req.auth?.token.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError("failed-precondition", "Add an email to your account first, so your garden can't be lost.");
  }
  const data = input(req);
  const code = text(data.friendCode, 12).toUpperCase();
  const message = text(data.message, 280);
  if (code.length < 4) throw new HttpsError("invalid-argument", "Enter your friend's code.");

  const codeSnap = await friendCodes().doc(code).get();
  if (!codeSnap.exists) throw new HttpsError("not-found", "No one has that code. Double-check it?");
  const targetUid = codeSnap.get("uid") as string;
  if (targetUid === uid) throw new HttpsError("invalid-argument", "That's your own code 🌼");

  const [meSnap, targetSnap] = await Promise.all([users().doc(uid).get(), users().doc(targetUid).get()]);
  const me = userOf(meSnap);
  const target = userOf(targetSnap);
  if (me.approverIds.includes(targetUid)) {
    throw new HttpsError("already-exists", `${target.displayName} is already your approver.`);
  }
  const pendingInvite = await invites()
    .where("fromUid", "==", uid).where("toUid", "==", targetUid).where("status", "==", "pending").limit(1).get();
  if (!pendingInvite.empty) throw new HttpsError("already-exists", `You've already invited ${target.displayName}.`);

  if (me.approverIds.length === 0) {
    await createInvite(uid, me, targetUid, target);
    return { status: "invited" };
  }

  const pendingRequest = await requests()
    .where("requesterId", "==", uid).where("status", "==", "pending").get();
  if (pendingRequest.docs.some((d) => d.get("type") === "add_approver" && d.get("targetUid") === targetUid)) {
    throw new HttpsError("already-exists", "Your approvers are already being asked about this.");
  }
  await createApprovalRequest(uid, me, {
    type: "add_approver",
    packageName: null,
    appName: null,
    minutes: 0,
    dailyMinutes: 0,
    weeklyMinutes: 0,
    targetUid,
    targetName: target.displayName,
    message,
    dayKey: new Date().toISOString().slice(0, 10),
  });
  return { status: "requested" };
});

export const respondInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  const data = input(req);
  const inviteId = text(data.inviteId, 100);
  const accept = data.accept === true;
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
      text: `${result.me.flower} ${result.me.displayName} is now ${result.from.displayName}'s approver`,
    });
    await notify([result.fromUid], {
      type: "invite_accepted",
      title: `${result.me.flower} ${result.me.displayName} is your approver now`,
      body: "Your limits are locked in. Only your approvers can give you more time.",
    });
  }
  return { ok: true };
});

export const cancelInvite = onCall(async (req) => {
  const uid = requireAuth(req);
  const inviteRef = invites().doc(text(input(req).inviteId, 100));
  const invite = await inviteRef.get();
  if (!invite.exists || invite.get("fromUid") !== uid) throw new HttpsError("not-found", "That invite is gone.");
  if (invite.get("status") === "pending") await inviteRef.update({ status: "cancelled" });
  return { ok: true };
});

/** An approver stops approving for someone. Always allowed: nobody can be forced to stay. */
export const stepDown = onCall(async (req) => {
  const uid = requireAuth(req);
  const approveeUid = text(input(req).approveeUid, 128);
  const [meSnap, approveeSnap] = await Promise.all([users().doc(uid).get(), users().doc(approveeUid).get()]);
  const me = userOf(meSnap);
  const approvee = userOf(approveeSnap);
  if (!approvee.approverIds.includes(uid)) throw new HttpsError("failed-precondition", "You're not their approver.");

  const batch = db().batch();
  batch.update(users().doc(approveeUid), { approverIds: FieldValue.arrayRemove(uid) });
  batch.update(users().doc(uid), { approveeIds: FieldValue.arrayRemove(approveeUid) });
  await batch.commit();

  await postToChat(chatIdFor(uid, approveeUid), {
    senderId: uid,
    kind: "system",
    text: `${me.displayName} stepped down as ${approvee.displayName}'s approver`,
  });
  await notify([approveeUid], {
    type: "approver_left",
    title: `${me.flower} ${me.displayName} stepped down as your approver`,
    body: approvee.approverIds.length > 1
      ? "Your other approvers are still looking after your garden."
      : "Invite someone new so your limits stay locked in.",
  });
  return { ok: true };
});
