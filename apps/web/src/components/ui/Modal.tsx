"use client";

import { useEffect, useRef } from "react";
import styles from "./ui.module.css";

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  wide?: boolean;
}

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])';

export function Modal({
  open,
  onClose,
  title,
  children,
  footer,
  wide = false,
}: ModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const lastActive = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;
    lastActive.current = document.activeElement as HTMLElement | null;
    // Move focus to the dialog title (docs/03 §6.2).
    titleRef.current?.focus();
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prevOverflow;
      lastActive.current?.focus?.();
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key === "Tab") {
        const nodes = modalRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE);
        if (!nodes || nodes.length === 0) return;
        const list = Array.from(nodes).filter((n) => n.offsetParent !== null);
        if (list.length === 0) return;
        const first = list[0];
        const last = list[list.length - 1];
        const active = document.activeElement as HTMLElement;
        if (e.shiftKey && (active === first || active === titleRef.current)) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && active === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className={styles.backdrop}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={modalRef}
        className={[styles.modal, wide ? styles.modalWide : ""]
          .filter(Boolean)
          .join(" ")}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
      >
        <div className={styles.modalHeader}>
          <h2
            id="modal-title"
            ref={titleRef}
            tabIndex={-1}
            className={styles.modalTitle}
          >
            {title}
          </h2>
          <button
            type="button"
            className={styles.iconBtn}
            onClick={onClose}
            aria-label="Close"
          >
            ✕
          </button>
        </div>
        <div className={styles.modalBody}>{children}</div>
        {footer && <div className={styles.modalFooter}>{footer}</div>}
      </div>
    </div>
  );
}
