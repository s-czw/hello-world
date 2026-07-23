"use client";

import { useRef, useState } from "react";
import { Avatar } from "@/components/ui/Avatar";
import { formatDue } from "@/lib/dates";
import { useUsers, type Task } from "@/lib/tasks";
import { CompleteToggle } from "./CompleteToggle";
import styles from "./task.module.css";

export interface SubtaskListProps {
  parentId: string;
  subtasks: Task[];
  progress: { total: number; completed: number };
  onOpenSubtask: (id: string) => void;
  onToggleSubtask: (id: string, completed: boolean) => void;
  onAddSubtask: (title: string) => void;
}

/**
 * Compact subtask checklist (docs/03 §4.c-5): complete-circle · title · assignee-mini
 * · due-mini per row; clicking the title swaps the peek to that subtask. "+ Add
 * subtask" appends. One level only — subtasks have no subtasks of their own.
 */
export function SubtaskList({
  parentId,
  subtasks,
  progress,
  onOpenSubtask,
  onToggleSubtask,
  onAddSubtask,
}: SubtaskListProps) {
  const { data: users } = useUsers();
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  function assignee(id: string | undefined) {
    return (users ?? []).find((u) => u.id === id);
  }

  function commit() {
    const t = title.trim();
    if (t) {
      onAddSubtask(t);
      setTitle("");
      // keep the row open + focused for rapid entry
      requestAnimationFrame(() => inputRef.current?.focus());
    } else {
      setAdding(false);
    }
  }

  return (
    <section className={styles.block}>
      <div className={styles.blockHead}>
        <div className={styles.peekSectionTitle}>Subtasks</div>
        {progress.total > 0 && (
          <span className={styles.subProgress} aria-label={`${progress.completed} of ${progress.total} complete`}>
            {progress.completed}/{progress.total}
          </span>
        )}
      </div>

      <ul className={styles.subList}>
        {subtasks.map((s) => {
          const sid = s.id ?? "";
          const a = assignee(s.assigneeId);
          const isTemp = sid.startsWith("temp-");
          return (
            <li key={sid} className={styles.subRow} data-completed={!!s.completed}>
              <CompleteToggle
                taskId={sid}
                completed={!!s.completed}
                size={16}
                onChange={(c) => !isTemp && onToggleSubtask(sid, c)}
              />
              <button
                type="button"
                className={styles.subTitle}
                onClick={() => !isTemp && onOpenSubtask(sid)}
                disabled={isTemp}
              >
                {s.title}
              </button>
              {s.dueDate && <span className={styles.subDue}>{formatDue(s.dueDate)}</span>}
              {a ? (
                <Avatar name={a.name ?? "?"} seed={a.id} size={20} />
              ) : (
                <span className={styles.subAvatarSpacer} aria-hidden="true" />
              )}
            </li>
          );
        })}
      </ul>

      {adding ? (
        <input
          ref={inputRef}
          className={styles.subAddInput}
          placeholder="Subtask name…"
          aria-label="New subtask name"
          value={title}
          autoFocus
          onChange={(e) => setTitle(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              commit();
            } else if (e.key === "Escape") {
              e.preventDefault();
              setTitle("");
              setAdding(false);
            }
          }}
          onBlur={commit}
        />
      ) : (
        <button
          type="button"
          className={styles.subAddBtn}
          onClick={() => setAdding(true)}
          data-testid={`add-subtask-${parentId}`}
        >
          + Add subtask
        </button>
      )}
    </section>
  );
}
