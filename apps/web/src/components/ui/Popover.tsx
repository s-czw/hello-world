"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import styles from "./ui.module.css";

export interface PopoverProps {
  open: boolean;
  onClose: () => void;
  /** The element the popover anchors under. Focus returns here on close. */
  anchorRef: React.RefObject<HTMLElement | null>;
  children: React.ReactNode;
  /** Horizontal alignment relative to the anchor. */
  align?: "start" | "end";
  width?: number;
  label?: string;
}

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])';

// Module-level open counter so other surfaces (e.g. the list keyboard nav) can
// yield while any picker popover is open.
let openPopoverCount = 0;
export function anyPopoverOpen(): boolean {
  return openPopoverCount > 0;
}

/**
 * Anchored popover rendered through a portal (so it escapes table/overflow
 * clipping). Outside-click + Esc close; focus moves in on open and returns to
 * the anchor on close. Soft menu — used for the assignee/priority/section/date
 * pickers (docs/03 §2.6).
 */
export function Popover({
  open,
  onClose,
  anchorRef,
  children,
  align = "start",
  width,
  label,
}: PopoverProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const [mounted, setMounted] = useState(false);

  useEffect(() => setMounted(true), []);

  useLayoutEffect(() => {
    if (!open) return;
    function place() {
      const a = anchorRef.current;
      const panel = panelRef.current;
      if (!a || !panel) return;
      const r = a.getBoundingClientRect();
      const w = width ?? panel.offsetWidth;
      const h = panel.offsetHeight;
      let left = align === "end" ? r.right - w : r.left;
      left = Math.max(8, Math.min(left, window.innerWidth - w - 8));
      let top = r.bottom + 4;
      if (top + h > window.innerHeight - 8) {
        top = Math.max(8, r.top - h - 4); // flip above when it would overflow
      }
      setPos({ top, left });
    }
    place();
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", place);
    return () => {
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", place);
    };
  }, [open, anchorRef, align, width]);

  useEffect(() => {
    if (!open) return;
    openPopoverCount++;
    // Focus the first focusable control inside the panel.
    const t = window.setTimeout(() => {
      const panel = panelRef.current;
      const first = panel?.querySelector<HTMLElement>(FOCUSABLE);
      (first ?? panel)?.focus();
    }, 0);
    function onDown(e: MouseEvent) {
      const panel = panelRef.current;
      if (
        panel &&
        !panel.contains(e.target as Node) &&
        !anchorRef.current?.contains(e.target as Node)
      ) {
        onClose();
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation();
        onClose();
        anchorRef.current?.focus();
      }
    }
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey, true);
    return () => {
      openPopoverCount = Math.max(0, openPopoverCount - 1);
      window.clearTimeout(t);
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey, true);
    };
  }, [open, onClose, anchorRef]);

  if (!open || !mounted) return null;

  return createPortal(
    <div
      ref={panelRef}
      className={styles.popover}
      role="dialog"
      aria-label={label}
      style={{
        top: pos?.top ?? -9999,
        left: pos?.left ?? -9999,
        width,
        visibility: pos ? "visible" : "hidden",
      }}
    >
      {children}
    </div>,
    document.body,
  );
}
