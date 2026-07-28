"use client";

import { useRef, useState } from "react";
import styles from "./list.module.css";

export interface QuickAddRowProps {
  onAdd: (title: string) => void;
  placeholder?: string;
  autoFocus?: boolean;
}

/**
 * Ghost "Add task…" row at the bottom of a section. Enter commits and keeps
 * focus so the next task can be typed immediately (docs/03 §4.a rapid entry).
 */
export function QuickAddRow({
  onAdd,
  placeholder = "Add task…",
  autoFocus = false,
}: QuickAddRowProps) {
  const [val, setVal] = useState("");
  const ref = useRef<HTMLInputElement>(null);

  return (
    <div className={styles.quickAdd}>
      <div className={styles.quickAddIcon} aria-hidden="true">
        +
      </div>
      <div className={styles.quickAddInputCell}>
        <input
          ref={ref}
          className={styles.quickAddInput}
          value={val}
          placeholder={placeholder}
          aria-label="Add a task"
          autoFocus={autoFocus}
          onChange={(e) => setVal(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              const t = val.trim();
              if (t) {
                onAdd(t);
                setVal("");
                requestAnimationFrame(() => ref.current?.focus());
              }
            } else if (e.key === "Escape") {
              setVal("");
              ref.current?.blur();
            }
          }}
        />
      </div>
    </div>
  );
}
