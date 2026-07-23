"use client";

import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import type { components } from "@cairn/api-client";
import { api } from "@/lib/api";
import { useMe, isAdmin } from "@/lib/auth";
import { useMyTasks } from "@/lib/tasks";
import { usePersistentFlag, ONBOARDING_KEYS } from "@/lib/onboarding";
import styles from "./onboarding.module.css";

type Project = components["schemas"]["ProjectResponse"];
type Portfolio = components["schemas"]["PortfolioResponse"];
type User = components["schemas"]["UserResponse"];

interface Step {
  label: string;
  hint?: string;
  done: boolean;
  /** disabled = a prerequisite step isn't met yet (e.g. portfolio before a project). */
  disabled?: boolean;
  onClick?: () => void;
}

/**
 * First-run checklist card (docs/03 §4.g.3 / §4.i). Admin and member variants;
 * dismissable and localStorage-persisted (PD-31 — no schema). Auto-hides once
 * every step is complete. Rendered at the top of My Tasks.
 */
export function OnboardingChecklist() {
  const router = useRouter();
  const { data: me } = useMe();
  const admin = isAdmin(me);
  const { value: dismissed, ready, set: dismiss } = usePersistentFlag(
    ONBOARDING_KEYS.checklistDismissed,
  );

  const projects = useQuery({
    queryKey: ["projects"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/projects");
      if (error) throw error;
      return (data?.data ?? []) as Project[];
    },
  });
  const portfolios = useQuery({
    queryKey: ["portfolios"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/portfolios");
      if (error) throw error;
      return (data?.data ?? []) as Portfolio[];
    },
    enabled: admin,
  });
  const users = useQuery({
    queryKey: ["users"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/users");
      if (error) throw error;
      return (data?.data ?? []) as User[];
    },
    enabled: admin,
  });
  const myTasks = useMyTasks();

  // Wait until the flag is read and the driving queries settle, so we neither
  // flash the card before dismissal is known nor show stale "not done" ticks.
  if (!ready || dismissed || !me) return null;

  const hasProject = (projects.data ?? []).length > 0;

  if (admin) {
    const invited = (users.data ?? []).length > 1;
    const hasPortfolio = (portfolios.data ?? []).length > 0;
    const steps: Step[] = [
      {
        label: "Create a project",
        hint: "Sections and tasks live here",
        done: hasProject,
        onClick: () => router.push("/projects/new"),
      },
      {
        label: "Invite your team",
        hint: "Share an invite link from Members",
        done: invited,
        onClick: () => router.push("/settings/members"),
      },
      {
        label: "Create a portfolio",
        hint: hasProject ? "Roll projects into one view" : "Create a project first",
        done: hasPortfolio,
        disabled: !hasProject,
        onClick: () => router.push("/portfolios/new"),
      },
    ];
    if (steps.every((s) => s.done)) return null;
    return (
      <Card
        title="Set up your workspace"
        sub="Three quick steps to get Cairn working for your team."
        steps={steps}
        onDismiss={dismiss}
      />
    );
  }

  // Member variant.
  const assigned = (myTasks.data ?? []).length;
  const steps: Step[] = [
    {
      label: `You've joined ${me.orgName ?? "the workspace"}`,
      done: true,
    },
    {
      label:
        assigned > 0
          ? `You have ${assigned} task${assigned === 1 ? "" : "s"} assigned`
          : "Assigned tasks will show up here",
      hint: "This is your My Tasks landing page",
      done: assigned > 0,
    },
    {
      label: "Find your way around",
      hint: "Use the sidebar to jump between My Tasks, projects, and portfolios",
      done: false,
    },
  ];
  return (
    <Card
      title={`Welcome to ${me.orgName ?? "Cairn"}`}
      sub="Here's where your work lives."
      steps={steps}
      onDismiss={dismiss}
    />
  );
}

function Card({
  title,
  sub,
  steps,
  onDismiss,
}: {
  title: string;
  sub: string;
  steps: Step[];
  onDismiss: () => void;
}) {
  return (
    <div className={styles.card}>
      <button
        type="button"
        className={styles.dismiss}
        aria-label="Dismiss checklist"
        onClick={onDismiss}
      >
        ✕
      </button>
      <div className={styles.head}>
        <span className={styles.title}>{title}</span>
        <span className={styles.sub}>{sub}</span>
      </div>
      <div className={styles.steps}>
        {steps.map((s) => (
          <button
            key={s.label}
            type="button"
            className={[styles.step, s.done ? styles.stepDone : ""]
              .filter(Boolean)
              .join(" ")}
            disabled={s.done || s.disabled || !s.onClick}
            onClick={s.onClick}
          >
            <span
              className={[styles.check, s.done ? styles.checkDone : ""]
                .filter(Boolean)
                .join(" ")}
              aria-hidden="true"
            >
              {s.done ? "✓" : ""}
            </span>
            <span className={styles.stepLabel}>{s.label}</span>
            {s.hint && <span className={styles.stepHint}>{s.hint}</span>}
          </button>
        ))}
      </div>
    </div>
  );
}
