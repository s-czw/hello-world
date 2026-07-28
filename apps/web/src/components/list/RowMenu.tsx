"use client";

import { useEffect, useRef, useState } from "react";
import type { Section } from "@/lib/tasks";
import listStyles from "./list.module.css";
import styles from "@/components/task/task.module.css";

export interface RowMenuProps {
  sections: Section[];
  currentSectionId: string | undefined;
  onMoveToSection: (sectionId: string | null) => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onDelete: () => void;
}

/**
 * Row ⋯ menu — the non-drag "Move to section / position…" path required for
 * WCAG 2.5.7 (docs/03 §6.4), plus delete.
 */
export function RowMenu({
  sections,
  currentSectionId,
  onMoveToSection,
  onMoveUp,
  onMoveDown,
  onDelete,
}: RowMenuProps) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={wrapRef} style={{ display: "contents" }}>
      <button
        type="button"
        className={[listStyles.rowMenuBtn, open ? listStyles.rowMenuBtnOpen : ""]
          .filter(Boolean)
          .join(" ")}
        aria-label="Task actions"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={(e) => {
          e.stopPropagation();
          setOpen((o) => !o);
        }}
      >
        ⋯
      </button>
      {open && (
        <div className={styles.menu} role="menu" onClick={(e) => e.stopPropagation()}>
          <button
            type="button"
            role="menuitem"
            className={styles.menuItem}
            onClick={() => {
              onMoveUp();
              setOpen(false);
            }}
          >
            Move up
          </button>
          <button
            type="button"
            role="menuitem"
            className={styles.menuItem}
            onClick={() => {
              onMoveDown();
              setOpen(false);
            }}
          >
            Move down
          </button>
          {sections.length > 0 && (
            <>
              <div className={styles.menuSep} />
              <div className={styles.menuLabel}>Move to section</div>
              {sections.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  role="menuitem"
                  className={styles.menuItem}
                  disabled={s.id === currentSectionId}
                  onClick={() => {
                    onMoveToSection(s.id ?? null);
                    setOpen(false);
                  }}
                >
                  {s.name}
                  {s.id === currentSectionId && (
                    <span className={styles.optionCheck} aria-hidden="true">
                      ✓
                    </span>
                  )}
                </button>
              ))}
            </>
          )}
          <div className={styles.menuSep} />
          <button
            type="button"
            role="menuitem"
            className={`${styles.menuItem} ${styles.menuItemDanger}`}
            onClick={() => {
              setOpen(false);
              if (confirm("Delete this task? This cannot be undone.")) {
                onDelete();
              }
            }}
          >
            Delete task
          </button>
        </div>
      )}
    </div>
  );
}
