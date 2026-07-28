"use client";

import { useMemo, useState } from "react";
import { Avatar } from "@/components/ui/Avatar";
import { formatDue } from "@/lib/dates";
import { relativeTime } from "@/lib/status";
import { renderCommentBody } from "@/lib/mentions";
import type { User } from "@/lib/tasks";
import type { ActivityEntry } from "@/lib/taskdetail";
import styles from "./task.module.css";

export interface ActivityStreamProps {
  entries: ActivityEntry[];
  users: User[];
  meId: string | undefined;
  isAdmin: boolean;
  sectionNames: Record<string, string>;
  onEditComment: (commentId: string, body: string) => void;
  onDeleteComment: (commentId: string) => void;
}

function userName(id: string | undefined, users: User[]): string {
  const u = users.find((x) => x.id === id);
  return u?.name ?? u?.email ?? "Someone";
}

/** Verb phrase for a system activity entry (actor rendered separately). */
function systemSentence(e: ActivityEntry, users: User[], sectionNames: Record<string, string>): string {
  const diff = e.diff as { from?: string | null; to?: string | null } | undefined;
  switch (e.action) {
    case "task.created":
      return "created this task";
    case "task.completed":
      return "marked this complete";
    case "task.reopened":
      return "marked this incomplete";
    case "task.assignee_changed":
      return diff?.to ? `assigned ${userName(diff.to, users)}` : "unassigned this task";
    case "task.due_changed":
      return diff?.to ? `set the due date to ${formatDue(diff.to)}` : "cleared the due date";
    case "task.section_changed":
      return diff?.to && sectionNames[diff.to]
        ? `moved this to ${sectionNames[diff.to]}`
        : "moved this task";
    default:
      return "updated this task";
  }
}

type Row =
  | { kind: "comment"; entry: ActivityEntry }
  | { kind: "system"; entry: ActivityEntry }
  | { kind: "systemGroup"; groupId: string; entries: ActivityEntry[] };

/** Build display rows: contiguous runs of ≥2 system events collapse into a group. */
function buildRows(entries: ActivityEntry[]): Row[] {
  const rows: Row[] = [];
  let run: ActivityEntry[] = [];
  const flush = () => {
    if (run.length === 0) return;
    if (run.length === 1) rows.push({ kind: "system", entry: run[0] });
    else rows.push({ kind: "systemGroup", groupId: run[0].id ?? "g", entries: run });
    run = [];
  };
  for (const e of entries) {
    if (e.kind === "comment") {
      flush();
      rows.push({ kind: "comment", entry: e });
    } else {
      run.push(e);
    }
  }
  flush();
  return rows;
}

/**
 * Merged comments + system-event stream (docs/03 §4.c-7). Comments show
 * author + relative time + body (mentions + URLs rendered); own comments are
 * editable/deletable (edited marker). Runs of system events collapse behind a
 * "Show N updates" toggle.
 */
export function ActivityStream({
  entries,
  users,
  meId,
  isAdmin,
  sectionNames,
  onEditComment,
  onDeleteComment,
}: ActivityStreamProps) {
  const rows = useMemo(() => buildRows(entries), [entries]);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  function SystemRow({ e }: { e: ActivityEntry }) {
    return (
      <li className={styles.sysRow}>
        <span className={styles.sysDot} aria-hidden="true" />
        <span className={styles.sysText}>
          <span className={styles.sysActor}>{userName(e.actorId, users)}</span>{" "}
          {systemSentence(e, users, sectionNames)}
          <span className={styles.sysTime}> · {relativeTime(e.createdAt)}</span>
        </span>
      </li>
    );
  }

  return (
    <section className={styles.block}>
      <div className={styles.peekSectionTitle}>Activity</div>
      {rows.length === 0 ? (
        <p className={styles.attachEmpty}>No activity yet.</p>
      ) : (
        <ul className={styles.stream}>
          {rows.map((row) => {
            if (row.kind === "system") return <SystemRow key={row.entry.id} e={row.entry} />;

            if (row.kind === "systemGroup") {
              const isOpen = !!expanded[row.groupId];
              return (
                <li key={row.groupId} className={styles.sysGroup}>
                  <button
                    type="button"
                    className={styles.sysToggle}
                    aria-expanded={isOpen}
                    onClick={() => setExpanded((s) => ({ ...s, [row.groupId]: !s[row.groupId] }))}
                  >
                    {isOpen ? "Hide" : "Show"} {row.entries.length} updates
                  </button>
                  {isOpen && (
                    <ul className={styles.sysGroupList}>
                      {row.entries.map((e) => (
                        <SystemRow key={e.id} e={e} />
                      ))}
                    </ul>
                  )}
                </li>
              );
            }

            // comment
            const e = row.entry;
            const cid = e.id ?? "";
            const own = !!meId && e.actorId === meId;
            const isEditing = editingId === cid;
            return (
              <li key={cid} className={styles.commentRow}>
                <Avatar name={userName(e.actorId, users)} seed={e.actorId} size={24} />
                <div className={styles.commentBody}>
                  <div className={styles.commentHead}>
                    <span className={styles.commentAuthor}>{userName(e.actorId, users)}</span>
                    <span className={styles.commentTime}>{relativeTime(e.createdAt)}</span>
                    {e.edited && <span className={styles.commentEdited}>(edited)</span>}
                    {own && !isEditing && (
                      <span className={styles.commentActions}>
                        <button
                          type="button"
                          className={styles.commentAction}
                          onClick={() => {
                            setEditingId(cid);
                            setEditValue(e.body ?? "");
                          }}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className={styles.commentAction}
                          onClick={() => onDeleteComment(cid)}
                        >
                          Delete
                        </button>
                      </span>
                    )}
                    {!own && isAdmin && !isEditing && (
                      <span className={styles.commentActions}>
                        <button
                          type="button"
                          className={styles.commentAction}
                          onClick={() => onDeleteComment(cid)}
                        >
                          Delete
                        </button>
                      </span>
                    )}
                  </div>
                  {isEditing ? (
                    <div className={styles.commentEdit}>
                      <textarea
                        className={styles.composerInput}
                        value={editValue}
                        autoFocus
                        aria-label="Edit comment"
                        onChange={(ev) => setEditValue(ev.target.value)}
                      />
                      <div className={styles.commentEditActions}>
                        <button
                          type="button"
                          className={styles.composerSend}
                          disabled={editValue.trim().length === 0}
                          onClick={() => {
                            const b = editValue.trim();
                            if (b && b !== (e.body ?? "")) onEditComment(cid, b);
                            setEditingId(null);
                          }}
                        >
                          Save
                        </button>
                        <button
                          type="button"
                          className={styles.commentAction}
                          onClick={() => setEditingId(null)}
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className={styles.commentText}>
                      {renderCommentBody(e.body ?? "", users)}
                    </div>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
