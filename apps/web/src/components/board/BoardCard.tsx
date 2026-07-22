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
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = sortable;

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

  return (
    <div
      ref={overlay ? undefined : setNodeRef}
      style={style}
      className={`${styles.card} ${overlay ? styles.cardDragging : ""}`}
      {...(overlay ? {} : attributes)}
      {...(overlay ? {} : listeners)}
      onClick={overlay ? undefined : () => onOpenPeek(id)}
      role={overlay ? undefined : "button"}
      tabIndex={overlay ? undefined : 0}
      aria-label={overlay ? undefined : `Open ${task.title}`}
      onKeyDown={
        overlay
          ? undefined
          : (e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                onOpenPeek(id);
              }
            }
      }
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
        <span className={styles.cardName}>{task.title}</span>
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
