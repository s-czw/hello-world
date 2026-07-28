"use client";

import { forwardRef } from "react";
import styles from "./ui.module.css";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "default" | "compact" | "dialog";

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  block?: boolean;
}

const variantClass: Record<Variant, string> = {
  primary: styles.btnPrimary,
  secondary: styles.btnSecondary,
  ghost: styles.btnGhost,
  danger: styles.btnDanger,
};

const sizeClass: Record<Size, string> = {
  default: "",
  compact: styles.btnCompact,
  dialog: styles.btnDialog,
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  function Button(
    {
      variant = "primary",
      size = "default",
      loading = false,
      block = false,
      disabled,
      children,
      className,
      type = "button",
      ...rest
    },
    ref,
  ) {
    return (
      <button
        ref={ref}
        type={type}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        className={[
          styles.btn,
          variantClass[variant],
          sizeClass[size],
          block ? styles.btnBlock : "",
          className ?? "",
        ]
          .filter(Boolean)
          .join(" ")}
        {...rest}
      >
        {loading && <span className={styles.spinner} aria-hidden="true" />}
        {!loading && children}
      </button>
    );
  },
);
