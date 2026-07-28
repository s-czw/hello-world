"use client";

import { useMemo } from "react";
import { format } from "date-fns";
import { isoToLocalDate } from "@/lib/dates";
import { STATUSES, STATUS_NONE } from "@/lib/status";
import type { Rollup } from "@/lib/portfolio";
import styles from "./portfolio.module.css";

/** The four ratified status buckets + the stale/none bucket, in severity order. */
const SEGMENTS: { key: string; label: string; color: string }[] = [
  { key: "off_track", label: "Off track", color: "var(--status-off-track-bold)" },
  { key: "at_risk", label: "At risk", color: "var(--status-at-risk-bold)" },
  { key: "on_hold", label: "On hold", color: "var(--status-on-hold-bold)" },
  { key: "on_track", label: "On track", color: "var(--status-on-track-bold)" },
  { key: "no_update", label: "No update", color: "var(--status-none-bold)" },
];

/**
 * Roll-up strip (docs/03 §4.d): Projects (n) · a stacked status bar with each
 * segment count-labeled (never color-only) · Tasks complete (x/y + bar) · Due
 * range (earliest start → latest end). Counts come from the API summary; the
 * task totals and date range are folded from the per-project rows.
 */
export function RollupSummary({ rollup }: { rollup: Rollup }) {
  const s = rollup.summary ?? {};
  const projects = useMemo(() => rollup.projects ?? [], [rollup.projects]);

  const counts: Record<string, number> = {
    off_track: s.offTrack ?? 0,
    at_risk: s.atRisk ?? 0,
    on_hold: s.onHold ?? 0,
    on_track: s.onTrack ?? 0,
    no_update: s.noUpdate ?? 0,
  };
  const total = s.total ?? projects.length;

  const { tasksComplete, tasksTotal, dueRange } = useMemo(() => {
    let complete = 0;
    let all = 0;
    let min: Date | null = null;
    let max: Date | null = null;
    for (const p of projects) {
      complete += p.tasksComplete ?? 0;
      all += p.tasksTotal ?? 0;
      if (p.startDate) {
        const d = isoToLocalDate(p.startDate);
        if (!min || d < min) min = d;
      }
      if (p.endDate) {
        const d = isoToLocalDate(p.endDate);
        if (!max || d > max) max = d;
      }
    }
    let range = "No dates set";
    if (min && max) range = `${format(min, "MMM d")} → ${format(max, "MMM d, yyyy")}`;
    else if (min) range = `From ${format(min, "MMM d, yyyy")}`;
    else if (max) range = `Until ${format(max, "MMM d, yyyy")}`;
    return { tasksComplete: complete, tasksTotal: all, dueRange: range };
  }, [projects]);

  const pct = tasksTotal > 0 ? Math.round((tasksComplete / tasksTotal) * 100) : 0;
  const activeSegments = SEGMENTS.filter((seg) => counts[seg.key] > 0);

  return (
    <div className={styles.tiles}>
      <div className={styles.tile}>
        <div className={styles.tileLabel}>Projects</div>
        <div className={styles.tileValue}>{total}</div>
      </div>

      <div className={`${styles.tile} ${styles.tileWide}`}>
        <div className={styles.tileLabel}>Status</div>
        {total > 0 ? (
          <>
            <div className={styles.statusBar} role="img" aria-label={statusAria(counts)}>
              {activeSegments.map((seg) => (
                <span
                  key={seg.key}
                  className={styles.statusSeg}
                  style={{
                    flexGrow: counts[seg.key],
                    background: seg.color,
                  }}
                />
              ))}
            </div>
            <div className={styles.statusLegend}>
              {activeSegments.map((seg) => (
                <span key={seg.key} className={styles.legendItem}>
                  <span
                    className={styles.legendDot}
                    style={{ background: seg.color }}
                    aria-hidden="true"
                  />
                  {seg.label} {counts[seg.key]}
                </span>
              ))}
            </div>
          </>
        ) : (
          <div className={styles.tileValueMuted}>—</div>
        )}
      </div>

      <div className={styles.tile}>
        <div className={styles.tileLabel}>Tasks complete</div>
        <div className={styles.tileValue}>
          {tasksComplete}
          <span className={styles.tileOf}>/{tasksTotal}</span>
        </div>
        <div className={styles.tileTrack} aria-hidden="true">
          <div className={styles.tileFill} style={{ width: `${pct}%` }} />
        </div>
      </div>

      <div className={styles.tile}>
        <div className={styles.tileLabel}>Due range</div>
        <div className={styles.tileRange}>{dueRange}</div>
      </div>
    </div>
  );
}

function statusAria(counts: Record<string, number>): string {
  const parts = [
    ...STATUSES.map((d) => {
      const key = d.value;
      const n = counts[key] ?? 0;
      return n > 0 ? `${d.label} ${n}` : null;
    }),
    counts.no_update > 0 ? `${STATUS_NONE.label} ${counts.no_update}` : null,
  ].filter(Boolean);
  return `Project status breakdown: ${parts.join(", ") || "none"}`;
}
