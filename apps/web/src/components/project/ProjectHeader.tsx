"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { StatusChip } from "@/components/ui/StatusChip";
import { useProject } from "@/lib/tasks";
import { StatusComposer } from "./StatusComposer";
import styles from "./project.module.css";

export type ProjectTab = "overview" | "list" | "board";

const TABS: { key: ProjectTab; label: string; seg: string }[] = [
  { key: "overview", label: "Overview", seg: "overview" },
  { key: "list", label: "List", seg: "list" },
  { key: "board", label: "Board", seg: "board" },
];

/**
 * Shared project page header (docs/03 §4.a): breadcrumb, title + color dot,
 * current-status chip + "Update status" button, and the Overview | List | Board
 * view-switcher tabs. Rendered by the list, board and overview pages.
 */
export function ProjectHeader({
  projectId,
  active,
}: {
  projectId: string;
  active: ProjectTab;
}) {
  const project = useProject(projectId);
  const [composerOpen, setComposerOpen] = useState(false);

  const name = project.data?.name ?? "Project";
  const color = project.data?.color;

  return (
    <div className={styles.header}>
      <div className={styles.headMain}>
        <div className={styles.breadcrumb}>Projects</div>
        <h1 className={styles.title}>
          <span
            className={styles.projectDot}
            style={color ? { background: color } : undefined}
            aria-hidden="true"
          />
          {name}
        </h1>
        <nav className={styles.tabs} aria-label="Project views">
          {TABS.map((t) => {
            const isActive = t.key === active;
            return (
              <Link
                key={t.key}
                href={`/projects/${projectId}/${t.seg}`}
                className={`${styles.tab} ${isActive ? styles.tabActive : ""}`}
                aria-current={isActive ? "page" : undefined}
              >
                {t.label}
              </Link>
            );
          })}
        </nav>
      </div>

      <div className={styles.headSide}>
        <StatusChip
          status={project.data?.currentStatus}
          statusUpdatedAt={project.data?.statusUpdatedAt}
          showTime
          onClick={() => setComposerOpen(true)}
        />
        <Button
          variant="secondary"
          size="compact"
          onClick={() => setComposerOpen(true)}
        >
          Update status
        </Button>
      </div>

      <StatusComposer
        open={composerOpen}
        onClose={() => setComposerOpen(false)}
        projectId={projectId}
        initialStatus={project.data?.currentStatus}
      />
    </div>
  );
}
