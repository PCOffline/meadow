/**
 * Allowed hours for a limited app. Days are "1" (Monday) to "7" (Sunday), each a list of
 * windows in minutes since midnight, e.g. 10:00–16:00 is { from: 600, to: 960 }. A day that is
 * missing allows the whole day; an empty list allows none of it. No schedule at all means the
 * app may be used at any hour. Must match Schedule in the app's Models.kt.
 */
import { z } from "zod";

export type Window = { from: number; to: number };
export type Schedule = Partial<Record<Day, Window[]>>;

export const DAYS = ["1", "2", "3", "4", "5", "6", "7"] as const;
type Day = (typeof DAYS)[number];
const MINUTES_IN_DAY = 1440;
export const MAX_WINDOWS = 4;

const windowSchema = z.object({
  from: z.number().int().min(0).max(MINUTES_IN_DAY),
  to: z.number().int().min(0).max(MINUTES_IN_DAY),
}).refine((w) => w.from < w.to, "A time window must end after it starts.");

export const ScheduleSchema = z.object(
  Object.fromEntries(DAYS.map((d) => [d, z.array(windowSchema).max(MAX_WINDOWS).optional()])) as Record<Day, z.ZodOptional<z.ZodArray<typeof windowSchema>>>,
).strict();

/** Sorted, overlapping windows joined. */
function merge(windows: Window[]): Window[] {
  const sorted = windows.toSorted((a, b) => a.from - b.from);
  const out: Window[] = [];
  for (const w of sorted) {
    const last = out[out.length - 1];
    if (last && w.from <= last.to) last.to = Math.max(last.to, w.to);
    else out.push({ ...w });
  }
  return out;
}

const allDay: Window[] = [{ from: 0, to: MINUTES_IN_DAY }];

function allowedOn(schedule: Schedule | null | undefined, day: Day): Window[] {
  if (!schedule) return allDay;
  const windows = schedule[day];
  return windows === undefined ? allDay : merge(windows);
}

/** True when every hour `next` allows was already allowed by `old`: nothing got looser. */
export function isScheduleStricter(old: Schedule | null | undefined, next: Schedule | null | undefined): boolean {
  return DAYS.every((day) => {
    const before = allowedOn(old, day);
    return allowedOn(next, day).every((w) => before.some((b) => b.from <= w.from && w.to <= b.to));
  });
}

/** Firestore stores exactly the days given, with windows merged and sorted. */
export function normalize(schedule: Schedule | null | undefined): Schedule | null {
  if (!schedule) return null;
  const out: Schedule = {};
  for (const day of DAYS) {
    const windows = schedule[day];
    if (windows !== undefined) out[day] = merge(windows);
  }
  return out;
}

