"use client";

import { useMemo, useRef, useState } from "react";
import { Popover } from "@/components/ui/Popover";
import { Avatar } from "@/components/ui/Avatar";
import { useUsers, type User } from "@/lib/tasks";
import uiStyles from "@/components/ui/ui.module.css";
import styles from "./task.module.css";

export interface AssigneePickerProps {
  value: string | undefined;
  onChange: (assigneeId: string | null) => void;
  variant?: "cell" | "field";
}

export function AssigneePicker({
  value,
  onChange,
  variant = "field",
}: AssigneePickerProps) {
  const anchorRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const { data: users } = useUsers();

  const current = useMemo(
    () => (users ?? []).find((u) => u.id === value),
    [users, value],
  );

  const filtered = useMemo(() => {
    const active = (users ?? []).filter((u) => u.active !== false);
    const s = q.trim().toLowerCase();
    if (!s) return active;
    return active.filter(
      (u) =>
        (u.name ?? "").toLowerCase().includes(s) ||
        (u.email ?? "").toLowerCase().includes(s),
    );
  }, [users, q]);

  function select(id: string | null) {
    onChange(id);
    setOpen(false);
    setQ("");
  }

  return (
    <>
      <button
        ref={anchorRef}
        type="button"
        aria-label={current ? `Assignee: ${current.name}` : "Assign task"}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={variant === "cell" ? uiStyles.cellTrigger : uiStyles.fieldTrigger}
        onClick={() => setOpen((o) => !o)}
      >
        {current ? (
          <span className={styles.assigneeValue}>
            <Avatar name={current.name ?? "?"} seed={current.id} size={20} />
            <span className={styles.assigneeName}>{current.name}</span>
          </span>
        ) : (
          <span className={uiStyles.pickerPlaceholder}>Unassigned</span>
        )}
      </button>
      <Popover
        open={open}
        onClose={() => setOpen(false)}
        anchorRef={anchorRef}
        width={248}
        label="Assignee"
      >
        <div className={styles.assigneePop}>
          <input
            className={uiStyles.control}
            placeholder="Search people…"
            aria-label="Search people"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <ul className={styles.optionList} role="listbox" aria-label="Assignee">
            <li>
              <button
                type="button"
                role="option"
                aria-selected={!value}
                className={styles.option}
                onClick={() => select(null)}
              >
                <span className={uiStyles.pickerPlaceholder}>Unassigned</span>
                {!value && (
                  <span className={styles.optionCheck} aria-hidden="true">
                    ✓
                  </span>
                )}
              </button>
            </li>
            {filtered.map((u: User) => (
              <li key={u.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={u.id === value}
                  className={styles.option}
                  onClick={() => select(u.id ?? null)}
                >
                  <Avatar name={u.name ?? "?"} seed={u.id} size={20} />
                  <span className={styles.assigneeName}>{u.name}</span>
                  {u.id === value && (
                    <span className={styles.optionCheck} aria-hidden="true">
                      ✓
                    </span>
                  )}
                </button>
              </li>
            ))}
            {filtered.length === 0 && (
              <li className={styles.optionEmpty}>No matches</li>
            )}
          </ul>
        </div>
      </Popover>
    </>
  );
}
