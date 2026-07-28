"use client";

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Avatar } from "@/components/ui/Avatar";
import { CompleteToggle } from "@/components/task/CompleteToggle";
import { PriorityFlag } from "@/components/task/PriorityFlag";
import { formatDue, isOverdue } from "@/lib/dates";
import { priorityDef, type Task } from "@/lib/tasks";
import styles from "./board.module.css";

export interface BoardCardProps {
  task: Task;
  userName: (id: string | undefined) => string | undefined;
  onOpenPeek: (id: string) => void;
  onComplete: (id: string, completed: boolean) => void;
  /** Rendered inside the DragOverlay — no sortable wiring, no interactions. */
  overlay?: boolean;
}

export function BoardCard({
  task,
  userName,
  onOpenPeek,
  onComplete,
  overlay = false,
}: BoardCardProps) {
  const id = task.id ?? "";
  const sortable = useSortable({ id, disabled: overlay });
  // `attributes` is deliberately NOT spread onto the card: it stamps role="button"
  // + tabIndex + aria-roledescription on a container that holds its own buttons
  // (complete toggle, task name), which axe flags as `nested-interactive` (WCAG
  // 4.1.2). The board uses the PointerSensor only, so dnd-kit's keyboard
  // attributes buy nothing; keyboard users reach the card through its name button
  // and move cards with the list view's "Move to…" menu (WCAG 2.5.7 drag
  // alternative).
  const { listeners, setNodeRef, transform, transition, isDragging } = sortable;

  const style = overlay
    ? undefined
    : {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : undefined,
      };

  const assignee = userName(task.assigneeId);
  const overdue = !task.completed && isOverdue(task.dueDate);
  const hasPriority = priorityDef(task.priority).value !== "none";

  // Activation sits on the card so a click anywhere opens the peek; the name
  // button below is the focusable, screen-reader-visible control and bubbles its
  // click (and Enter/Space) up into that same handler.
  return (
    <div
      ref={overlay ? undefined : setNodeRef}
      style={style}
      className={`${styles.card} ${overlay ? styles.cardDragging : ""}`}
      {...(overlay ? {} : listeners)}
      onClick={overlay ? undefined : () => onOpenPeek(id)}
    >
      <div className={styles.cardTop}>
        <span
          className={styles.cardComplete}
          onPointerDown={(e) => e.stopPropagation()}
          onClick={(e) => e.stopPropagation()}
        >
          <CompleteToggle
            taskId={id}
            completed={!!task.completed}
            onChange={(c) => onComplete(id, c)}
            size={16}
          />
        </span>
        {overlay ? (
          <span className={styles.cardName}>{task.title}</span>
        ) : (
          <button type="button" className={styles.cardName} aria-label={`Open ${task.title}`}>
            {task.title}
          </button>
        )}
      </div>

      {(task.dueDate || hasPriority || assignee) && (
        <div className={styles.cardMeta}>
          {task.dueDate && (
            <span
              className={`${styles.dueChip} ${overdue ? styles.dueChipOverdue : ""}`}
            >
              {formatDue(task.dueDate)}
            </span>
          )}
          {hasPriority && <PriorityFlag priority={task.priority} />}
          <span className={styles.cardSpacer} />
          {assignee && <Avatar name={assignee} seed={task.assigneeId} size={20} />}
        </div>
      )}
    </div>
  );
}