const clock = (m: number) => `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;

/** "10:00–16:00", "10:00–16:00 on weekdays", or "at set hours" when days differ. */
export function describeSchedule(schedule: Schedule | null | undefined): string | null {
  const s = normalize(schedule);
  if (!s) return null;
  const days = DAYS.map((d) => JSON.stringify(allowedOn(s, d)));
  if (days.every((d) => d === days[0])) {
    const windows = allowedOn(s, "1");
    if (windows.length === 0) return "not at all";
    return `only ${windows.map((w) => `${clock(w.from)}–${clock(w.to)}`).join(" and ")}`;
  }
  return "only at set hours";
}

/**
 * A day's own daily limit, replacing the everyday one on that weekday ("6": 120 is two hours on
 * Saturdays). 0 means no daily limit that day. Must match AppLimit.dayMinutes in the app.
 */
export type DayMinutes = Partial<Record<Day, number>>;

export const DayMinutesSchema = z.object(
  Object.fromEntries(DAYS.map((d) => [d, z.number().int().min(0).max(MINUTES_IN_DAY).optional()])) as Record<Day, z.ZodOptional<z.ZodNumber>>,
).strict();

/**
 * At most [minutes] of use between [from] and [to] on each of [days] (1 = Monday), on top of the
 * daily limit: "up to 30 minutes between 10:00 and 16:00 on weekdays". Must match TimeCap in the app.
 */
export type Cap = { days: number[]; from: number; to: number; minutes: number };
export const MAX_CAPS = 6;

export const CapsSchema = z.array(
  z.object({
    days: z.array(z.number().int().min(1).max(7)).min(1).max(7),
    from: z.number().int().min(0).max(MINUTES_IN_DAY),
    to: z.number().int().min(0).max(MINUTES_IN_DAY),
    minutes: z.number().int().min(1).max(MINUTES_IN_DAY),
  }).strict()
    .refine((c) => c.from < c.to, "A time window must end after it starts.")
    .refine((c) => c.minutes < c.to - c.from, "A cap must be shorter than its hours."),
).max(MAX_CAPS);

/** Days listed once each, in order; nothing kept when there's nothing to keep. */
export function normalizeDayMinutes(days: DayMinutes | null | undefined): DayMinutes | null {
  if (!days) return null;
  const out: DayMinutes = {};
  for (const day of DAYS) if (days[day] !== undefined) out[day] = days[day];
  return Object.keys(out).length ? out : null;
}

export function normalizeCaps(caps: Cap[] | null | undefined): Cap[] | null {
  if (!caps?.length) return null;
  return caps.map((c) => ({ days: [...new Set(c.days)].toSorted((a, b) => a - b), from: c.from, to: c.to, minutes: c.minutes }));
}

/** Everything a limit holds back, as stored. */
export interface LimitRules {
  dailyMinutes: number;
  weeklyMinutes: number;
  schedule?: Schedule | null;
  dayMinutes?: DayMinutes | null;
  caps?: Cap[] | null;
}

/** The daily limit on [day]: its own, or the everyday one. 0 is none. */
function dailyOn(rules: LimitRules, day: Day): number {
  return rules.dayMinutes?.[day] ?? rules.dailyMinutes;
}

/** 0 means no limit, so anything is at least as strict as 0. */
const minutesStricter = (old: number, next: number) => old === 0 || (next > 0 && next <= old);

/** Whether [next] still holds back no less than [cap] did on [day]. */
function capKept(cap: Cap, day: number, next: LimitRules): boolean {
  const daily = dailyOn(next, String(day) as Day);
  if (daily > 0 && daily <= cap.minutes) return true;
  return (next.caps ?? []).some((c) => c.days.includes(day) && c.from <= cap.from && cap.to <= c.to && c.minutes <= cap.minutes);
}

/** True when nothing [next] allows went beyond what [old] allowed: each day, the week, the hours and every cap. */
export function isLimitStricter(old: LimitRules, next: LimitRules): boolean {
  return DAYS.every((day) => minutesStricter(dailyOn(old, day), dailyOn(next, day)))
    && minutesStricter(old.weeklyMinutes, next.weeklyMinutes)
    && isScheduleStricter(old.schedule ?? null, next.schedule ?? null)
    && (old.caps ?? []).every((cap) => cap.days.every((day) => capKept(cap, day, next)));
}

const SHORT_DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** "on weekdays", "at weekends", "every day" or "on Mon and Wed". */
export function describeDays(days: number[]): string {
  const set = [...new Set(days)].toSorted((a, b) => a - b);
  const key = set.join("");
  if (key === "1234567") return "every day";
  if (key === "12345") return "on weekdays";
  if (key === "67") return "at weekends";
  const names = set.map((d) => SHORT_DAYS[d - 1]);
  return `on ${names.join(", ").replace(/, ([^,]*)$/, " and $1")}`;
}

/** "2h at weekends", "no limit on Sun": each group of days sharing their own limit. */
export function describeDayMinutes(days: DayMinutes | null | undefined, format: (m: number) => string): string[] {
  const groups = new Map<number, number[]>();
  for (const day of DAYS) {
    const m = days?.[day];
    if (m !== undefined) groups.set(m, [...(groups.get(m) ?? []), Number(day)]);
  }
  return [...groups].map(([m, d]) => `${m > 0 ? format(m) : "no limit"} ${describeDays(d)}`);
}

/** "up to 30m 10:00–16:00 on weekdays". */
export function describeCaps(caps: Cap[] | null | undefined, format: (m: number) => string): string[] {
  return (caps ?? []).map((c) => `up to ${format(c.minutes)} ${clock(c.from)}–${clock(c.to)} ${describeDays(c.days)}`);
}
