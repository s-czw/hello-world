"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { playDrawerClose, playDrawerOpen } from "@/lib/motion";
import styles from "./ui.module.css";

export interface DrawerProps {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
  ariaLabel?: string;
  /** Element focus returns to when the drawer closes (the originating row). */
  returnFocusRef?: React.RefObject<HTMLElement | null>;
}

/**
 * Right-anchored side peek — fixed 520px, full height under the topbar, slides
 * in/out (motion moment 2). Soft focus-trap: the list behind stays clickable
 * (no blocking backdrop), Esc closes, focus moves in on open and back to the
 * originating row on close. Becomes a full-width sheet < 720px (docs/03 §6.8).
 */
export function Drawer({
  open,
  onClose,
  children,
  ariaLabel = "Task detail",
  returnFocusRef,
}: DrawerProps) {
  const [render, setRender] = useState(open);
  const panelRef = useRef<HTMLDivElement>(null);
  const innerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) setRender(true);
  }, [open]);

  useLayoutEffect(() => {
    if (render && open && panelRef.current) {
      playDrawerOpen(panelRef.current, innerRef.current);
      panelRef.current.focus({ preventScroll: true });
    }
  }, [render, open]);

  useEffect(() => {
    if (!open && render && panelRef.current) {
      playDrawerClose(panelRef.current, () => {
        setRender(false);
        returnFocusRef?.current?.focus?.();
      });
    }
  }, [open, render, returnFocusRef]);

  useEffect(() => {
    if (!render) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [render, onClose]);

  if (!render) return null;

  return createPortal(
    <div
      ref={panelRef}
      className={styles.drawer}
      role="dialog"
      aria-modal="false"
      aria-label={ariaLabel}
      tabIndex={-1}
    >
      <div ref={innerRef} className={styles.drawerInner}>
        {children}
      </div>
    </div>,
    document.body,
  );
}
