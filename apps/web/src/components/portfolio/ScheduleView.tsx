"use client";

import { useMemo } from "react";
import { format } from "date-fns";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { DatePicker } from "@/components/ui/DatePicker";
import { isoToLocalDate, todayIso } from "@/lib/dates";
import { isStatusStale, statusDef, STATUS_NONE } from "@/lib/status";
import {
  useRollup,
  useUpdateProjectDates,
  type RollupProject,
} from "@/lib/portfolio";
import styles from "./portfolio.module.css";

function barColor(p: RollupProject): string {
  const def = statusDef(p.currentStatus);
  if (!def || isStatusStale(p.statusUpdatedAt)) return STATUS_NONE.bold;
  return def.bold;
}

const clamp = (n: number) => Math.max(0, Math.min(100, n));

/**
 * Portfolio schedule (docs/03 §4.e floor, simplified per PD-2): a date-ordered
 * table, one row per project with a proportional mini date-bar inside the
 * portfolio's overall min→max range, colored by status, with a "today" tick.
 * Undated projects collect in a tray at the bottom with inline date editing —
 * that is how dates get fixed (no axis, no zoom, no drag).
 */
export function ScheduleView({ portfolioId }: { portfolioId: string }) {
  const rollup = useRollup(portfolioId);
  const updateDates = useUpdateProjectDates(portfolioId);
  const projects = useMemo(
    () => rollup.data?.projects ?? [],
    [rollup.data?.projects],
  );

  const { dated, undated, minMs, spanMs, todayPct } = useMemo(() => {
    const dated = projects.filter((p) => p.startDate && p.endDate);
    const undated = projects.filter((p) => !p.startDate || !p.endDate);
    let min = Infinity;
    let max = -Infinity;
    for (const p of dated) {
      const s = isoToLocalDate(p.startDate!).getTime();
      const e = isoToLocalDate(p.endDate!).getTime();
      if (s < min) min = s;
      if (e > max) max = e;
    }
    const hasRange = dated.length > 0 && max > min;
    const spanMs = hasRange ? max - min : 1;
    dated.sort(
      (a, b) =>
        isoToLocalDate(a.startDate!).getTime() -
        isoToLocalDate(b.startDate!).getTime(),
    );
    const todayMs = isoToLocalDate(todayIso()).getTime();
    const todayPct =
      hasRange && todayMs >= min && todayMs <= max
        ? clamp(((todayMs - min) / spanMs) * 100)
        : null;
    return { dated, undated, minMs: min, spanMs, todayPct };
  }, [projects]);

  if (rollup.isLoading) {
    return (
      <div className={styles.scheduleWrap}>
        <Skeleton height={200} />
      </div>
    );
  }

  if (projects.length === 0) {
    return (
      <div className={styles.scheduleWrap}>
        <EmptyState
          icon="🗓"
          headline="Add projects to this portfolio"
          body="Projects with start and end dates appear here on the schedule."
        />
      </div>
    );
  }

  return (
    <div className={styles.scheduleWrap}>
      {dated.length > 0 ? (
        <div className={styles.schedule}>
          {todayPct !== null && (
            <div className={styles.todayTick} style={{ left: `calc(240px + (100% - 240px) * ${todayPct / 100})` }}>
              <span className={styles.todayPill}>Today</span>
            </div>
          )}
          {dated.map((p) => {
            const s = isoToLocalDate(p.startDate!).getTime();
            const e = isoToLocalDate(p.endDate!).getTime();
            const left = clamp(((s - minMs) / spanMs) * 100);
            const width = Math.max(2, clamp(((e - s) / spanMs) * 100));
            const color = barColor(p);
            return (
              <div key={p.projectId} className={styles.schedRow}>
                <div className={styles.schedRail}>
                  <span
                    className={styles.dot}
                    style={{ background: color }}
                    aria-hidden="true"
                  />
                  <span className={styles.schedName} title={p.name}>
                    {p.name}
                  </span>
                </div>
                <div className={styles.schedTrack}>
                  <div
                    className={styles.schedBar}
                    style={{ left: `${left}%`, width: `${width}%`, background: color }}
                    title={`${format(isoToLocalDate(p.startDate!), "MMM d, yyyy")} → ${format(isoToLocalDate(p.endDate!), "MMM d, yyyy")}`}
                    aria-label={`${p.name}: ${format(isoToLocalDate(p.startDate!), "MMM d, yyyy")} to ${format(isoToLocalDate(p.endDate!), "MMM d, yyyy")}`}
                  />
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <p className={styles.schedHint}>
          No projects have both a start and end date yet. Set dates below to see
          them on the schedule.
        </p>
      )}

      {undated.length > 0 && (
        <div className={styles.tray}>
          <div className={styles.trayTitle}>No dates ({undated.length})</div>
          <div className={styles.trayList}>
            {undated.map((p) => (
              <div key={p.projectId} className={styles.trayRow}>
                <span className={styles.trayName} title={p.name}>
                  <span
                    className={styles.dot}
                    style={{ background: barColor(p) }}
                    aria-hidden="true"
                  />
                  {p.name}
                </span>
                <div className={styles.trayDates}>
                  <DatePicker
                    value={p.startDate ?? null}
                    ariaLabel={`Start date for ${p.name}`}
                    placeholder="Start"
                    onChange={(iso) =>
                      updateDates.mutate({ projectId: p.projectId!, startDate: iso })
                    }
                  />
                  <span className={styles.trayArrow} aria-hidden="true">
                    →
                  </span>
                  <DatePicker
                    value={p.endDate ?? null}
                    ariaLabel={`End date for ${p.name}`}
                    placeholder="End"
                    onChange={(iso) =>
                      updateDates.mutate({ projectId: p.projectId!, endDate: iso })
                    }
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
