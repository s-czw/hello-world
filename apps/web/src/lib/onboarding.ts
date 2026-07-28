"use client";

import { useCallback, useEffect, useState } from "react";

/**
 * First-run / onboarding state lives entirely in localStorage — no schema
 * (docs/03 §4.i, PD-31). Each flag is a one-way switch: once set it stays set,
 * so the checklist and the just-in-time hints each surface at most until the
 * user completes or dismisses them.
 */
const PREFIX = "cairn.onboarding.";

export const ONBOARDING_KEYS = {
  checklistDismissed: `${PREFIX}checklist-dismissed`,
  hintList: `${PREFIX}hint-list`,
  hintBoard: `${PREFIX}hint-board`,
} as const;

function readFlag(key: string): boolean {
  if (typeof window === "undefined") return false;
  try {
    return window.localStorage.getItem(key) === "1";
  } catch {
    return false;
  }
}

function writeFlag(key: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, "1");
  } catch {
    /* private mode / disabled storage — degrade to non-persistent */
  }
}

/**
 * A persisted one-way boolean flag. `set` flips it to true (persisted) and
 * re-renders. `ready` is false during the first client render so callers can
 * avoid a flash before localStorage has been read (SSR renders nothing).
 */
export function usePersistentFlag(key: string): {
  value: boolean;
  ready: boolean;
  set: () => void;
} {
  const [value, setValue] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setValue(readFlag(key));
    setReady(true);
  }, [key]);

  const set = useCallback(() => {
    writeFlag(key);
    setValue(true);
  }, [key]);

  return { value, ready, set };
}
