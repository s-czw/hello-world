"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { components } from "@cairn/api-client";
import { useMyTasks } from "@/lib/tasks";
import { usePortfolios } from "@/lib/portfolio";
import { dueBucket } from "@/lib/dates";
import styles from "./shell.module.css";

type Team = components["schemas"]["TeamResponse"];
type Project = components["schemas"]["ProjectResponse"];

export function Sidebar() {
  const pathname = usePathname();

  const teams = useQuery({
    queryKey: ["teams"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/teams");
      if (error) throw error;
      return (data?.data ?? []) as Team[];
    },
  });

  const projects = useQuery({
    queryKey: ["projects"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/projects");
      if (error) throw error;
      return (data?.data ?? []) as Project[];
    },
  });

  const portfolios = usePortfolios();

  const myTasks = useMyTasks();
  const badge = (myTasks.data ?? []).filter((t) => {
    const b = dueBucket(t.dueDate);
    return b === "Overdue" || b === "Today";
  }).length;

  const byTeam = new Map<string, Project[]>();
  for (const p of projects.data ?? []) {
    const list = byTeam.get(p.teamId ?? "") ?? [];
    list.push(p);
    byTeam.set(p.teamId ?? "", list);
  }

  return (
    <nav className={styles.sidebar} aria-label="Primary">
      <div className={styles.brand}>
        <span className={styles.brandMark} aria-hidden="true" />
        Cairn
      </div>

      <div className={styles.nav}>
        <Link
          href="/my-tasks"
          className={[
            styles.navItem,
            pathname === "/my-tasks" ? styles.navItemActive : "",
          ]
            .filter(Boolean)
            .join(" ")}
        >
          <span className={styles.navItemLabel}>My Tasks</span>
          {badge > 0 && (
            <span className={styles.badge} aria-label={`${badge} due or overdue`}>
              {badge}
            </span>
          )}
        </Link>

        <div className={styles.sectionLabel}>
          Portfolios
          <Link
            href="/portfolios/new"
            className={styles.navItem}
            style={{ height: "auto", padding: "0 6px" }}
            aria-label="New portfolio"
          >
            +
          </Link>
        </div>

        {(portfolios.data ?? []).length === 0 && !portfolios.isLoading && (
          <div className={styles.sidebarEmpty}>No portfolios yet</div>
        )}

        {(portfolios.data ?? []).map((pf) => {
          const active = pathname.startsWith(`/portfolios/${pf.id}`);
          return (
            <Link
              key={pf.id}
              href={`/portfolios/${pf.id}`}
              className={[
                styles.navItem,
                styles.projectItem,
                active ? styles.navItemActive : "",
              ]
                .filter(Boolean)
                .join(" ")}
            >
              <span
                className={styles.projectDot}
                style={pf.color ? { background: pf.color } : undefined}
                aria-hidden="true"
              />
              {pf.name}
            </Link>
          );
        })}

        <div className={styles.sectionLabel}>
          Teams
          <Link
            href="/projects/new"
            className={styles.navItem}
            style={{ height: "auto", padding: "0 6px" }}
            aria-label="New project"
          >
            +
          </Link>
        </div>

        {(teams.data ?? []).length === 0 && !teams.isLoading && (
          <div className={styles.sidebarEmpty}>No teams yet</div>
        )}

        {(teams.data ?? []).map((team) => {
          const teamProjects = (byTeam.get(team.id ?? "") ?? []).filter(
            (p) => !p.archived,
          );
          return (
            <div key={team.id} className={styles.teamGroup}>
              <div className={styles.teamName}>{team.name}</div>
              {teamProjects.length === 0 ? (
                <div className={styles.sidebarEmpty}>No projects</div>
              ) : (
                teamProjects.map((p) => {
                  const href = `/projects/${p.id}/list`;
                  const active = pathname.startsWith(`/projects/${p.id}`);
                  return (
                    <Link
                      key={p.id}
                      href={href}
                      className={[
                        styles.navItem,
                        styles.projectItem,
                        active ? styles.navItemActive : "",
                      ]
                        .filter(Boolean)
                        .join(" ")}
                    >
                      <span
                        className={styles.projectDot}
                        style={p.color ? { background: p.color } : undefined}
                        aria-hidden="true"
                      />
                      {p.name}
                    </Link>
                  );
                })
              )}
            </div>
          );
        })}
      </div>
    </nav>
  );
}
