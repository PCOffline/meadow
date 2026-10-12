import { FieldValue, Transaction } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import {
  chatIdFor, db, notify, parse, postToChat, requests, requireAuth, RequestDoc, summarize, userOf, users, UserDoc,
} from "./shared";

const MAX_PENDING = 10;

const note = z.string().trim().max(280).default("");
const packageName = z.string().trim().min(1, "Which app?").max(200);
const appName = z.string().trim().max(100).nullish();
const dayKey = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Bad date.");

const CreateInput = z.discriminatedUnion("type", [
  z.object({ type: z.literal("extra_time"), packageName, appName, minutes: z.number().int().min(1).max(240), message: note, dayKey }),
  z.object({
    type: z.literal("change_limit"),
    packageName,
    appName,
    dailyMinutes: z.number().int().min(0).max(1440),
    weeklyMinutes: z.number().int().min(0).max(10080),
    message: note,
    dayKey,
  }).refine((r) => r.dailyMinutes > 0 || r.weeklyMinutes > 0, "Set a daily or weekly limit."),
  z.object({ type: z.literal("remove_limit"), packageName, appName, message: note, dayKey }),
  z.object({ type: z.literal("disable_strict_mode"), message: note, dayKey }),
], { error: "Unknown request type." });

type NewRequest = Omit<RequestDoc, "requesterId" | "requesterName" | "status" | "approverIds">;

/** Stores a request, drops a card into each Petal Pal's chat and pings them. */
async function createApprovalRequest(uid: string, me: UserDoc, req: NewRequest): Promise<string> {
  const approverIds = [...me.approverIds];
  if (approverIds.length === 0) throw new HttpsError("failed-precondition", "Invite a Petal Pal first.");

  const ref = requests().doc();
  const doc: RequestDoc & { createdAt: FieldValue } = {
    ...req,
    requesterId: uid,
    requesterName: me.displayName,
    status: "pending",
    approverIds,
    createdAt: FieldValue.serverTimestamp(),
  };
  await ref.set(doc);
  const summary = summarize(doc);

  await Promise.all(
    approverIds.map((approverId) =>
      postToChat(chatIdFor(uid, approverId), {
        senderId: uid,
        kind: "request",
        text: `💌 ${me.displayName} asks: ${summary}`,
        requestId: ref.id,
      }),
    ),
  );
  await notify(approverIds, {
    type: "request_new",
    requestId: ref.id,
    title: `${me.flower} ${me.displayName} asks for ${summary}`,
    body: req.message ? `“${req.message}”` : "Tap to answer. You can also say yes right from here.",
  });
  return ref.id;
}

export const createRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const input = parse(CreateInput, call.data);
  const me = userOf(await users().doc(uid).get());
  if (me.approverIds.length === 0) throw new HttpsError("failed-precondition", "Invite a Petal Pal first.");

  const request: NewRequest = {
    type: input.type,
    packageName: null,
    appName: null,
    minutes: 0,
    dailyMinutes: 0,
    weeklyMinutes: 0,
    message: input.message,
    dayKey: input.dayKey,
  };
  if (input.type !== "disable_strict_mode") {
    const limit = await users().doc(uid).collection("limits").doc(input.packageName).get();
    if (!limit.exists) throw new HttpsError("failed-precondition", "That app doesn't have a limit.");
    request.packageName = input.packageName;
    request.appName = input.appName || (limit.get("appName") as string);
  }
  if (input.type === "extra_time") request.minutes = input.minutes;
  if (input.type === "change_limit") {
    request.dailyMinutes = input.dailyMinutes;
    request.weeklyMinutes = input.weeklyMinutes;
  }
  if (input.type === "disable_strict_mode" && !me.strictMode) {
    throw new HttpsError("failed-precondition", "Strict mode is already off.");
  }

  const pending = await requests().where("requesterId", "==", uid).where("status", "==", "pending").get();
  if (pending.size >= MAX_PENDING) {
    throw new HttpsError("resource-exhausted", "You have lots of requests waiting already. Give your friends a moment 🌿");
  }
  const duplicate = pending.docs.some((d) =>
    d.get("type") === request.type && (d.get("packageName") ?? null) === request.packageName,
  );
  if (duplicate) throw new HttpsError("already-exists", "You've already asked for this — it's waiting for an answer.");

  const requestId = await createApprovalRequest(uid, me, request);
  return { requestId };
});

