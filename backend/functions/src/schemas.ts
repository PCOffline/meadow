/** Input schemas for every callable function. Pure, so they can be unit-tested. */
import { z } from "zod";
import { errors } from "./copy";
import { INVITE_TOKEN_LENGTH } from "./format";
import { CapsSchema, DayMinutesSchema, ScheduleSchema } from "./schedule";

/** Must match FlowerAvatars in the app's Models.kt. */
export const FLOWERS = [
  "🌼", "🌸", "🌻", "🌷", "🌹", "🪻", "🌺", "🪷", "🍀", "🌿", "🍄", "🌵",
  "🐝", "🐞", "🦋", "🐌", "🐛", "🐸", "🐰", "🦔", "🐿️", "🦊", "🐱", "🐻", "🐼", "🐢", "🐥", "🦉",
] as const;
export const EMERGENCY_MINUTES = [15, 30, 60];

// Ids and package names become document paths, so they can't contain "/" or be "." or "..".
const id = z.string().trim().regex(/^[A-Za-z0-9_-]{1,128}$/);
const note = z.string().trim().max(280).default("");
const packageName = z.string().trim().regex(/^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*$/, errors.whichApp).max(200);
/** Whether `s` is a package name, and so safe to use as a limit's document id. */
export const isPackageName = (s: string): boolean => packageName.safeParse(s).success;
const appName = z.string().trim().max(100).nullish();
const dayKey = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, errors.badDate);
export const inviteToken = z.string().trim().regex(new RegExp(`^[A-Za-z0-9]{${INVITE_TOKEN_LENGTH}}$`), errors.linkGone);

export const ProfileInput = z.object({
  displayName: z.string().trim().min(1, errors.nameRequired).max(30, errors.nameTooLong),
  flower: z.enum(FLOWERS).catch("🌼"),
  /** Version of the terms and privacy notice the person accepted when signing up. */
  termsVersion: z.number().int().min(1).max(1000).nullish(),
});

export const InviteByCodeInput = z.object({
  friendCode: z.string().trim().toUpperCase().min(4, errors.codeRequired).max(12).regex(/^[A-Z0-9]+$/, errors.codeUnknown),
});
export const RespondInviteInput = z.object({ inviteId: id, accept: z.boolean() });
/** From a chat: ask the other person to be your Bumblebee, or offer to be theirs. */
export const InviteFromChatInput = z.object({ uid: id, offer: z.boolean() });
export const InviteIdInput = z.object({ inviteId: id });
export const InviteTokenInput = z.object({ token: inviteToken });
export const RemoveBumblebeeInput = z.object({ approverUid: id });
export const CancelRemovalInput = z.object({ approverUid: id });
export const StepDownInput = z.object({ approveeUid: id });

const schedule = ScheduleSchema.nullish();
/** The Bumblebees to ask; all of them when left out. */
const askWho = z.array(id).min(1, errors.pickABumblebee).max(20).nullish();
/** How the day stands, as the phone sees it: time spent on the app and time today allows. */
const usage = {
  usedMinutes: z.number().int().min(0).max(1440).nullish(),
  allowedMinutes: z.number().int().min(0).max(10080).nullish(),
};
const limitShape = {
  packageName,
  appName,
  dailyMinutes: z.number().int().min(0).max(1440),
  weeklyMinutes: z.number().int().min(0).max(10080),
  schedule,
  dayMinutes: DayMinutesSchema.nullish(),
  caps: CapsSchema.nullish(),
};
type Holds = { dailyMinutes: number; weeklyMinutes: number; schedule?: unknown; dayMinutes?: Record<string, number | undefined> | null; caps?: unknown[] | null };
const hasALimit = (r: Holds) =>
  r.dailyMinutes > 0 || r.weeklyMinutes > 0 || r.schedule != null
  || Object.values(r.dayMinutes ?? {}).some((m) => (m ?? 0) > 0) || (r.caps?.length ?? 0) > 0;

export const CreateRequestInput = z.discriminatedUnion("type", [
  z.object({ type: z.literal("extra_time"), packageName, appName, minutes: z.number().int().min(1).max(240), message: note, dayKey, approverIds: askWho, ...usage }),
  z.object({ type: z.literal("change_limit"), ...limitShape, message: note, dayKey, approverIds: askWho, ...usage })
    .refine(hasALimit, errors.setALimit),
  z.object({ type: z.literal("remove_limit"), packageName, appName, message: note, dayKey, approverIds: askWho, ...usage }),
], { error: errors.unknownRequest });

/** A limit saved through the backend: hours, days or caps changed while someone has Bumblebees. */
export const SaveLimitInput = z.object(limitShape).refine(hasALimit, errors.setALimit);

export const DecideRequestInput = z.object({
  requestId: id,
  approve: z.boolean(),
  note: z.string().trim().max(200).default(""),
  minutes: z.number().int().min(1).nullish(),
});
export const RequestIdInput = z.object({ requestId: id });

export const EmergencyInput = z.object({
  minutes: z.number().int().refine((m) => EMERGENCY_MINUTES.includes(m), errors.emergencyMinutes),
  reason: z.string().trim().min(10, errors.emergencyReason).max(280),
  packageName: z.string().trim().max(200).nullish(),
  // Ignored: Bumblebees are told the name the limit was planted under (see emergencyUnlock).
  appName: z.string().trim().max(100).nullish(),
  // Set only when reporting an unlock the phone already took while offline.
  startedAt: z.number().int().positive().nullish(),
  endedAt: z.number().int().positive().nullish(),
  reportId: z.string().regex(/^[A-Za-z0-9-]{8,64}$/).nullish(),
});
