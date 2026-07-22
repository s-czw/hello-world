"use client";

import { useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Project } from "@/lib/tasks";
import { Button } from "@/components/ui/Button";
import { Popover } from "@/components/ui/Popover";
import { useAddProjectToPortfolio } from "@/lib/portfolio";
import uiStyles from "@/components/ui/ui.module.css";
import taskStyles from "@/components/task/task.module.css";
import styles from "./portfolio.module.css";

export interface AddProjectsTypeaheadProps {
  portfolioId: string;
  /** Project ids already in the portfolio — hidden from the list. */
  existingProjectIds: Set<string>;
}

/**
 * "+ Add projects" typeahead (docs/03 §4.d): a filter input over the org's
 * projects (archived + already-member excluded), each POSTing a membership.
 * Stays open for adding several in a row.
 */
export function AddProjectsTypeahead({
  portfolioId,
  existingProjectIds,
}: AddProjectsTypeaheadProps) {
  const anchorRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const add = useAddProjectToPortfolio(portfolioId);

  const projects = useQuery({
    queryKey: ["projects"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/projects");
      if (error) throw error;
      return (data?.data ?? []) as Project[];
    },
  });

  const filtered = useMemo(() => {
    const s = q.trim().toLowerCase();
    return (projects.data ?? [])
      .filter((p) => !p.archived && p.id && !existingProjectIds.has(p.id))
      .filter((p) => (s ? (p.name ?? "").toLowerCase().includes(s) : true));
  }, [projects.data, q, existingProjectIds]);

  function openPop() {
    setQ("");
    setOpen(true);
  }

  return (
    <>
      <Button
        ref={anchorRef}
        variant="secondary"
        size="compact"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={openPop}
      >
        + Add projects
      </Button>
      <Popover
        open={open}
        onClose={() => setOpen(false)}
        anchorRef={anchorRef}
        align="end"
        width={288}
        label="Add projects"
      >
        <div className={styles.addPop}>
          <input
            className={uiStyles.control}
            placeholder="Search projects…"
            aria-label="Search projects"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <ul className={taskStyles.optionList} role="listbox" aria-label="Projects">
            {filtered.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={false}
                  className={taskStyles.option}
                  disabled={add.isPending}
                  onClick={() => {
                    if (p.id) add.mutate(p.id);
                  }}
                >
                  <span
                    className={styles.dot}
                    style={p.color ? { background: p.color } : undefined}
                    aria-hidden="true"
                  />
                  <span className={styles.addName}>{p.name}</span>
                </button>
              </li>
            ))}
            {filtered.length === 0 && (
              <li className={taskStyles.optionEmpty}>
                {projects.isLoading ? "Loading…" : "No projects to add"}
              </li>
            )}
          </ul>
        </div>
      </Popover>
    </>
  );
}
