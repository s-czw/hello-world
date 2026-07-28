"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@cairn/api-client";
import { api, problemMessage } from "./api";
import { useToast } from "@/components/ui/Toast";

export type Task = components["schemas"]["TaskResponse"];
export type Section = components["schemas"]["SectionResponse"];
export type Project = components["schemas"]["ProjectResponse"];
export type User = components["schemas"]["UserResponse"];
export type MyTask = components["schemas"]["MyTaskResponse"];

export type Priority = "none" | "low" | "medium" | "high";

export interface PriorityDef {
  value: Priority;
  label: string;
  color: string | null;
}

/** docs/03 §2.3 — none renders no flag; the rest are flag + label. */
export const PRIORITIES: PriorityDef[] = [
  { value: "none", label: "None", color: null },
  { value: "low", label: "Low", color: "var(--priority-low)" },
  { value: "medium", label: "Medium", color: "var(--priority-medium)" },
  { value: "high", label: "High", color: "var(--priority-high)" },
];

export function priorityDef(p: string | undefined): PriorityDef {
  return PRIORITIES.find((x) => x.value === p) ?? PRIORITIES[0];
}

/**
 * A patch for a task. Unlike the generated request type, nullable fields accept
 * `null` to *clear* them (unassign, clear due date) — the API distinguishes
 * absent (leave) from JSON null (clear) via per-field present flags. The
 * generated TS type lacks the nullability, so we cast at the fetch boundary.
 */
export interface TaskPatch {
  title?: string;
  completed?: boolean;
  description?: string | null;
  assigneeId?: string | null;
  dueDate?: string | null;
  priority?: string;
}

type UpdateBody = components["schemas"]["UpdateTaskRequest"];

export const tasksKey = (projectId: string) => ["tasks", projectId] as const;

// ── Queries ────────────────────────────────────────────────────────────────

export function useProject(projectId: string) {
  return useQuery({
    queryKey: ["project", projectId],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/projects/{id}", {
        params: { path: { id: projectId } },
      });
      if (error) throw error;
      return (data?.data ?? null) as Project | null;
    },
    enabled: !!projectId,
  });
}

export function useSections(projectId: string) {
  return useQuery({
    queryKey: ["sections", projectId],
    queryFn: async () => {
      const { data, error } = await api.GET(
        "/api/v1/projects/{projectId}/sections",
        { params: { path: { projectId } } },
      );
      if (error) throw error;
      return (data?.data ?? []) as Section[];
    },
    enabled: !!projectId,
  });
}

export function useTasks(projectId: string) {
  return useQuery({
    queryKey: tasksKey(projectId),
    queryFn: async () => {
      const { data, error } = await api.GET(
        "/api/v1/projects/{projectId}/tasks",
        { params: { path: { projectId }, query: { limit: 200 } } },
      );
      if (error) throw error;
      return (data?.data ?? []) as Task[];
    },
    enabled: !!projectId,
  });
}

export function useUsers() {
  return useQuery({
    queryKey: ["users"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/users", {
        params: { query: { limit: 200 } },
      });
      if (error) throw error;
      return (data?.data ?? []) as User[];
    },
    staleTime: 60_000,
  });
}

export function useMyTasks() {
  return useQuery({
    queryKey: ["me-tasks"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/me/tasks");
      if (error) throw error;
      return (data?.data ?? []) as MyTask[];
    },
  });
}

// ── Optimistic reorder helper (pure) ─────────────────────────────────────────

/**
 * Reorder `tasks` for a move. Rendering groups by section in array order, so
 * only the moved task's position *relative to its new section's tasks* matters.
 */
export function reorderForMove(
  tasks: Task[],
  movedId: string,
  sectionId: string | null,
  beforeTaskId?: string | null,
  afterTaskId?: string | null,
): Task[] {
  const moved = tasks.find((t) => t.id === movedId);
  if (!moved) return tasks;
  const rest = tasks.filter((t) => t.id !== movedId);
  const next: Task = { ...moved, sectionId: sectionId ?? undefined };

  let idx: number;
  if (afterTaskId) {
    const i = rest.findIndex((t) => t.id === afterTaskId);
    idx = i < 0 ? rest.length : i + 1;
  } else if (beforeTaskId) {
    const i = rest.findIndex((t) => t.id === beforeTaskId);
    idx = i < 0 ? rest.length : i;
  } else {
    // Append after the last task already in the target section.
    let last = -1;
    for (let i = 0; i < rest.length; i++) {
      if ((rest[i].sectionId ?? null) === sectionId) last = i;
    }
    idx = last < 0 ? rest.length : last + 1;
  }
  rest.splice(idx, 0, next);
  return rest;
}