/** Applies an approved request. Runs inside the decision transaction. */
function applyApproval(tx: Transaction, requestId: string, r: RequestDoc, deciderUid: string, grantedMinutes: number): void {
  const requesterRef = users().doc(r.requesterId);
  switch (r.type) {
    case "extra_time":
      tx.set(requesterRef.collection("grants").doc(requestId), {
        packageName: r.packageName,
        minutes: grantedMinutes,
        dayKey: r.dayKey,
        requestId,
        grantedBy: deciderUid,
        createdAt: FieldValue.serverTimestamp(),
      });
      break;
    case "change_limit":
      tx.set(
        requesterRef.collection("limits").doc(r.packageName!),
        {
          packageName: r.packageName,
          appName: r.appName,
          dailyMinutes: r.dailyMinutes,
          weeklyMinutes: r.weeklyMinutes,
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
      break;
    case "remove_limit":
      tx.delete(requesterRef.collection("limits").doc(r.packageName!));
      break;
    case "disable_strict_mode":
      tx.update(requesterRef, { strictMode: false });
      break;
  }
}

const DecideInput = z.object({
  requestId: z.string().trim().min(1).max(100),
  approve: z.boolean(),
  note: z.string().trim().max(200).default(""),
  minutes: z.number().int().min(1).nullish(),
});

export const decideRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const { requestId, approve, note: decisionNote, minutes } = parse(DecideInput, call.data);
  const requestRef = requests().doc(requestId);

  const outcome = await db().runTransaction(async (tx) => {
    const snap = await tx.get(requestRef);
    if (!snap.exists) throw new HttpsError("not-found", "That request is gone.");
    const r = snap.data() as RequestDoc;
    if (r.status !== "pending") throw new HttpsError("failed-precondition", "That request was already answered.");
    if (r.requesterId === uid) throw new HttpsError("permission-denied", "You can't answer your own request 🌱");
    if (!r.approverIds.includes(uid)) throw new HttpsError("permission-denied", "You're not a Petal Pal for this.");

    const [requesterSnap, deciderSnap] = await Promise.all([tx.get(users().doc(r.requesterId)), tx.get(users().doc(uid))]);
    const requester = userOf(requesterSnap);
    const decider = userOf(deciderSnap);
    // Pals who were removed or stepped down since the request was made can't answer it.
    if (!requester.approverIds.includes(uid)) throw new HttpsError("permission-denied", "You're no longer their Petal Pal.");

    const grantedMinutes = approve && r.type === "extra_time" && minutes ? Math.min(minutes, r.minutes) : r.minutes;
    if (approve) applyApproval(tx, requestId, r, uid, grantedMinutes);
    tx.update(requestRef, {
      status: approve ? "approved" : "denied",
      decidedBy: uid,
      decidedByName: decider.displayName,
      decisionNote,
      decidedAt: FieldValue.serverTimestamp(),
      ...(r.type === "extra_time" && approve ? { minutes: grantedMinutes } : {}),
    });
    return { r: { ...r, minutes: grantedMinutes }, decider };
  });

  const { r, decider } = outcome;
  const summary = summarize(r);
  await postToChat(chatIdFor(uid, r.requesterId), {
    senderId: uid,
    kind: "system",
    text: approve ? `🌸 ${decider.displayName} said yes: ${summary}` : `🍃 ${decider.displayName} said not today: ${summary}`,
  });
  await notify([r.requesterId], {
    type: "request_decided",
    requestId,
    title: approve ? `🌸 ${decider.displayName} said yes!` : `🍃 ${decider.displayName} said not today`,
    body: decisionNote ? `${capitalize(summary)} — “${decisionNote}”` : capitalize(summary),
  });
  return { ok: true };
});

export const cancelRequest = onCall(async (call) => {
  const uid = requireAuth(call);
  const { requestId } = parse(z.object({ requestId: z.string().trim().min(1).max(100) }), call.data);
  const requestRef = requests().doc(requestId);
  await db().runTransaction(async (tx) => {
    const snap = await tx.get(requestRef);
    if (!snap.exists || snap.get("requesterId") !== uid) throw new HttpsError("not-found", "That request is gone.");
    if (snap.get("status") !== "pending") return;
    tx.update(requestRef, { status: "cancelled", decidedAt: FieldValue.serverTimestamp() });
  });
  return { ok: true };
});

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
