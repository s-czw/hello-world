"use client";

import { forwardRef, useId } from "react";
import styles from "./ui.module.css";

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps
  extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  options: SelectOption[];
  error?: string;
}

/**
 * Native styled select. Full type-ahead combobox is only needed for
 * assignee/section pickers (list phase); role/status selection uses this.
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  function Select({ label, options, error, id, className, ...rest }, ref) {
    const autoId = useId();
    const selectId = id ?? autoId;
    return (
      <div className={styles.field}>
        {label && (
          <label className={styles.label} htmlFor={selectId}>
            {label}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          aria-invalid={error ? true : undefined}
          className={[
            styles.control,
            error ? styles.controlError : "",
            className ?? "",
          ]
            .filter(Boolean)
            .join(" ")}
          {...rest}
        >
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        {error && (
          <span className={styles.errorText} role="alert">
            <span aria-hidden="true">⚠</span>
            {error}
          </span>
        )}
      </div>
    );
  },
);
