"use client";

import { usePersistentFlag } from "@/lib/onboarding";
import styles from "./onboarding.module.css";

/**
 * One-shot just-in-time hint (docs/03 §4.i.2). Shows exactly once per browser —
 * dismissed automatically the first time it renders is NOT the behavior; it
 * stays visible until the user closes it, then never returns (localStorage,
 * PD-31). Renders nothing under SSR / before the flag is read to avoid a flash.
 */
export function JitHint({
  storageKey,
  children,
}: {
  storageKey: string;
  children: React.ReactNode;
}) {
  const { value: dismissed, ready, set } = usePersistentFlag(storageKey);
  if (!ready || dismissed) return null;
  return (
    <div className={styles.hint} role="note">
      <span aria-hidden="true">💡</span>
      <span className={styles.hintText}>{children}</span>
      <button
        type="button"
        className={styles.hintClose}
        aria-label="Dismiss hint"
        onClick={set}
      >
        ✕
      </button>
    </div>
  );
}