function applyPatch(t: Task, patch: TaskPatch): Task {
  const next: Task = { ...t };
  if (patch.title !== undefined) next.title = patch.title;
  if (patch.completed !== undefined) next.completed = patch.completed;
  if (patch.priority !== undefined) next.priority = patch.priority;
  if (patch.description !== undefined) next.description = patch.description ?? undefined;
  if ("assigneeId" in patch) next.assigneeId = patch.assigneeId ?? undefined;
  if ("dueDate" in patch) next.dueDate = patch.dueDate ?? undefined;
  return next;
}

// ── Mutations (optimistic with rollback + error toast) ───────────────────────

export interface MoveArgs {
  id: string;
  sectionId: string | null;
  beforeTaskId?: string | null;
  afterTaskId?: string | null;
}

export function useTaskMutations(projectId: string) {
  const qc = useQueryClient();
  const toast = useToast();
  const key = tasksKey(projectId);

  const invalidateRelated = () => {
    qc.invalidateQueries({ queryKey: key });
    qc.invalidateQueries({ queryKey: ["me-tasks"] });
  };

  const updateTask = useMutation({
    mutationFn: async ({ id, patch }: { id: string; patch: TaskPatch }) => {
      const { data, error } = await api.PATCH("/api/v1/tasks/{id}", {
        params: { path: { id } },
        // Cast: TaskPatch allows null-to-clear; the generated type doesn't.
        body: patch as unknown as UpdateBody,
      });
      if (error) throw error;
      return (data?.data ?? null) as Task | null;
    },
    onMutate: async ({ id, patch }) => {
      await qc.cancelQueries({ queryKey: key });
      const prev = qc.getQueryData<Task[]>(key);
      qc.setQueryData<Task[]>(key, (old) =>
        (old ?? []).map((t) => (t.id === id ? applyPatch(t, patch) : t)),
      );
      return { prev };
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev);
      toast.error(problemMessage(err, "Couldn't save that change."));
    },
    onSettled: invalidateRelated,
  });

  const moveTask = useMutation({
    mutationFn: async ({ id, sectionId, beforeTaskId, afterTaskId }: MoveArgs) => {
      const { data, error } = await api.POST("/api/v1/tasks/{id}/move", {
        params: { path: { id } },
        body: {
          sectionId: sectionId ?? undefined,
          beforeTaskId: beforeTaskId ?? undefined,
          afterTaskId: afterTaskId ?? undefined,
        },
      });
      if (error) throw error;
      return (data?.data ?? null) as Task | null;
    },
    onMutate: async (args) => {
      await qc.cancelQueries({ queryKey: key });
      const prev = qc.getQueryData<Task[]>(key);
      qc.setQueryData<Task[]>(key, (old) =>
        reorderForMove(old ?? [], args.id, args.sectionId, args.beforeTaskId, args.afterTaskId),
      );
      return { prev };
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev);
      toast.error(problemMessage(err, "Couldn't move that task."));
    },
    onSettled: invalidateRelated,
  });

  const createTask = useMutation({
    mutationFn: async (vars: { title: string; sectionId: string | null }) => {
      const { data, error } = await api.POST(
        "/api/v1/projects/{projectId}/tasks",
        {
          params: { path: { projectId } },
          body: {
            title: vars.title,
            sectionId: vars.sectionId ?? undefined,
          },
        },
      );
      if (error) throw error;
      return (data?.data ?? null) as Task | null;
    },
    onMutate: async (vars) => {
      await qc.cancelQueries({ queryKey: key });
      const prev = qc.getQueryData<Task[]>(key);
      const tempId = `temp-${crypto.randomUUID()}`;
      const temp: Task = {
        id: tempId,
        projectId,
        sectionId: vars.sectionId ?? undefined,
        title: vars.title,
        completed: false,
        priority: "none",
      };
      qc.setQueryData<Task[]>(key, (old) =>
        reorderForMove([...(old ?? []), temp], tempId, vars.sectionId),
      );
      return { prev, tempId };
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev);
      toast.error(problemMessage(err, "Couldn't add that task."));
    },
    onSuccess: (created, _vars, ctx) => {
      if (created && ctx?.tempId) {
        qc.setQueryData<Task[]>(key, (old) =>
          (old ?? []).map((t) => (t.id === ctx.tempId ? created : t)),
        );
      }
    },
    onSettled: invalidateRelated,
  });

  const deleteTask = useMutation({
    mutationFn: async (id: string) => {
      const { error } = await api.DELETE("/api/v1/tasks/{id}", {
        params: { path: { id } },
      });
      if (error) throw error;
      return id;
    },
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: key });
      const prev = qc.getQueryData<Task[]>(key);
      qc.setQueryData<Task[]>(key, (old) => (old ?? []).filter((t) => t.id !== id));
      return { prev };
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev);
      toast.error(problemMessage(err, "Couldn't delete that task."));
    },
    onSettled: invalidateRelated,
  });

  return { updateTask, moveTask, createTask, deleteTask };
}
