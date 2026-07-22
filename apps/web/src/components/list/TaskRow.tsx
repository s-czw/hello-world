"use client";

import { useEffect, useRef, useState } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { CompleteToggle } from "@/components/task/CompleteToggle";
import { AssigneePicker } from "@/components/task/AssigneePicker";
import { PriorityPicker } from "@/components/task/PriorityPicker";
import { DatePicker } from "@/components/ui/DatePicker";
import { RowMenu } from "./RowMenu";
import { isOverdue } from "@/lib/dates";
import type { Priority, Section, Task, TaskPatch } from "@/lib/tasks";
import styles from "./list.module.css";

export interface TaskRowProps {
  task: Task;
  sections: Section[];
  selected: boolean;
  draggable?: boolean;
  onOpenPeek: (id: string) => void;
  onSelect: (id: string) => void;
  onUpdate: (id: string, patch: TaskPatch) => void;
  onComplete: (id: string, completed: boolean) => void;
  onMoveToSection: (id: string, sectionId: string | null) => void;
  onMoveWithin: (id: string, dir: -1 | 1) => void;
  onDelete: (id: string) => void;
}

export function TaskRow({
  task,
  sections,
  selected,
  draggable = true,
  onOpenPeek,
  onSelect,
  onUpdate,
  onComplete,
  onMoveToSection,
  onMoveWithin,
  onDelete,
}: TaskRowProps) {
  const id = task.id ?? "";
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id, disabled: !draggable });
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(task.title ?? "");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (editing) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [editing]);

  function commitName() {
    const t = draft.trim();
    setEditing(false);
    if (t === "" || t === (task.title ?? "")) {
      setDraft(task.title ?? "");
      return;
    }
    onUpdate(id, { title: t });
  }

  const overdue = !task.completed && isOverdue(task.dueDate);

  return (
    <div
      ref={setNodeRef}
      data-row-id={id}
      className={[
        styles.row,
        selected ? styles.rowSelected : "",
        task.completed ? styles.rowCompleted : "",
        isDragging ? styles.rowDragging : "",
      ]
        .filter(Boolean)
        .join(" ")}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      onClick={() => onSelect(id)}
    >
      <div className={`${styles.cell} ${styles.cellComplete}`}>
        <CompleteToggle
          taskId={id}
          completed={!!task.completed}
          onChange={(c) => onComplete(id, c)}
        />
      </div>

      <div className={`${styles.cell} ${styles.nameCell}`}>
        {draggable ? (
          <button
            className={styles.dragHandle}
            aria-label="Drag to reorder"
            {...attributes}
            {...listeners}
            tabIndex={-1}
          >
            ⠿
          </button>
        ) : (
          <span className={styles.dragHandle} aria-hidden="true" />
        )}
        {editing ? (
          <input
            ref={inputRef}
            className={styles.nameInput}
            value={draft}
            aria-label="Task name"
            onChange={(e) => setDraft(e.target.value)}
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                commitName();
              } else if (e.key === "Escape") {
                e.preventDefault();
                setDraft(task.title ?? "");
                setEditing(false);
              }
            }}
            onBlur={commitName}
          />
        ) : (
          <span
            className={styles.name}
            role="button"
            tabIndex={-1}
            title={task.title}
            onClick={(e) => {
              e.stopPropagation();
              onOpenPeek(id);
            }}
            onDoubleClick={(e) => {
              e.stopPropagation();
              setDraft(task.title ?? "");
              setEditing(true);
            }}
          >
            {task.title}
          </span>
        )}
      </div>

      <div className={styles.cell} onClick={(e) => e.stopPropagation()}>
        <AssigneePicker
          variant="cell"
          value={task.assigneeId}
          onChange={(assigneeId) => onUpdate(id, { assigneeId })}
        />
      </div>

      <div
        className={`${styles.cell} ${overdue ? styles.dueOverdue : ""}`}
        onClick={(e) => e.stopPropagation()}
      >
        <DatePicker
          variant="cell"
          value={task.dueDate}
          ariaLabel="Due date"
          onChange={(dueDate) => onUpdate(id, { dueDate })}
        />
      </div>

      <div className={styles.cell} onClick={(e) => e.stopPropagation()}>
        <PriorityPicker
          variant="cell"
          value={task.priority}
          onChange={(p: Priority) => onUpdate(id, { priority: p })}
        />
      </div>

      <div className={styles.cell} onClick={(e) => e.stopPropagation()}>
        <div className={styles.rowMenuWrap}>
          <RowMenu
            sections={sections}
            currentSectionId={task.sectionId}
            onMoveToSection={(sid) => onMoveToSection(id, sid)}
            onMoveUp={() => onMoveWithin(id, -1)}
            onMoveDown={() => onMoveWithin(id, 1)}
            onDelete={() => onDelete(id)}
          />
        </div>
      </div>
    </div>
  );
}
