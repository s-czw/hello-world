"use client";

import {
  STATUS_NONE,
  isStatusStale,
  relativeTime,
  statusDef,
} from "@/lib/status";
import styles from "./ui.module.css";

export interface StatusChipProps {
  status?: string | null;
  statusUpdatedAt?: string | null;
  /** Append "· Nd ago" (or "· No update") after the label. */
  showTime?: boolean;
  size?: "sm" | "md";
  /** When set, the chip renders as a button (an entry point to the composer). */
  onClick?: () => void;
  /** Keep the status color even when old — for history entries (posted-at color). */
  ignoreStale?: boolean;
  className?: string;
  title?: string;
}

/**
 * Project status chip (docs/03 §2.3). Label + icon, never color-only. A missing
 * status, or one older than 7 days, renders grey ("stale = no recent update").
 */
export function StatusChip({
  status,
  statusUpdatedAt,
  showTime = false,
  size = "md",
  onClick,
  ignoreStale = false,
  className,
  title,
}: StatusChipProps) {
  const def = statusDef(status);
  const stale = ignoreStale ? false : isStatusStale(statusUpdatedAt);
  // Stale or unset → grey; a set-but-stale status keeps its label but goes grey.
  const grey = !def || stale;
  const visual = grey ? STATUS_NONE : def;
  const label = def ? def.label : "No status";

  let time = "";
  if (showTime) {
    if (!def) time = "";
    else if (!statusUpdatedAt) time = "No update";
    else time = relativeTime(statusUpdatedAt);
  }

  const classes = [
    styles.statusChip,
    size === "sm" ? styles.statusChipSm : "",
    onClick ? styles.statusChipBtn : "",
    className ?? "",
  ]
    .filter(Boolean)
    .join(" ");

  const style: React.CSSProperties = {
    background: visual.bg,
    color: visual.ink,
  };

  const inner = (
    <>
      <span aria-hidden="true" className={styles.statusChipIcon}>
        {visual.icon}
      </span>
      <span>{label}</span>
      {time && <span className={styles.statusChipTime}>· {time}</span>}
    </>
  );

  const aria = `Status: ${label}${
    def && stale ? " (needs an update)" : ""
  }${showTime && def && statusUpdatedAt ? `, updated ${relativeTime(statusUpdatedAt)}` : ""}`;

  if (onClick) {
    return (
      <button
        type="button"
        className={classes}
        style={style}
        onClick={onClick}
        aria-label={aria}
        title={title ?? "Update status"}
      >
        {inner}
      </button>
    );
  }
  return (
    <span className={classes} style={style} aria-label={aria} title={title}>
      {inner}
    </span>
  );
}
