"use client";

import { useRef, useState } from "react";
import { useDroppable } from "@dnd-kit/core";
import {
  SortableContext,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import type { Task } from "@/lib/tasks";
import { BoardCard } from "./BoardCard";
import styles from "./board.module.css";

export interface BoardColumnProps {
  sectionKey: string;
  name: string;
  ids: string[];
  taskMap: Map<string, Task>;
  userName: (id: string | undefined) => string | undefined;
  onOpenPeek: (id: string) => void;
  onComplete: (id: string, completed: boolean) => void;
  onQuickAdd: (title: string) => void;
}

export function BoardColumn({
  sectionKey,
  name,
  ids,
  taskMap,
  userName,
  onOpenPeek,
  onComplete,
  onQuickAdd,
}: BoardColumnProps) {
  const { setNodeRef, isOver } = useDroppable({ id: sectionKey });

  return (
    <section className={styles.column} aria-label={name}>
      <header className={styles.columnHead}>
        <span className={styles.columnName}>{name}</span>
        <span className={styles.columnCount}>{ids.length}</span>
      </header>
      <div
        ref={setNodeRef}
        className={`${styles.well} ${isOver ? styles.wellOver : ""}`}
      >
        <SortableContext items={ids} strategy={verticalListSortingStrategy}>
          {ids.map((id) => {
            const t = taskMap.get(id);
            if (!t) return null;
            return (
              <BoardCard
                key={id}
                task={t}
                userName={userName}
                onOpenPeek={onOpenPeek}
                onComplete={onComplete}
              />
            );
          })}
        </SortableContext>
        <QuickAddCard onAdd={onQuickAdd} />
      </div>
    </section>
  );
}

function QuickAddCard({ onAdd }: { onAdd: (title: string) => void }) {
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const inputRef = useRef<HTMLTextAreaElement>(null);

  function commit() {
    const t = title.trim();
    if (t) {
      onAdd(t);
      setTitle("");
      // Keep the composer open for rapid entry.
      requestAnimationFrame(() => inputRef.current?.focus());
    } else {
      setAdding(false);
    }
  }

  if (!adding) {
    return (
      <button
        type="button"
        className={styles.addCardBtn}
        onClick={() => {
          setAdding(true);
          requestAnimationFrame(() => inputRef.current?.focus());
        }}
      >
        + Add task
      </button>
    );
  }

  return (
    <textarea
      ref={inputRef}
      className={styles.addCardInput}
      value={title}
      rows={2}
      placeholder="Task name"
      aria-label="New task name"
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
  );
}
