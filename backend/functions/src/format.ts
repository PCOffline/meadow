import { randomInt } from "node:crypto";
import { describeSchedule, Schedule } from "./schedule";

/** "disable_strict_mode" only appears on old requests: strict mode now turns off without asking. */
export type RequestType = "extra_time" | "change_limit" | "remove_limit" | "disable_strict_mode";

/** The parts of a request needed to describe it in words. */
export interface RequestSummary {
  type: RequestType;
  appName: string | null;
  minutes: number;
  dailyMinutes: number;
  weeklyMinutes: number;
  schedule?: Schedule | null;
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

export function limitText(daily: number, weekly: number, schedule?: Schedule | null): string {
  const parts: string[] = [];
  if (daily > 0) parts.push(`${formatMinutes(daily)} a day`);
  if (weekly > 0) parts.push(`${formatMinutes(weekly)} a week`);
  const hours = describeSchedule(schedule);
  if (hours) parts.push(hours);
  return parts.length ? parts.join(", ").replace(/, ([^,]*)$/, " and $1") : "no limit";
}

/**
 * The name a stored limit was planted under, or the package name when there's none. Bumblebees
 * read it, so it never comes from what a phone says about a request or an unlock.
 */
export function storedAppName(limit: { get(field: string): unknown } | null, packageName: string): string {
  const name = limit?.get("appName");
  return typeof name === "string" && name.trim() !== "" ? name : packageName;
}

/** Lower-case phrase describing what was asked, e.g. "15 more minutes on Instagram". */
export function summarize(r: RequestSummary): string {
  const app = r.appName ?? "an app";
  switch (r.type) {
    case "extra_time":
      return `${r.minutes} more minutes on ${app}`;
    case "change_limit":
      return `a new limit for ${app}: ${limitText(r.dailyMinutes, r.weeklyMinutes, r.schedule)}`;
    case "remove_limit":
      return `removing the limit on ${app}`;
    case "disable_strict_mode":
      return "turning off strict mode";
  }
}

export function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
