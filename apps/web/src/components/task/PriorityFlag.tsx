import { priorityDef } from "@/lib/tasks";
import styles from "./task.module.css";

export interface PriorityFlagProps {
  priority: string | undefined;
  /** Show a muted "Priority" placeholder when none (inline cells). */
  placeholder?: boolean;
}

/** Flag icon + label (docs/03 §2.3 — none renders no flag). Icon + label, never color-only. */
export function PriorityFlag({ priority, placeholder }: PriorityFlagProps) {
  const def = priorityDef(priority);
  if (def.value === "none") {
    return placeholder ? (
      <span className={styles.priorityPlaceholder}>Priority</span>
    ) : (
      <span className={styles.priorityNone}>None</span>
    );
  }
  return (
    <span className={styles.priorityFlag}>
      <span aria-hidden="true" style={{ color: def.color ?? undefined }}>
        ⚑
      </span>
      {def.label}
    </span>
  );
}
