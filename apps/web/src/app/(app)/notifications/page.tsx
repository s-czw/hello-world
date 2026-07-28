"use client";

import { useRouter } from "next/navigation";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  deepLink,
  payloadOf,
  sentence,
  useMarkAllRead,
  useMarkRead,
  useNotifications,
  type Notification,
} from "@/lib/notifications";
import { relativeTime, statusDef } from "@/lib/status";
import styles from "./notifications.module.css";

function isToday(iso: string | undefined): boolean {
  if (!iso) return false;
  const d = new Date(iso);
  const now = new Date();
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  );
}

export default function NotificationsPage() {
  const router = useRouter();
  const { data, isLoading } = useNotifications();
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();

  const items = data ?? [];
  const unreadCount = items.filter((n) => !n.read).length;
  const today = items.filter((n) => isToday(n.createdAt));
  const earlier = items.filter((n) => !isToday(n.createdAt));

  function open(n: Notification) {
    if (!n.read && n.id) markRead.mutate(n.id);
    const href = deepLink(n);
    if (href) router.push(href);
  }

  function renderGroup(label: string, rows: Notification[]) {
    if (rows.length === 0) return null;
    return (
      <div key={label}>
        <div className={styles.groupTitle}>{label}</div>
        {rows.map((n) => {
          const p = payloadOf(n);
          const sdef = n.type === "status_update" ? statusDef(p.status) : null;
          return (
            <button
              key={n.id}
              type="button"
              className={[styles.row, n.read ? "" : styles.rowUnread]
                .filter(Boolean)
                .join(" ")}
              onClick={() => open(n)}
            >
              {n.read ? (
                <span className={styles.dotSpacer} aria-hidden="true" />
              ) : (
                <span className={styles.dot} aria-label="Unread" />
              )}
              <Avatar name={p.actorName ?? "?"} seed={n.actorId} size={24} />
              <span className={styles.rowBody}>
                <span className={styles.sentence}>{sentence(n)}</span>
                {sdef && (
                  <span className={styles.meta}>
                    <span
                      className={styles.statusPill}
                      style={{ background: sdef.bg, color: sdef.ink }}
                    >
                      <span aria-hidden="true">{sdef.icon}</span>
                      {sdef.label}
                    </span>
                  </span>
                )}
                {p.snippet && !sdef && (
                  <span className={styles.snippet}>{p.snippet}</span>
                )}
              </span>
              <span className={styles.time}>{relativeTime(n.createdAt)}</span>
            </button>
          );
        })}
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Notifications</h1>
        {unreadCount > 0 && (
          <Button
            variant="secondary"
            size="compact"
            onClick={() => markAllRead.mutate()}
          >
            Mark all read
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className={styles.panel} style={{ padding: 16 }}>
          <Skeleton height={20} />
          <div style={{ height: 12 }} />
          <Skeleton height={20} />
          <div style={{ height: 12 }} />
          <Skeleton height={20} width="60%" />
        </div>
      ) : items.length === 0 ? (
        <div className={styles.panel}>
          <EmptyState
            icon="🔔"
            headline="You're all caught up"
            body="Assignments, comments, and status changes land here."
          />
        </div>
      ) : (
        <div className={styles.panel}>
          {renderGroup("Today", today)}
          {renderGroup("Earlier", earlier)}
        </div>
      )}
    </div>
  );
}
