"use client";

import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@cairn/api-client";
import { api, problemMessage } from "@/lib/api";
import { useToast } from "@/components/ui/Toast";
import { EmptyState } from "@/components/ui/EmptyState";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { CompleteToggle } from "@/components/task/CompleteToggle";
import { PriorityFlag } from "@/components/task/PriorityFlag";
import { useMyTasks, type MyTask } from "@/lib/tasks";
import { DUE_BUCKET_ORDER, dueBucket, formatDue } from "@/lib/dates";
import styles from "../page.module.css";

export default function MyTasksPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const { data, isLoading } = useMyTasks();

  const projects = useQuery({
    queryKey: ["projects"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/projects");
      if (error) throw error;
      return (data?.data ?? []) as components["schemas"]["ProjectResponse"][];
    },
  });
  const noProjects =
    !projects.isLoading && (projects.data ?? []).length === 0;

  const complete = useMutation({
    mutationFn: async (id: string) => {
      const { error } = await api.PATCH("/api/v1/tasks/{id}", {
        params: { path: { id } },
        body: { completed: true },
      });
      if (error) throw error;
    },
    onMutate: async (id: string) => {
      await qc.cancelQueries({ queryKey: ["me-tasks"] });
      const prev = qc.getQueryData<MyTask[]>(["me-tasks"]);
      qc.setQueryData<MyTask[]>(["me-tasks"], (old) =>
        (old ?? []).filter((t) => t.id !== id),
      );
      return { prev };
    },
    onError: (err, _id, ctx) => {
      if (ctx?.prev) qc.setQueryData(["me-tasks"], ctx.prev);
      toast.error(problemMessage(err, "Couldn't complete that task."));
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ["me-tasks"] }),
  });

  const groups = new Map<string, MyTask[]>();
  for (const t of data ?? []) {
    const k = dueBucket(t.dueDate);
    const arr = groups.get(k) ?? [];
    arr.push(t);
    groups.set(k, arr);
  }
  for (const arr of groups.values()) {
    arr.sort((a, b) => (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999"));
  }

  function openTask(t: MyTask) {
    if (t.projectId && t.id) {
      router.push(`/projects/${t.projectId}/list?task=${t.id}`);
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h1 className={styles.pageTitle}>My Tasks</h1>
          <p className={styles.pageSub}>Tasks assigned to you across projects.</p>
        </div>
      </div>

      {isLoading ? (
        <div className={styles.panel} style={{ padding: 16 }}>
          <Skeleton height={20} />
          <div style={{ height: 12 }} />
          <Skeleton height={20} />
          <div style={{ height: 12 }} />
          <Skeleton height={20} width="60%" />
        </div>
      ) : noProjects ? (
        <div className={styles.panel}>
          <EmptyState
            icon="✳"
            headline="Create your first project"
            body="Projects hold your sections and tasks. Start one to get going."
            action={
              <Button onClick={() => router.push("/projects/new")}>
                New project
              </Button>
            }
          />
        </div>
      ) : (data ?? []).length === 0 ? (
        <div className={styles.panel}>
          <EmptyState
            icon="✓"
            headline="You're clear"
            body="Tasks assigned to you land here."
          />
        </div>
      ) : (
        <div className={styles.panel}>
          {DUE_BUCKET_ORDER.filter((k) => (groups.get(k) ?? []).length > 0).map(
            (k) => (
              <div key={k}>
                <div
                  className={[
                    styles.groupTitle,
                    k === "Overdue" ? styles.groupTitleOverdue : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                >
                  {k} ({(groups.get(k) ?? []).length})
                </div>
                {(groups.get(k) ?? []).map((t) => (
                  <div key={t.id} className={styles.taskRow}>
                    <CompleteToggle
                      taskId={t.id ?? ""}
                      completed={false}
                      onChange={(c) => {
                        if (c && t.id) complete.mutate(t.id);
                      }}
                    />
                    <button
                      type="button"
                      className={styles.taskNameBtn}
                      onClick={() => openTask(t)}
                    >
                      {t.title}
                    </button>
                    <span className={styles.projectPill}>
                      <span
                        className={styles.projectDot}
                        style={
                          t.projectColor ? { background: t.projectColor } : undefined
                        }
                        aria-hidden="true"
                      />
                      {t.projectName}
                    </span>
                    <span className={styles.myPriority}>
                      <PriorityFlag priority={t.priority} />
                    </span>
                    <span
                      className={[
                        styles.taskDue,
                        k === "Overdue" ? styles.taskDueOverdue : "",
                      ]
                        .filter(Boolean)
                        .join(" ")}
                    >
                      {t.dueDate ? formatDue(t.dueDate) : ""}
                    </span>
                  </div>
                ))}
              </div>
            ),
          )}
        </div>
      )}
    </div>
  );
}
