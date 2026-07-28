"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { csrfHeaders, type components } from "@cairn/api-client";
import { api, problemMessage } from "./api";
import { useToast } from "@/components/ui/Toast";
import { reorderForMove, tasksKey, type Task, type TaskPatch } from "./tasks";

export type TaskDetail = components["schemas"]["TaskDetailResponse"];
export type ActivityEntry = components["schemas"]["ActivityStreamEntry"];
export type Attachment = components["schemas"]["AttachmentResponse"];

type UpdateBody = components["schemas"]["UpdateTaskRequest"];

export const taskDetailKey = (id: string) => ["task-detail", id] as const;
export const activityKey = (id: string) => ["activity", id] as const;
export const attachmentsKey = (id: string) => ["attachments", id] as const;

/** True when the mime type is an image we can thumbnail / lightbox. */
export function isImageType(contentType: string | undefined): boolean {
  return !!contentType && contentType.startsWith("image/");
}

/** Human file size — "820 B", "12.4 KB", "3.1 MB". */
export function formatBytes(bytes: number | undefined): string {
  const n = bytes ?? 0;
  if (n < 1024) return `${n} B`;
  const kb = n / 1024;
  if (kb < 1024) return `${kb < 10 ? kb.toFixed(1) : Math.round(kb)} KB`;
  const mb = kb / 1024;
  return `${mb < 10 ? mb.toFixed(1) : Math.round(mb)} MB`;
}

// ── Queries ──────────────────────────────────────────────────────────────────

/**
 * Full task detail (the task + its subtasks + progress). `initialTask` seeds the
 * cache so the peek renders instantly while the detail loads.
 */
export function useTaskDetail(taskId: string | undefined, initialTask?: Task) {
  return useQuery({
    queryKey: taskDetailKey(taskId ?? ""),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/tasks/{id}", {
        params: { path: { id: taskId! } },
      });
      if (error) throw error;
      return (data?.data ?? null) as TaskDetail | null;
    },
    enabled: !!taskId,
    placeholderData: initialTask
      ? { task: initialTask, subtasks: [], subtaskProgress: { total: 0, completed: 0 } }
      : undefined,
  });
}

/** Merged comments + system events, chronological (the API merges them). */
export function useActivityStream(taskId: string | undefined) {
  return useQuery({
    queryKey: activityKey(taskId ?? ""),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/tasks/{taskId}/activity", {
        params: { path: { taskId: taskId! } },
      });
      if (error) throw error;
      return (data?.data ?? []) as ActivityEntry[];
    },
    enabled: !!taskId,
  });
}

export function useAttachments(taskId: string | undefined) {
  return useQuery({
    queryKey: attachmentsKey(taskId ?? ""),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/tasks/{taskId}/attachments", {
        params: { path: { taskId: taskId! } },
      });
      if (error) throw error;
      return (data?.data ?? []) as Attachment[];
    },
    enabled: !!taskId,
  });
}

// ── Helpers ────────────────────────────────────────────────────────────────

function applyTaskPatch(t: Task, patch: TaskPatch): Task {
  const n: Task = { ...t };
  if (patch.title !== undefined) n.title = patch.title;
  if (patch.completed !== undefined) n.completed = patch.completed;
  if (patch.priority !== undefined) n.priority = patch.priority;
  if (patch.description !== undefined) n.description = patch.description ?? undefined;
  if ("assigneeId" in patch) n.assigneeId = patch.assigneeId ?? undefined;
  if ("dueDate" in patch) n.dueDate = patch.dueDate ?? undefined;
  return n;
}

function progressOf(subtasks: Task[]): { total: number; completed: number } {
  return {
    total: subtasks.length,
    completed: subtasks.filter((s) => s.completed).length,
  };
}

// ── Mutations (optimistic + rollback + error toast) ──────────────────────────

/**
 * All the side-peek writes. `projectId` is the list the viewed task belongs to
 * (so the list stays reconciled). Each mutation optimistically patches the
 * caches the peek and the list both read, then invalidates to reconcile.
 */
