import styles from "./ui.module.css";

export interface EmptyStateProps {
  icon?: React.ReactNode;
  headline: string;
  body?: string;
  action?: React.ReactNode;
}

export function EmptyState({ icon, headline, body, action }: EmptyStateProps) {
  return (
    <div className={styles.empty}>
      {icon && (
        <span className={styles.emptyIcon} aria-hidden="true">
          {icon}
        </span>
      )}
      <span className={styles.emptyHeadline}>{headline}</span>
      {body && <span className={styles.emptyBody}>{body}</span>}
      {action}
    </div>
  );
}
