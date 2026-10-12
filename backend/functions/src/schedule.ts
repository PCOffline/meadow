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
  const sorted = [...windows].sort((a, b) => a.from - b.from);
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