export function useTaskDetailMutations(projectId: string) {
  const qc = useQueryClient();
  const toast = useToast();
  const listKey = tasksKey(projectId);

  // Update the viewed task (fields, completion, description). `parentId` (when
  // the viewed task is a subtask) keeps the parent's subtask row + progress fresh.
  const patchTask = useMutation({
    mutationFn: async ({ id, patch }: { id: string; parentId?: string; patch: TaskPatch }) => {
      const { data, error } = await api.PATCH("/api/v1/tasks/{id}", {
        params: { path: { id } },
        body: patch as unknown as UpdateBody,
      });
      if (error) throw error;
      return (data?.data ?? null) as Task | null;
    },
    onMutate: async ({ id, parentId, patch }) => {
      await qc.cancelQueries({ queryKey: taskDetailKey(id) });
      await qc.cancelQueries({ queryKey: listKey });
      const prevDetail = qc.getQueryData<TaskDetail>(taskDetailKey(id));
      const prevParent = parentId
        ? qc.getQueryData<TaskDetail>(taskDetailKey(parentId))
        : undefined;
      const prevList = qc.getQueryData<Task[]>(listKey);

      qc.setQueryData<TaskDetail>(taskDetailKey(id), (old) =>
        old?.task ? { ...old, task: applyTaskPatch(old.task, patch) } : old,
      );
      if (parentId) {
        qc.setQueryData<TaskDetail>(taskDetailKey(parentId), (old) => {
          if (!old?.subtasks) return old;
          const subtasks = old.subtasks.map((s) =>
            s.id === id ? applyTaskPatch(s, patch) : s,
          );
          return { ...old, subtasks, subtaskProgress: progressOf(subtasks) };
        });
      }
      qc.setQueryData<Task[]>(listKey, (old) =>
        (old ?? []).map((t) => (t.id === id ? applyTaskPatch(t, patch) : t)),
      );
      return { prevDetail, prevParent, prevList, id, parentId };
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prevDetail) qc.setQueryData(taskDetailKey(ctx.id), ctx.prevDetail);
      if (ctx?.parentId && ctx.prevParent)
        qc.setQueryData(taskDetailKey(ctx.parentId), ctx.prevParent);
      if (ctx?.prevList) qc.setQueryData(listKey, ctx.prevList);
      toast.error(problemMessage(err, "Couldn't save that change."));
    },
    onSettled: (_d, _e, vars) => {
      qc.invalidateQueries({ queryKey: taskDetailKey(vars.id) });
      if (vars.parentId) qc.invalidateQueries({ queryKey: taskDetailKey(vars.parentId) });
      qc.invalidateQueries({ queryKey: activityKey(vars.id) });
      qc.invalidateQueries({ queryKey: listKey });
      qc.invalidateQueries({ queryKey: ["me-tasks"] });
    },
  });

  // Move the viewed (top-level) task to another section.
  const moveViewedTask = useMutation({
    mutationFn: async ({ id, sectionId }: { id: string; sectionId: string | null }) => {
      const { data, error } = await api.POST("/api/v1/tasks/{id}/move", {
        params: { path: { id } },
        body: { sectionId: sectionId ?? undefined },
      });
      if (error) throw error;
      return (data?.data ?? null) as Task | null;
    },
    onMutate: async ({ id, sectionId }) => {
      await qc.cancelQueries({ queryKey: taskDetailKey(id) });
      await qc.cancelQueries({ queryKey: listKey });
      const prevDetail = qc.getQueryData<TaskDetail>(taskDetailKey(id));
      const prevList = qc.getQueryData<Task[]>(listKey);
      qc.setQueryData<TaskDetail>(taskDetailKey(id), (old) =>
        old?.task ? { ...old, task: { ...old.task, sectionId: sectionId ?? undefined } } : old,
      );
      qc.setQueryData<Task[]>(listKey, (old) => reorderForMove(old ?? [], id, sectionId));
      return { prevDetail, prevList, id };
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prevDetail) qc.setQueryData(taskDetailKey(ctx.id), ctx.prevDetail);
      if (ctx?.prevList) qc.setQueryData(listKey, ctx.prevList);
      toast.error(problemMessage(err, "Couldn't move that task."));
    },
    onSettled: (_d, _e, vars) => {
      qc.invalidateQueries({ queryKey: taskDetailKey(vars.id) });
      qc.invalidateQueries({ queryKey: listKey });
      qc.invalidateQueries({ queryKey: ["me-tasks"] });
    },
  });

  const deleteTask = useMutation({
    mutationFn: async ({ id }: { id: string; parentId?: string }) => {
      const { error } = await api.DELETE("/api/v1/tasks/{id}", { params: { path: { id } } });
      if (error) throw error;
      return id;
    },
    onSettled: (_d, _e, vars) => {
      if (vars.parentId) qc.invalidateQueries({ queryKey: taskDetailKey(vars.parentId) });
      qc.invalidateQueries({ queryKey: listKey });
      qc.invalidateQueries({ queryKey: ["me-tasks"] });
    },
    onError: (err) => toast.error(problemMessage(err, "Couldn't delete that task.")),
  });

  // ── Subtasks ───────────────────────────────────────────────────────────────
  const addSubtask = useMutation({
    mutationFn: async ({ parentId, title }: { parentId: string; title: string }) => {
      const { data, error } = await api.POST("/api/v1/tasks/{id}/subtasks", {
        params: { path: { id: parentId } },
        body: { title },
      });
      if (error) throw error;
      return (data?.data ?? null) as Task | null;
    },
    onMutate: async ({ parentId, title }) => {
      await qc.cancelQueries({ queryKey: taskDetailKey(parentId) });
      const prev = qc.getQueryData<TaskDetail>(taskDetailKey(parentId));
      const tempId = `temp-${crypto.randomUUID()}`;
      const temp: Task = {
        id: tempId,
        projectId,
        parentTaskId: parentId,
        title,
        completed: false,
        priority: "none",
      };
      qc.setQueryData<TaskDetail>(taskDetailKey(parentId), (old) => {
        const subtasks = [...(old?.subtasks ?? []), temp];
        return {
          task: old?.task ?? { id: parentId },
          subtasks,
          subtaskProgress: progressOf(subtasks),
        };
      });
      return { prev, parentId, tempId };
    },
    onError: (err, _vars, ctx) => {
      if (ctx) qc.setQueryData(taskDetailKey(ctx.parentId), ctx.prev);
      toast.error(problemMessage(err, "Couldn't add that subtask."));
    },
    onSuccess: (created, _vars, ctx) => {
      if (created && ctx) {
        qc.setQueryData<TaskDetail>(taskDetailKey(ctx.parentId), (old) =>
          old?.subtasks
            ? {
                ...old,
                subtasks: old.subtasks.map((s) => (s.id === ctx.tempId ? created : s)),
              }
            : old,
        );
      }
    },
    onSettled: (_d, _e, vars) => {
      qc.invalidateQueries({ queryKey: taskDetailKey(vars.parentId) });
    },
  });

  const promoteSubtask = useMutation({
    mutationFn: async ({
      id,
      sectionId,
    }: {
      id: string;
      parentId: string;
      sectionId: string | null;
    }) => {
      const { data, error } = await api.POST("/api/v1/tasks/{id}/promote", {
        params: { path: { id } },
        body: { sectionId: sectionId ?? undefined },
      });
      if (error) throw error;
      return (data?.data ?? null) as Task | null;
    },
    onSuccess: async (_d, vars) => {
      // Await the list refetch so the promoted task is present before we navigate to it.
      await qc.invalidateQueries({ queryKey: listKey });
      qc.invalidateQueries({ queryKey: taskDetailKey(vars.parentId) });
      qc.invalidateQueries({ queryKey: taskDetailKey(vars.id) });
      qc.invalidateQueries({ queryKey: ["me-tasks"] });
    },
    onError: (err) => toast.error(problemMessage(err, "Couldn't promote that subtask.")),
  });

  // ── Comments (stream = activity key) ─────────────────────────────────────────
  const postComment = useMutation({
    mutationFn: async ({ taskId, body }: { taskId: string; body: string }) => {
      const { data, error } = await api.POST("/api/v1/tasks/{taskId}/comments", {
        params: { path: { taskId } },
        body: { body },
      });
      if (error) throw error;
      return (data?.data ?? null) as components["schemas"]["CommentResponse"] | null;
    },
    onMutate: async ({ taskId, body }) => {
      await qc.cancelQueries({ queryKey: activityKey(taskId) });
      const prev = qc.getQueryData<ActivityEntry[]>(activityKey(taskId));
      const tempId = `temp-${crypto.randomUUID()}`;
      const optimistic: ActivityEntry = {
        kind: "comment",
        id: tempId,
        body,
        edited: false,
        createdAt: new Date().toISOString(),
      };
      qc.setQueryData<ActivityEntry[]>(activityKey(taskId), (old) => [...(old ?? []), optimistic]);
      return { prev, taskId, tempId };
    },
    onError: (err, _vars, ctx) => {
      if (ctx) qc.setQueryData(activityKey(ctx.taskId), ctx.prev);
      toast.error(problemMessage(err, "Couldn't post that comment."));
    },
    onSuccess: (created, _vars, ctx) => {
      if (created && ctx) {
        qc.setQueryData<ActivityEntry[]>(activityKey(ctx.taskId), (old) =>
          (old ?? []).map((e) =>
            e.id === ctx.tempId
              ? {
                  kind: "comment",
                  id: created.id,
                  actorId: created.authorId,
                  body: created.body,
                  edited: !!created.edited,
                  editedAt: created.editedAt,
                  createdAt: created.createdAt,
                }
              : e,
          ),
        );
      }
    },
    onSettled: (_d, _e, vars) => {
      qc.invalidateQueries({ queryKey: activityKey(vars.taskId) });
    },
  });

  const editComment = useMutation({
    mutationFn: async ({ commentId, body }: { commentId: string; taskId: string; body: string }) => {
      const { data, error } = await api.PATCH("/api/v1/comments/{id}", {
        params: { path: { id: commentId } },
        body: { body },
      });
      if (error) throw error;
      return (data?.data ?? null) as components["schemas"]["CommentResponse"] | null;
    },
    onMutate: async ({ commentId, taskId, body }) => {
      await qc.cancelQueries({ queryKey: activityKey(taskId) });
      const prev = qc.getQueryData<ActivityEntry[]>(activityKey(taskId));
      qc.setQueryData<ActivityEntry[]>(activityKey(taskId), (old) =>
        (old ?? []).map((e) =>
          e.id === commentId ? { ...e, body, edited: true, editedAt: new Date().toISOString() } : e,
        ),
      );
      return { prev, taskId };
    },
    onError: (err, _vars, ctx) => {
      if (ctx) qc.setQueryData(activityKey(ctx.taskId), ctx.prev);
      toast.error(problemMessage(err, "Couldn't save that edit."));
    },
    onSettled: (_d, _e, vars) => {
      qc.invalidateQueries({ queryKey: activityKey(vars.taskId) });
    },
  });

  const deleteComment = useMutation({
    mutationFn: async ({ commentId }: { commentId: string; taskId: string }) => {
      const { error } = await api.DELETE("/api/v1/comments/{id}", {
        params: { path: { id: commentId } },
      });
      if (error) throw error;
      return commentId;
    },
    onMutate: async ({ commentId, taskId }) => {
      await qc.cancelQueries({ queryKey: activityKey(taskId) });
      const prev = qc.getQueryData<ActivityEntry[]>(activityKey(taskId));
      qc.setQueryData<ActivityEntry[]>(activityKey(taskId), (old) =>
        (old ?? []).filter((e) => e.id !== commentId),
      );
      return { prev, taskId };
    },
    onError: (err, _vars, ctx) => {
      if (ctx) qc.setQueryData(activityKey(ctx.taskId), ctx.prev);
      toast.error(problemMessage(err, "Couldn't delete that comment."));
    },
    onSettled: (_d, _e, vars) => {
      qc.invalidateQueries({ queryKey: activityKey(vars.taskId) });
    },
  });

  // ── Attachments (multipart via raw fetch — openapi-fetch can't do form-data) ──
  const uploadAttachment = useMutation({
    mutationFn: async ({ taskId, file }: { taskId: string; file: File }) => {
      const form = new FormData();
      form.append("file", file);
      const res = await fetch(`/api/v1/tasks/${taskId}/attachments`, {
        method: "POST",
        credentials: "include",
        headers: csrfHeaders(),
        body: form,
      });
      if (!res.ok) {
        let problem: unknown = null;
        try {
          problem = await res.json();
        } catch {
          /* non-JSON body */
        }
        throw (
          problem ??
          new Error(
            res.status === 413
              ? "That file is too large (max 25 MB)."
              : res.status === 415
                ? "That file type isn't allowed."
                : "Upload failed.",
          )
        );
      }
      const json = (await res.json()) as { data?: Attachment };
      return json.data ?? null;
    },
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: attachmentsKey(vars.taskId) });
    },
    onError: (err) => toast.error(problemMessage(err, "Couldn't upload that file.")),
  });

  const deleteAttachment = useMutation({
    mutationFn: async ({ attachmentId }: { attachmentId: string; taskId: string }) => {
      const res = await fetch(`/api/v1/attachments/${attachmentId}`, {
        method: "DELETE",
        credentials: "include",
        headers: csrfHeaders(),
      });
      if (!res.ok && res.status !== 204) throw new Error("Delete failed.");
      return attachmentId;
    },
    onMutate: async ({ attachmentId, taskId }) => {
      await qc.cancelQueries({ queryKey: attachmentsKey(taskId) });
      const prev = qc.getQueryData<Attachment[]>(attachmentsKey(taskId));
      qc.setQueryData<Attachment[]>(attachmentsKey(taskId), (old) =>
        (old ?? []).filter((a) => a.id !== attachmentId),
      );
      return { prev, taskId };
    },
    onError: (err, _vars, ctx) => {
      if (ctx) qc.setQueryData(attachmentsKey(ctx.taskId), ctx.prev);
      toast.error(problemMessage(err, "Couldn't delete that file."));
    },
    onSettled: (_d, _e, vars) => {
      qc.invalidateQueries({ queryKey: attachmentsKey(vars.taskId) });
    },
  });

  return {
    patchTask,
    moveViewedTask,
    deleteTask,
    addSubtask,
    promoteSubtask,
    postComment,
    editComment,
    deleteComment,
    uploadAttachment,
    deleteAttachment,
  };
}
