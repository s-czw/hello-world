"use client";

import { useRef, useState } from "react";
import {
  addMonths,
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  isSameMonth,
  startOfMonth,
  startOfWeek,
} from "date-fns";
import { Popover } from "./Popover";
import {
  formatDue,
  isoToLocalDate,
  parseTypedDate,
  todayIso,
} from "@/lib/dates";
import styles from "./ui.module.css";

export interface DatePickerProps {
  value?: string | null;
  onChange: (iso: string | null) => void;
  /** cell = borderless inline trigger; field = bordered form control. */
  variant?: "cell" | "field";
  ariaLabel?: string;
  placeholder?: string;
  autoOpen?: boolean;
}

const WEEKDAYS = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];

/**
 * Plain date picker — typed input (plain formats only, NO natural language,
 * docs/03 §2.6 / PD-31) + calendar popover. Clearable. Used both inline in the
 * list Due cell and in the task side-peek (one component, two anchors).
 */
export function DatePicker({
  value,
  onChange,
  variant = "field",
  ariaLabel = "Due date",
  placeholder = "No date",
  autoOpen = false,
}: DatePickerProps) {
  const anchorRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(autoOpen);
  const [text, setText] = useState("");
  const [error, setError] = useState(false);
  const [viewMonth, setViewMonth] = useState<Date>(
    value ? isoToLocalDate(value) : new Date(),
  );

  function openPicker() {
    setText(value ? formatDue(value) : "");
    setError(false);
    setViewMonth(value ? isoToLocalDate(value) : new Date());
    setOpen(true);
  }

  function commitTyped() {
    const raw = text.trim();
    if (raw === "") {
      onChange(null);
      setOpen(false);
      return;
    }
    const iso = parseTypedDate(raw);
    if (!iso) {
      setError(true);
      return;
    }
    onChange(iso);
    setOpen(false);
  }

  function pick(iso: string) {
    onChange(iso);
    setOpen(false);
  }

  const gridStart = startOfWeek(startOfMonth(viewMonth));
  const gridEnd = endOfWeek(endOfMonth(viewMonth));
  const days = eachDayOfInterval({ start: gridStart, end: gridEnd });
  const selected = value ? isoToLocalDate(value) : null;

  return (
    <>
      <button
        ref={anchorRef}
        type="button"
        aria-label={ariaLabel}
        aria-haspopup="dialog"
        aria-expanded={open}
        className={variant === "cell" ? styles.cellTrigger : styles.fieldTrigger}
        onClick={openPicker}
      >
        <span className={value ? undefined : styles.pickerPlaceholder}>
          {value ? formatDue(value) : placeholder}
        </span>
      </button>

      <Popover
        open={open}
        onClose={() => setOpen(false)}
        anchorRef={anchorRef}
        width={264}
        label="Choose a date"
      >
        <div className={styles.datePicker}>
          <input
            className={[styles.control, error ? styles.controlError : ""]
              .filter(Boolean)
              .join(" ")}
            value={text}
            placeholder="e.g. aug 12 or 12/08"
            aria-label="Type a date"
            aria-invalid={error || undefined}
            onChange={(e) => {
              setText(e.target.value);
              setError(false);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                commitTyped();
              }
            }}
          />
          {error && (
            <span className={styles.errorText} role="alert">
              <span aria-hidden="true">⚠</span> Unrecognized date
            </span>
          )}

          <div className={styles.calHeader}>
            <button
              type="button"
              className={styles.iconBtn}
              aria-label="Previous month"
              onClick={() => setViewMonth((m) => addMonths(m, -1))}
            >
              ‹
            </button>
            <span className={styles.calMonth}>{format(viewMonth, "MMMM yyyy")}</span>
            <button
              type="button"
              className={styles.iconBtn}
              aria-label="Next month"
              onClick={() => setViewMonth((m) => addMonths(m, 1))}
            >
              ›
            </button>
          </div>

          <div className={styles.calGrid} role="grid">
            {WEEKDAYS.map((w) => (
              <span key={w} className={styles.calWeekday} aria-hidden="true">
                {w}
              </span>
            ))}
            {days.map((d) => {
              const iso = format(d, "yyyy-MM-dd");
              const isSel = selected ? isSameDay(d, selected) : false;
              const dim = !isSameMonth(d, viewMonth);
              return (
                <button
                  key={iso}
                  type="button"
                  className={[
                    styles.calDay,
                    isSel ? styles.calDaySelected : "",
                    dim ? styles.calDayDim : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  aria-label={format(d, "MMMM d, yyyy")}
                  aria-pressed={isSel}
                  onClick={() => pick(iso)}
                >
                  {format(d, "d")}
                </button>
              );
            })}
          </div>

          <div className={styles.calFooter}>
            <button
              type="button"
              className={styles.linkBtn}
              onClick={() => pick(todayIso())}
            >
              Today
            </button>
            {value && (
              <button
                type="button"
                className={styles.linkBtn}
                onClick={() => {
                  onChange(null);
                  setOpen(false);
                }}
              >
                Clear
              </button>
            )}
          </div>
        </div>
      </Popover>
    </>
  );
}
