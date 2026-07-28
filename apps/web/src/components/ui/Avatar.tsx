import styles from "./ui.module.css";

const HUES = [
  "var(--avatar-0)",
  "var(--avatar-1)",
  "var(--avatar-2)",
  "var(--avatar-3)",
  "var(--avatar-4)",
  "var(--avatar-5)",
  "var(--avatar-6)",
  "var(--avatar-7)",
];

/** Deterministic hue from a stable id (docs/03 §2.3 — 8 hues, initials only). */
function hueFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) {
    h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  }
  return HUES[h % HUES.length];
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export interface AvatarProps {
  name: string;
  /** Stable seed for hue (defaults to name). */
  seed?: string;
  size?: 20 | 24 | 32;
}

export function Avatar({ name, seed, size = 24 }: AvatarProps) {
  const fontSize = size <= 20 ? 9 : size <= 24 ? 11 : 13;
  return (
    <span
      className={styles.avatar}
      style={{
        width: size,
        height: size,
        fontSize,
        background: hueFor(seed ?? name),
      }}
      title={name}
      aria-label={name}
      role="img"
    >
      {initials(name)}
    </span>
  );
}
