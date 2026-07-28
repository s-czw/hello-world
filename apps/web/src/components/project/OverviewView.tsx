"use client";

import { useMemo, useState } from "react";
import { format } from "date-fns";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { StatusChip } from "@/components/ui/StatusChip";
import { DatePicker } from "@/components/ui/DatePicker";
import { Skeleton } from "@/components/ui/Skeleton";
import { linkify } from "@/lib/linkify";
import { isOverdue } from "@/lib/dates";
import { relativeTime, statusDef, STATUS_NONE } from "@/lib/status";
import { useProject, useTasks, useUsers } from "@/lib/tasks";
import {
  useStatusUpdates,
  useTeams,
  useUpdateProject,
  type StatusUpdate,
} from "@/lib/project";
import { StatusComposer } from "./StatusComposer";
import styles from "./project.module.css";

export function OverviewView({ projectId }: { projectId: string }) {
  const project = useProject(projectId);
  const tasksQ = useTasks(projectId);
  const usersQ = useUsers();
  const teamsQ = useTeams();
  const history = useStatusUpdates(projectId);
  const updateProject = useUpdateProject(projectId);
  const [composerOpen, setComposerOpen] = useState(false);

  const userById = useMemo(() => {
    const m = new Map<string, string>();
    for (const u of usersQ.data ?? [])
      if (u.id) m.set(u.id, u.name ?? u.email ?? "Unknown");
    return m;
  }, [usersQ.data]);

  const p = project.data;

  const tasks = tasksQ.data ?? [];
  const total = tasks.length;
  const complete = tasks.filter((t) => t.completed).length;
  const overdue = tasks.filter((t) => !t.completed && isOverdue(t.dueDate)).length;
  const pct = total > 0 ? Math.round((complete / total) * 100) : 0;

  // Project members = its team's members (plus the owner), resolved to users.
  const members = useMemo(() => {
    if (!p) return [];
    const team = (teamsQ.data ?? []).find((t) => t.id === p.teamId);
    const ids = new Set<string>(team?.memberIds ?? []);
    if (p.ownerId) ids.add(p.ownerId);
    return [...ids]
      .map((id) => ({ id, name: userById.get(id) ?? "Member" }))
      .filter((m) => userById.has(m.id));
  }, [p, teamsQ.data, userById]);

  const ownerName = p?.ownerId ? userById.get(p.ownerId) ?? "—" : "Unassigned";

  if (project.isLoading || !p) {
    return (
      <div className={styles.overview}>
        <Skeleton height={120} />
        <Skeleton height={120} />
      </div>
    );
  }

  return (
    <div className={styles.overview}>
      {/* ── Status block ─────────────────────────────────────────────── */}
      <section className={styles.card} aria-label="Status">
        <div className={styles.cardHead}>
          <h2 className={styles.cardTitle}>Status</h2>
          <div className={styles.statusBlockActions}>
            <StatusChip
              status={p.currentStatus}
              statusUpdatedAt={p.statusUpdatedAt}
              showTime
              onClick={() => setComposerOpen(true)}
            />
            <Button
              variant="secondary"
              size="compact"
              onClick={() => setComposerOpen(true)}
            >
              Update status
            </Button>
          </div>
        </div>
        <div className={styles.history}>
          {history.isLoading ? (
            <Skeleton height={48} />
          ) : (history.data ?? []).length === 0 ? (
            <p className={styles.emptyLine}>
              No status updates yet. Post the first one to keep the team aligned.
            </p>
          ) : (
            (history.data ?? []).map((u) => (
              <HistoryItem key={u.id} update={u} authorName={userById.get(u.authorId ?? "") ?? "Someone"} />
            ))
          )}
        </div>
      </section>

      {/* ── About ────────────────────────────────────────────────────── */}
      <section className={styles.card} aria-label="About">
        <div className={styles.cardHead}>
          <h2 className={styles.cardTitle}>About</h2>
        </div>
        <div className={styles.about}>
          <div className={styles.aboutDesc}>
            {p.description ? (
              linkify(p.description)
            ) : (
              <span className={styles.emptyLine}>No description.</span>
            )}
          </div>
          <dl className={styles.aboutGrid}>
            <dt>Owner</dt>
            <dd>
              {p.ownerId ? (
                <span className={styles.ownerRow}>
                  <Avatar name={ownerName} seed={p.ownerId} size={20} />
                  {ownerName}
                </span>
              ) : (
                <span className={styles.emptyLine}>Unassigned</span>
              )}
            </dd>

            <dt>Start date</dt>
            <dd>
              <DatePicker
                value={p.startDate}
                ariaLabel="Start date"
                placeholder="Set start date"
                onChange={(iso) => updateProject.mutate({ startDate: iso })}
              />
            </dd>

            <dt>End date</dt>
            <dd>
              <DatePicker
                value={p.endDate}
                ariaLabel="End date"
                placeholder="Set end date"
                onChange={(iso) => updateProject.mutate({ endDate: iso })}
              />
            </dd>

            <dt>Members</dt>
            <dd>
              {members.length > 0 ? (
                <div className={styles.avatarStack}>
                  {members.slice(0, 8).map((m) => (
                    <Avatar key={m.id} name={m.name} seed={m.id} size={24} />
                  ))}
                </div>
              ) : (
                <span className={styles.emptyLine}>No members.</span>
              )}
            </dd>
          </dl>
        </div>
      </section>

      {/* ── Quick stats ──────────────────────────────────────────────── */}
      <section className={styles.card} aria-label="Quick stats">
        <div className={styles.cardHead}>
          <h2 className={styles.cardTitle}>Quick stats</h2>
        </div>
        <div className={styles.stats}>
          <div className={styles.stat}>
            <div className={styles.statValue}>
              {complete}
              <span className={styles.statOf}>/{total}</span>
            </div>
            <div className={styles.statLabel}>Tasks complete</div>
            <div className={styles.progressTrack} aria-hidden="true">
              <div className={styles.progressFill} style={{ width: `${pct}%` }} />
            </div>
            <div className={styles.statSub}>{pct}% done</div>
          </div>
          <div className={styles.stat}>
            <div
              className={`${styles.statValue} ${overdue > 0 ? styles.statValueAlert : ""}`}
            >
              {overdue}
            </div>
            <div className={styles.statLabel}>Overdue</div>
          </div>
        </div>
      </section>

      <StatusComposer
        open={composerOpen}
        onClose={() => setComposerOpen(false)}
        projectId={projectId}
        initialStatus={p.currentStatus}
      />
    </div>
  );
}

function HistoryItem({
  update,
  authorName,
}: {
  update: StatusUpdate;
  authorName: string;
}) {
  const def = statusDef(update.status);
  const visual = def ?? STATUS_NONE;
  const when = update.createdAt
    ? `${format(new Date(update.createdAt), "MMM d, yyyy")} · ${relativeTime(update.createdAt)}`
    : "";
  return (
    <article className={styles.historyItem}>
      <span
        className={styles.historyDot}
        style={{ background: visual.bold }}
        aria-hidden="true"
      />
      <div className={styles.historyBody}>
        <div className={styles.historyTop}>
          <StatusChip status={update.status} size="sm" ignoreStale />
          <span className={styles.historyTitle}>{update.title}</span>
        </div>
        {update.body && <div className={styles.historyText}>{linkify(update.body)}</div>}
        <div className={styles.historyMeta}>
          <Avatar name={authorName} seed={update.authorId ?? authorName} size={20} />
          <span>{authorName}</span>
          <span className={styles.historyWhen}>{when}</span>
        </div>
      </div>
    </article>
  );
}
