"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { format } from "date-fns";
import { Avatar } from "@/components/ui/Avatar";
import { StatusChip } from "@/components/ui/StatusChip";
import { StatusComposer } from "@/components/project/StatusComposer";
import { isoToLocalDate } from "@/lib/dates";
import { isStatusStale, STATUS_SEVERITY } from "@/lib/status";
import { useUsers } from "@/lib/tasks";
import {
  useRemoveProjectFromPortfolio,
  type RollupProject,
} from "@/lib/portfolio";
import pageStyles from "@/app/(app)/page.module.css";
import taskStyles from "@/components/task/task.module.css";
import styles from "./portfolio.module.css";

/**
 * Severity for the default sort (docs/03 §4.d, PD-17): off_track, at_risk,
 * on_hold, on_track, then "no status" — trouble floats to the top. A stale or
 * unset status sorts as "no status" (grey), matching the chip treatment.
 */
function severity(p: RollupProject): number {
  if (!p.currentStatus || isStatusStale(p.statusUpdatedAt)) return 4;
  return STATUS_SEVERITY[p.currentStatus] ?? 4;
}

function dateRange(start?: string, end?: string): string {
  if (!start && !end) return "—";
  const s = start ? format(isoToLocalDate(start), "MMM d") : "…";
  const e = end ? format(isoToLocalDate(end), "MMM d, yyyy") : "…";
  return `${s} → ${e}`;
}

export function RollupTable({
  portfolioId,
  projects,
}: {
  portfolioId: string;
  projects: RollupProject[];
}) {
  const usersQ = useUsers();
  const [composerFor, setComposerFor] = useState<RollupProject | null>(null);

  const userById = useMemo(() => {
    const m = new Map<string, string>();
    for (const u of usersQ.data ?? [])
      if (u.id) m.set(u.id, u.name ?? u.email ?? "Unknown");
    return m;
  }, [usersQ.data]);

  // Default sort: status severity, then name for a stable tiebreak.
  const sorted = useMemo(() => {
    return [...projects].sort((a, b) => {
      const d = severity(a) - severity(b);
      if (d !== 0) return d;
      return (a.name ?? "").localeCompare(b.name ?? "");
    });
  }, [projects]);

  return (
    <div className={pageStyles.panel}>
      <div style={{ overflowX: "auto" }}>
        <table className={pageStyles.table}>
          <thead>
            <tr>
              <th>Project</th>
              <th>Status</th>
              <th>Owner</th>
              <th>Dates</th>
              <th>Progress</th>
              {/* A header needs text a screen reader can read, not just aria-label. */}
              <th>
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((p) => {
              const ownerName = p.ownerName ?? (p.ownerId ? userById.get(p.ownerId) : undefined);
              const total = p.tasksTotal ?? 0;
              const complete = p.tasksComplete ?? 0;
              const pct = total > 0 ? Math.round((complete / total) * 100) : 0;
              return (
                <tr key={p.projectId}>
                  <td>
                    <Link
                      className={styles.projectLink}
                      href={`/projects/${p.projectId}/list`}
                    >
                      <span
                        className={styles.dot}
                        style={p.color ? { background: p.color } : undefined}
                        aria-hidden="true"
                      />
                      <span className={styles.projectName}>{p.name}</span>
                    </Link>
                  </td>
                  <td>
                    <StatusChip
                      status={p.currentStatus}
                      statusUpdatedAt={p.statusUpdatedAt}
                      showTime
                      size="sm"
                      onClick={() => setComposerFor(p)}
                    />
                  </td>
                  <td>
                    {ownerName ? (
                      <span className={styles.ownerCell}>
                        <Avatar name={ownerName} seed={p.ownerId ?? ownerName} size={24} />
                        <span className={styles.ownerName}>{ownerName}</span>
                      </span>
                    ) : (
                      <span className={pageStyles.muted}>—</span>
                    )}
                  </td>
                  <td className={styles.dateCell}>{dateRange(p.startDate, p.endDate)}</td>
                  <td>
                    <div className={styles.progressCell}>
                      <div className={styles.progressTrack} aria-hidden="true">
                        <div className={styles.progressFill} style={{ width: `${pct}%` }} />
                      </div>
                      <span className={styles.progressText}>
                        {complete}/{total}
                      </span>
                    </div>
                  </td>
                  <td>
                    <div className={pageStyles.cellActions}>
                      <RemoveMenu
                        portfolioId={portfolioId}
                        projectId={p.projectId ?? ""}
                        projectName={p.name ?? "this project"}
                      />
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {composerFor && (
        <StatusComposer
          open={!!composerFor}
          onClose={() => setComposerFor(null)}
          projectId={composerFor.projectId ?? ""}
          initialStatus={composerFor.currentStatus}
        />
      )}
    </div>
  );
}

function RemoveMenu({
  portfolioId,
  projectId,
  projectName,
}: {
  portfolioId: string;
  projectId: string;
  projectName: string;
}) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const remove = useRemoveProjectFromPortfolio(portfolioId);

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={wrapRef} style={{ position: "relative" }}>
      <button
        type="button"
        className={styles.rowMenuBtn}
        aria-label={`Actions for ${projectName}`}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        ⋯
      </button>
      {open && (
        <div className={taskStyles.menu} role="menu">
          <button
            type="button"
            role="menuitem"
            className={`${taskStyles.menuItem} ${taskStyles.menuItemDanger}`}
            onClick={() => {
              setOpen(false);
              if (projectId) remove.mutate(projectId);
            }}
          >
            Remove from portfolio
          </button>
        </div>
      )}
    </div>
  );
}
