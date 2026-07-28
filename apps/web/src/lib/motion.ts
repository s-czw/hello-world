/**
 * Motion — docs/03 §5. M1 ships exactly two JS moments, both behind the single
 * `motionOK()` reduced-motion gate:
 *   1. task-complete: circle scale + SVG check draw (+ CSS row restyle)
 *   2. drawer (side peek) slide open/close
 * Everything else is CSS or nothing. Under `prefers-reduced-motion: reduce`
 * durations collapse to 0 and state is applied instantly.
 */
import { animate, utils } from "animejs";

export function motionOK(): boolean {
  if (typeof window === "undefined") return false;
  if (typeof window.matchMedia !== "function") return true;
  return !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/**
 * Moment 1 — task complete. Scales the circle and draws the check (the path has
 * `pathLength="1"`, so strokeDashoffset 1→0 draws it). Resolves when the draw
 * finishes so the caller can then commit + collapse the row. Reduced motion:
 * resolves immediately (the completed style still applies via CSS).
 */
export function playTaskComplete(circle: Element, check: SVGPathElement): Promise<void> {
  if (!motionOK()) return Promise.resolve();
  return new Promise<void>((resolve) => {
    animate(circle, {
      scale: [0.6, 1],
      duration: 180,
      ease: "outBack",
    });
    animate(check, {
      strokeDashoffset: [1, 0],
      duration: 220,
      delay: 60,
      ease: "outQuad",
      onComplete: () => resolve(),
    });
  });
}

/** Moment 2 — drawer open. Slides the panel in from the right + fades content. */
export function playDrawerOpen(panel: HTMLElement, body: HTMLElement | null) {
  if (!motionOK()) {
    utils.set(panel, { x: "0%" });
    if (body) utils.set(body, { opacity: 1 });
    return;
  }
  animate(panel, { x: ["100%", "0%"], duration: 260, ease: "outExpo" });
  if (body) animate(body, { opacity: [0, 1], duration: 160, delay: 80, ease: "outQuad" });
}

/** Moment 2 — drawer close. Slides out; `onDone` unmounts the panel. */
export function playDrawerClose(panel: HTMLElement, onDone: () => void) {
  if (!motionOK()) {
    onDone();
    return;
  }
  animate(panel, {
    x: ["0%", "100%"],
    duration: 200,
    ease: "inQuad",
    onComplete: onDone,
  });
}

/** Peek-swap crossfade when ↑/↓ retargets the open task (docs §4.c). */
export function playPeekSwap(body: HTMLElement) {
  if (!motionOK()) return;
  animate(body, { opacity: [0, 1], duration: 120, ease: "linear" });
}
