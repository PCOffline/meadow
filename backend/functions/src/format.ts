import { randomInt } from "node:crypto";

export type RequestType = "extra_time" | "change_limit" | "remove_limit" | "disable_strict_mode";

/** The parts of a request needed to describe it in words. */
export interface RequestSummary {
  type: RequestType;
  appName: string | null;
  minutes: number;
  dailyMinutes: number;
  weeklyMinutes: number;
}

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
export const INVITE_TOKEN_LENGTH = 16;

function randomString(alphabet: string, length: number): string {
  let out = "";
  for (let i = 0; i < length; i++) out += alphabet[randomInt(alphabet.length)];
  return out;
}

/** Short, unambiguous code people can read out loud (no 0/O, 1/I). */
export const randomFriendCode = (): string => randomString(CODE_ALPHABET, 6);

/** Unguessable token for invite links (~95 bits). */
export const randomInviteToken = (): string => randomString(TOKEN_ALPHABET, INVITE_TOKEN_LENGTH);

export const chatIdFor = (a: string, b: string): string => [a, b].sort().join("_");

export function inviteUrl(projectId: string, token: string): string {
  return `https://${projectId}.web.app/i/${token}`;
}

export function formatMinutes(minutes: number): string {
  if (minutes <= 0) return "0m";
  if (minutes < 60) return `${minutes}m`;
  if (minutes % 60 === 0) return `${minutes / 60}h`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

export function limitText(daily: number, weekly: number): string {
  const parts: string[] = [];
  if (daily > 0) parts.push(`${formatMinutes(daily)} a day`);
  if (weekly > 0) parts.push(`${formatMinutes(weekly)} a week`);
  return parts.length ? parts.join(" and ") : "no limit";
}

/** Lower-case phrase describing what was asked, e.g. "15 more minutes on Instagram". */
export function summarize(r: RequestSummary): string {
  const app = r.appName ?? "an app";
  switch (r.type) {
    case "extra_time":
      return `${r.minutes} more minutes on ${app}`;
    case "change_limit":
      return `a new limit for ${app}: ${limitText(r.dailyMinutes, r.weeklyMinutes)}`;
    case "remove_limit":
      return `removing the limit on ${app}`;
    case "disable_strict_mode":
      return "turning off strict mode";
  }
}

export function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
