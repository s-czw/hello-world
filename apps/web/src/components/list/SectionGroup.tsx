"use client";

import { useDroppable } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { TaskRow } from "./TaskRow";
import { QuickAddRow } from "./QuickAddRow";
import type { Section, Task, TaskPatch } from "@/lib/tasks";
import styles from "./list.module.css";

export interface SectionGroupProps {
  sectionKey: string;
  name: string;
  incompleteIds: string[];
  completedTasks: Task[];
  taskMap: Map<string, Task>;
  sections: Section[];
  collapsed: boolean;
  onToggleCollapse: () => void;
  completedExpanded: boolean;
  onToggleCompleted: () => void;
  selectedId: string | null;
  onOpenPeek: (id: string) => void;
  onSelect: (id: string) => void;
  onUpdate: (id: string, patch: TaskPatch) => void;
  onComplete: (id: string, completed: boolean) => void;
  onMoveToSection: (id: string, sectionId: string | null) => void;
  onMoveWithin: (id: string, dir: -1 | 1) => void;
  onDelete: (id: string) => void;
  onQuickAdd: (title: string) => void;
}

export function SectionGroup(props: SectionGroupProps) {
  const {
    sectionKey,
    name,
    incompleteIds,
    completedTasks,
    taskMap,
    sections,
    collapsed,
    onToggleCollapse,
    completedExpanded,
    onToggleCompleted,
    selectedId,
    onQuickAdd,
  } = props;

  const { setNodeRef, isOver } = useDroppable({ id: sectionKey });
  const total = incompleteIds.length + completedTasks.length;

  const rowHandlers = {
    onOpenPeek: props.onOpenPeek,
    onSelect: props.onSelect,
    onUpdate: props.onUpdate,
    onComplete: props.onComplete,
    onMoveToSection: props.onMoveToSection,
    onMoveWithin: props.onMoveWithin,
    onDelete: props.onDelete,
  };

  return (
    <div className={styles.section}>
      <div className={styles.sectionHead}>
        <button
          type="button"
          className={styles.sectionToggle}
          aria-expanded={!collapsed}
          onClick={onToggleCollapse}
        >
          <span
            className={[styles.caret, collapsed ? styles.caretCollapsed : ""]
              .filter(Boolean)
              .join(" ")}
            aria-hidden="true"
          >
            ▾
          </span>
          {name}
          <span className={styles.sectionCount}>{total}</span>
        </button>
      </div>

      {!collapsed && (
        <div
          ref={setNodeRef}
          className={isOver ? styles.sectionDropOver : undefined}
        >
          <SortableContext items={incompleteIds} strategy={verticalListSortingStrategy}>
            {incompleteIds.map((id) => {
              const t = taskMap.get(id);
              if (!t) return null;
              return (
                <TaskRow
                  key={id}
                  task={t}
                  sections={sections}
                  selected={selectedId === id}
                  {...rowHandlers}
                />
              );
            })}
          </SortableContext>

          {/* empty-section drop affordance */}
          {incompleteIds.length === 0 && <div className={styles.sectionDrop} />}

          <QuickAddRow onAdd={onQuickAdd} />

          {completedTasks.length > 0 && (
            <>
              <button
                type="button"
                className={styles.completedToggle}
                aria-expanded={completedExpanded}
                onClick={onToggleCompleted}
              >
                <span
                  className={[
                    styles.caret,
                    completedExpanded ? "" : styles.caretCollapsed,
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  aria-hidden="true"
                >
                  ▾
                </span>
                Completed ({completedTasks.length})
              </button>
              {completedExpanded &&
                completedTasks.map((t) => (
                  <TaskRow
                    key={t.id}
                    task={t}
                    sections={sections}
                    selected={selectedId === t.id}
                    draggable={false}
                    {...rowHandlers}
                  />
                ))}
            </>
          )}
        </div>
      )}
    </div>
  );
}
