"use client";

import { useRef, useState } from "react";
import { Popover } from "@/components/ui/Popover";
import { PRIORITIES, priorityDef, type Priority } from "@/lib/tasks";
import { PriorityFlag } from "./PriorityFlag";
import uiStyles from "@/components/ui/ui.module.css";
import styles from "./task.module.css";

export interface PriorityPickerProps {
  value: string | undefined;
  onChange: (priority: Priority) => void;
  variant?: "cell" | "field";
}

export function PriorityPicker({
  value,
  onChange,
  variant = "field",
}: PriorityPickerProps) {
  const anchorRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const def = priorityDef(value);

  return (
    <>
      <button
        ref={anchorRef}
        type="button"
        aria-label={`Priority: ${def.label}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={variant === "cell" ? uiStyles.cellTrigger : uiStyles.fieldTrigger}
        onClick={() => setOpen((o) => !o)}
      >
        <PriorityFlag priority={value} placeholder={variant === "cell"} />
      </button>
      <Popover
        open={open}
        onClose={() => setOpen(false)}
        anchorRef={anchorRef}
        width={168}
        label="Priority"
      >
        <ul className={styles.optionList} role="listbox" aria-label="Priority">
          {PRIORITIES.map((p) => (
            <li key={p.value}>
              <button
                type="button"
                role="option"
                aria-selected={p.value === def.value}
                className={styles.option}
                onClick={() => {
                  onChange(p.value);
                  setOpen(false);
                }}
              >
                <PriorityFlag priority={p.value} />
                {p.value === def.value && (
                  <span className={styles.optionCheck} aria-hidden="true">
                    ✓
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>
      </Popover>
    </>
  );
}
