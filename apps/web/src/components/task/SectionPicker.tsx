"use client";

import { useRef, useState } from "react";
import { Popover } from "@/components/ui/Popover";
import type { Section } from "@/lib/tasks";
import uiStyles from "@/components/ui/ui.module.css";
import styles from "./task.module.css";

export interface SectionPickerProps {
  value: string | undefined;
  sections: Section[];
  onChange: (sectionId: string | null) => void;
  variant?: "cell" | "field";
}

export function SectionPicker({
  value,
  sections,
  onChange,
  variant = "field",
}: SectionPickerProps) {
  const anchorRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const current = sections.find((s) => s.id === value);

  return (
    <>
      <button
        ref={anchorRef}
        type="button"
        aria-label={`Section: ${current?.name ?? "None"}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={variant === "cell" ? uiStyles.cellTrigger : uiStyles.fieldTrigger}
        onClick={() => setOpen((o) => !o)}
      >
        {current ? (
          current.name
        ) : (
          <span className={uiStyles.pickerPlaceholder}>No section</span>
        )}
      </button>
      <Popover
        open={open}
        onClose={() => setOpen(false)}
        anchorRef={anchorRef}
        width={224}
        label="Section"
      >
        <ul className={styles.optionList} role="listbox" aria-label="Section">
          {sections.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                role="option"
                aria-selected={s.id === value}
                className={styles.option}
                onClick={() => {
                  onChange(s.id ?? null);
                  setOpen(false);
                }}
              >
                <span className={styles.assigneeName}>{s.name}</span>
                {s.id === value && (
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
