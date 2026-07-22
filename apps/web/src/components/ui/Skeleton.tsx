import styles from "./ui.module.css";

export interface SkeletonProps {
  width?: number | string;
  height?: number | string;
  radius?: number | string;
  className?: string;
}

export function Skeleton({
  width = "100%",
  height = 16,
  radius,
  className,
}: SkeletonProps) {
  return (
    <span
      aria-hidden="true"
      className={[styles.skeleton, className ?? ""].filter(Boolean).join(" ")}
      style={{
        display: "block",
        width,
        height,
        borderRadius: radius,
      }}
    />
  );
}
