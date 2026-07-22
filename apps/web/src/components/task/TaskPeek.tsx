"use client";

import { useEffect, useRef, useState } from "react";
import { useToast } from "@/components/ui/Toast";
import { playPeekSwap } from "@/lib/motion";
import { linkify } from "@/lib/linkify";
import {
  useTaskMutations,
  type Priority,
  type Section,
  type Task,
} from "@/lib/tasks";
import { CompleteToggle } from "./CompleteToggle";
import { AssigneePicker } from "./AssigneePicker";
import { PriorityPicker } from "./PriorityPicker";
import { SectionPicker } from "./SectionPicker";
import { DatePicker } from "@/components/ui/DatePicker";
import uiStyles from "@/components/ui/ui.module.css";
import styles from "./task.module.css";

export interface TaskPeekProps {
  task: Task;
  sections: Section[];
  projectId: string;
  onClose: () => void;
}

/** Task detail side-peek content (docs/03 §4.c, M1 subset — no subtasks / comments / attachments). */
export function TaskPeek({ task, sections, projectId, onClose }: TaskPeekProps) {
  const { updateTask, moveTask, deleteTask } = useTaskMutations(projectId);
  const toast = useToast();
  const bodyRef = useRef<HTMLDivElement>(null);
  const idRef = useRef<string | undefined>(task.id);

  const [title, setTitle] = useState(task.title ?? "");
  const [desc, setDesc] = useState(task.description ?? "");
  const [editingDesc, setEditingDesc] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const taskId = task.id ?? "";

  // Swap content when ↑/↓ retargets the peek to another task.
  useEffect(() => {
    if (idRef.current !== task.id) {
      idRef.current = task.id;
      setTitle(task.title ?? "");
      setDesc(task.description ?? "");
      setEditingDesc(false);
      setMenuOpen(false);
      if (bodyRef.current) playPeekSwap(bodyRef.current);
    }
  }, [task]);

  function commitTitle() {
    const t = title.trim();
    if (t === "" || t === (task.title ?? "")) {
      setTitle(task.title ?? "");
      return;
    }
    updateTask.mutate({ id: taskId, patch: { title: t } });
  }

  function commitDesc() {
    setEditingDesc(false);
    if (desc === (task.description ?? "")) return;
    updateTask.mutate({ id: taskId, patch: { description: desc } });
  }

  return (
    <div className={styles.peek}>
      <div className={styles.peekHeader}>
        <CompleteToggle
          taskId={taskId}
          completed={!!task.completed}
          onChange={(c) => updateTask.mutate({ id: taskId, patch: { completed: c } })}
        />
        <span className={styles.peekCompleteLabel}>
          {task.completed ? "Completed" : "Mark complete"}
        </span>
        <div className={styles.peekActions}>
          <button
            type="button"
            className={uiStyles.iconBtn}
            aria-label="Copy link to task"
            title="Copy link"
            onClick={() => {
              navigator.clipboard
                ?.writeText(window.location.href)
                .then(() => toast.success("Link copied"))
                .catch(() => toast.error("Couldn't copy the link"));
            }}
          >
            🔗
          </button>
          <button
            type="button"
            className={uiStyles.iconBtn}
            aria-label="More actions"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((o) => !o)}
          >
            ⋯
          </button>
          {menuOpen && (
            <div className={styles.menu} role="menu">
              <button
                type="button"
                role="menuitem"
                className={`${styles.menuItem} ${styles.menuItemDanger}`}
                onClick={() => {
                  setMenuOpen(false);
                  if (confirm("Delete this task? This cannot be undone.")) {
                    deleteTask.mutate(taskId);
                    onClose();
                  }
                }}
              >
                Delete task
              </button>
            </div>
          )}
          <button
            type="button"
            className={uiStyles.iconBtn}
            aria-label="Close"
            onClick={onClose}
          >
            ✕
          </button>
        </div>
      </div>

      <div className={styles.peekScroll} ref={bodyRef}>
        <textarea
          className={`${styles.peekTitle} ${task.completed ? styles.peekTitleDone : ""}`}
          value={title}
          rows={1}
          aria-label="Task title"
          onChange={(e) => {
            setTitle(e.target.value);
            e.target.style.height = "auto";
            e.target.style.height = `${e.target.scrollHeight}px`;
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              (e.target as HTMLTextAreaElement).blur();
            }
          }}
          onBlur={commitTitle}
        />

        <div className={styles.peekGrid}>
          <span className={styles.peekLabel} id={`lbl-assignee-${taskId}`}>
            Assignee
          </span>
          <AssigneePicker
            value={task.assigneeId}
            onChange={(id) => updateTask.mutate({ id: taskId, patch: { assigneeId: id } })}
          />

          <span className={styles.peekLabel}>Due date</span>
          <DatePicker
            value={task.dueDate}
            onChange={(iso) => updateTask.mutate({ id: taskId, patch: { dueDate: iso } })}
          />

          <span className={styles.peekLabel}>Priority</span>
          <PriorityPicker
            value={task.priority}
            onChange={(p: Priority) => updateTask.mutate({ id: taskId, patch: { priority: p } })}
          />

          <span className={styles.peekLabel}>Section</span>
          <SectionPicker
            value={task.sectionId}
            sections={sections}
            onChange={(sid) => moveTask.mutate({ id: taskId, sectionId: sid })}
          />
        </div>

        <div>
          <div className={styles.peekSectionTitle}>Description</div>
          {editingDesc ? (
            <textarea
              className={styles.peekDesc}
              value={desc}
              autoFocus
              aria-label="Description"
              placeholder="Add a description…"
              onChange={(e) => setDesc(e.target.value)}
              onBlur={commitDesc}
            />
          ) : (
            <div
              className={styles.descDisplay}
              role="button"
              tabIndex={0}
              onClick={() => setEditingDesc(true)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  setEditingDesc(true);
                }
              }}
            >
              {desc ? (
                linkify(desc)
              ) : (
                <span className={styles.descPlaceholder}>Add a description…</span>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
