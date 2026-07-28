"use client";

import { useRef } from "react";
import { playTaskComplete } from "@/lib/motion";
import styles from "./task.module.css";

export interface CompleteToggleProps {
  completed: boolean;
  /** Called with the new completed value (after the draw animation on complete). */
  onChange: (completed: boolean) => void;
  taskId: string;
  size?: number;
}

/**
 * The complete-circle. Completing plays motion moment 1 (circle scale + check
 * draw) then commits; un-completing commits immediately. 24px hit area (a11y
 * §6.3). `data-task-complete` lets the list's `x` shortcut click it.
 */
export function CompleteToggle({
  completed,
  onChange,
  taskId,
  size = 18,
}: CompleteToggleProps) {
  const btnRef = useRef<HTMLButtonElement>(null);
  const busy = useRef(false);

  async function toggle() {
    if (busy.current) return;
    if (!completed) {
      busy.current = true;
      const svg = btnRef.current?.querySelector("svg");
      const circle = svg?.querySelector<SVGCircleElement>("[data-circle]");
      const check = svg?.querySelector<SVGPathElement>("[data-check]");
      if (circle && check) {
        await playTaskComplete(circle, check);
      }
      busy.current = false;
      onChange(true);
    } else {
      onChange(false);
    }
  }

  return (
    <button
      ref={btnRef}
      type="button"
      data-task-complete={taskId}
      data-completed={completed}
      className={styles.completeToggle}
      aria-pressed={completed}
      aria-label={completed ? "Mark incomplete" : "Mark complete"}
      onClick={(e) => {
        e.stopPropagation();
        void toggle();
      }}
    >
      <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
        <circle
          data-circle
          cx="12"
          cy="12"
          r="9"
          className={styles.toggleCircle}
          style={{ transformBox: "fill-box", transformOrigin: "center" }}
        />
        <path
          data-check
          className={styles.toggleCheck}
          pathLength={1}
          d="M8 12.4l2.6 2.6L16 9"
          fill="none"
        />
      </svg>
    </button>
  );
}
