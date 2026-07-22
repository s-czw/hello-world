import styles from "./ui.module.css";

export interface ChipProps {
  children: React.ReactNode;
  onRemove?: () => void;
  removeLabel?: string;
  style?: React.CSSProperties;
  className?: string;
}

export function Chip({
  children,
  onRemove,
  removeLabel = "Remove",
  style,
  className,
}: ChipProps) {
  return (
    <span
      className={[styles.chip, className ?? ""].filter(Boolean).join(" ")}
      style={style}
    >
      {children}
      {onRemove && (
        <button
          type="button"
          className={styles.chipRemove}
          onClick={onRemove}
          aria-label={removeLabel}
        >
          ✕
        </button>
      )}
    </span>
  );
}
