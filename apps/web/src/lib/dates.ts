/**
 * Plain date utilities. Dates in Cairn are calendar days (`yyyy-MM-dd`, no time
 * zone) — the API's `dueDate` is a `date`. The typed parser accepts a small set
 * of PLAIN formats only; there is deliberately NO natural-language parsing
 * ("tomorrow", "fri") per docs/03 §2.6 / PD-31.
 */
import { format, parseISO, isValid } from "date-fns";

const MONTHS = [
  "jan",
  "feb",
  "mar",
  "apr",
  "may",
  "jun",
  "jul",
  "aug",
  "sep",
  "oct",
  "nov",
  "dec",
];

function monthIndex(name: string): number | null {
  const n = name.slice(0, 3).toLowerCase();
  const i = MONTHS.indexOf(n);
  return i < 0 ? null : i + 1;
}

/** Build a validated `yyyy-MM-dd` or null if the components aren't a real day. */
function toIso(y: number, m: number, d: number): string | null {
  if (m < 1 || m > 12 || d < 1 || d > 31) return null;
  const dt = new Date(y, m - 1, d);
  if (
    dt.getFullYear() !== y ||
    dt.getMonth() !== m - 1 ||
    dt.getDate() !== d
  ) {
    return null; // e.g. Feb 30 rolled over
  }
  const mm = String(m).padStart(2, "0");
  const dd = String(d).padStart(2, "0");
  return `${y}-${mm}-${dd}`;
}

/**
 * Parse a typed date into `yyyy-MM-dd`, or null if unrecognized. Accepts:
 *   ISO            2026-08-12
 *   day/month      12/08   12-08   12.08   (day-first; year optional → 12/08/26)
 *   month-name day aug 12  august 12  aug 12 2026
 *   day month-name 12 aug  12 august 12 aug 2026
 * Returns null for anything else (no NL parsing).
 */
export function parseTypedDate(raw: string): string | null {
  const s = raw.trim().toLowerCase();
  if (!s) return null;
  const year = new Date().getFullYear();

  let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (m) return toIso(+m[1], +m[2], +m[3]);

  // day-first numeric (matches the .ae locale + the docs "12/08" example)
  m = s.match(/^(\d{1,2})[/.\-](\d{1,2})(?:[/.\-](\d{2}|\d{4}))?$/);
  if (m) {
    let y = m[3] ? +m[3] : year;
    if (m[3] && m[3].length === 2) y += 2000;
    return toIso(y, +m[2], +m[1]);
  }

  m = s.match(/^([a-z]{3,9})\s+(\d{1,2})(?:,?\s+(\d{4}))?$/);
  if (m) {
    const mo = monthIndex(m[1]);
    if (mo) return toIso(m[3] ? +m[3] : year, mo, +m[2]);
  }

  m = s.match(/^(\d{1,2})\s+([a-z]{3,9})(?:,?\s+(\d{4}))?$/);
  if (m) {
    const mo = monthIndex(m[2]);
    if (mo) return toIso(m[3] ? +m[3] : year, mo, +m[1]);
  }

  return null;
}

/** A calendar-day ISO string parsed as a *local* midnight Date. */
export function isoToLocalDate(iso: string): Date {
  const d = parseISO(iso);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/** Format a due date for display: "Aug 12", or "Aug 12, 2026" off the current year. */
export function formatDue(iso: string): string {
  const d = isoToLocalDate(iso);
  if (!isValid(d)) return iso;
  const thisYear = new Date().getFullYear();
  return format(d, d.getFullYear() === thisYear ? "MMM d" : "MMM d, yyyy");
}

/** Local `yyyy-MM-dd` for today. */
export function todayIso(): string {
  return format(new Date(), "yyyy-MM-dd");
}

export type DueBucket = "Overdue" | "Today" | "Upcoming" | "Later" | "No date";

export const DUE_BUCKET_ORDER: DueBucket[] = [
  "Overdue",
  "Today",
  "Upcoming",
  "Later",
  "No date",
];

/** Classify a due date relative to today for the My Tasks groups (docs §4.g). */
export function dueBucket(iso: string | undefined): DueBucket {
  if (!iso) return "No date";
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const d = isoToLocalDate(iso).getTime();
  const t = today.getTime();
  if (d < t) return "Overdue";
  if (d === t) return "Today";
  if (d <= t + 7 * 86_400_000) return "Upcoming";
  return "Later";
}

/** True when a due date is strictly before today (used for off-track ink). */
export function isOverdue(iso: string | undefined): boolean {
  return dueBucket(iso) === "Overdue";
}
