"use client";

import { forwardRef, useId } from "react";
import styles from "./ui.module.css";

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  modal?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, hint, modal = false, id, className, ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const errorId = `${inputId}-error`;
  const hintId = `${inputId}-hint`;
  const describedBy =
    [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(" ") ||
    undefined;

  return (
    <div className={styles.field}>
      {label && (
        <label className={styles.label} htmlFor={inputId}>
          {label}
        </label>
      )}
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={[
          styles.control,
          modal ? styles.controlModal : "",
          error ? styles.controlError : "",
          className ?? "",
        ]
          .filter(Boolean)
          .join(" ")}
        {...rest}
      />
      {hint && !error && (
        <span className={styles.hint} id={hintId}>
          {hint}
        </span>
      )}
      {error && (
        <span className={styles.errorText} id={errorId} role="alert">
          <span aria-hidden="true">⚠</span>
          {error}
        </span>
      )}
    </div>
  );
});
