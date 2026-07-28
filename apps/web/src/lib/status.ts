/**
 * Project-level status vocabulary (docs/03 §2.3, ratified spec enum, PD-17):
 * on_track / at_risk / off_track / on_hold. "Blocked" is gone everywhere.
 * Status lives on the project, never on a task (D-009).
 *
 * Staleness (M2 CONTRACT): a status whose `statusUpdatedAt` is null OR older
 * than 7 days reads as "no recent update" and gets the grey treatment. Matches
 * the API roll-up rule (strict > 7 days; exactly 7 days is still fresh).
 */
export type ProjectStatus = "on_track" | "at_risk" | "off_track" | "on_hold";

export interface StatusDef {
  value: ProjectStatus;
  label: string;
  /** Non-color differentiator — a chip is never color-only (§2.3, a11y §6). */
  icon: string;
  bg: string;
  ink: string;
  bold: string;
  /** Set only on on_hold (the helper line in the status picker, PD-14d). */
  helper?: string;
}

export const STATUSES: StatusDef[] = [
  {
    value: "on_track",
    label: "On track",
    icon: "●",
    bg: "var(--status-on-track-bg)",
    ink: "var(--status-on-track-ink)",
    bold: "var(--status-on-track-bold)",
  },
  {
    value: "at_risk",
    label: "At risk",
    icon: "▲",
    bg: "var(--status-at-risk-bg)",
    ink: "var(--status-at-risk-ink)",
    bold: "var(--status-at-risk-bold)",
  },
  {
    value: "off_track",
    label: "Off track",
    icon: "◆",
    bg: "var(--status-off-track-bg)",
    ink: "var(--status-off-track-ink)",
    bold: "var(--status-off-track-bold)",
  },
  {
    value: "on_hold",
    label: "On hold",
    icon: "⏸",
    bg: "var(--status-on-hold-bg)",
    ink: "var(--status-on-hold-ink)",
    bold: "var(--status-on-hold-bold)",
    helper:
      "Paused on purpose — the project stays visible. To hide a finished project, archive it.",
  },
];

/** Grey "no status" treatment, also used for stale statuses. */
export const STATUS_NONE: Omit<StatusDef, "value" | "helper"> = {
  label: "No status",
  icon: "○",
  bg: "var(--status-none-bg)",
  ink: "var(--status-none-ink)",
  bold: "var(--status-none-bold)",
};

export function statusDef(status: string | null | undefined): StatusDef | null {
  if (!status) return null;
  return STATUSES.find((s) => s.value === status) ?? null;
}

const STALE_MS = 7 * 24 * 60 * 60 * 1000;

/** True when a status update is missing or strictly older than 7 days. */
export function isStatusStale(statusUpdatedAt: string | null | undefined): boolean {
  if (!statusUpdatedAt) return true;
  const t = Date.parse(statusUpdatedAt);
  if (Number.isNaN(t)) return true;
  return Date.now() - t > STALE_MS;
}

/**
 * Severity order for the portfolio roll-up default sort (PD-17): trouble first.
 * Defined here so both the project and portfolio surfaces agree.
 */
export const STATUS_SEVERITY: Record<string, number> = {
  off_track: 0,
  at_risk: 1,
  on_hold: 2,
  on_track: 3,
};

/** Compact relative time — "just now", "5m ago", "3h ago", "2d ago", "4w ago". */
export function relativeTime(iso: string | null | undefined): string {
  if (!iso) return "";
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return "";
  const diff = Date.now() - t;
  if (diff < 0) return "just now";
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  const weeks = Math.floor(days / 7);
  if (weeks < 5) return `${weeks}w ago`;
  const months = Math.floor(days / 30);
  return `${months}mo ago`;
}
