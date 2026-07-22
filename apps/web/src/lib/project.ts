"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@cairn/api-client";
import { api, problemMessage } from "./api";
import { useToast } from "@/components/ui/Toast";
import type { Project } from "./tasks";

export type StatusUpdate = components["schemas"]["StatusUpdateResponse"];
export type Team = components["schemas"]["TeamResponse"];

/** A patch for a project. Nullable date fields accept `null` to clear (the API
 *  distinguishes absent from JSON null via per-field present flags). */
export interface ProjectPatch {
  name?: string;
  description?: string | null;
  color?: string;
  startDate?: string | null;
  endDate?: string | null;
  ownerId?: string;
  teamId?: string;
  archived?: boolean;
}

type UpdateProjectBody = components["schemas"]["UpdateProjectRequest"];

export const statusUpdatesKey = (projectId: string) =>
  ["status-updates", projectId] as const;

/** Reverse-chronological status history for a project (newest first). */
export function useStatusUpdates(projectId: string) {
  return useQuery({
    queryKey: statusUpdatesKey(projectId),
    queryFn: async () => {
      const { data, error } = await api.GET(
        "/api/v1/projects/{projectId}/status-updates",
        { params: { path: { projectId }, query: { limit: 50 } } },
      );
      if (error) throw error;
      return (data?.data ?? []) as StatusUpdate[];
    },
    enabled: !!projectId,
  });
}

export function useTeams() {
  return useQuery({
    queryKey: ["teams"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/teams");
      if (error) throw error;
      return (data?.data ?? []) as Team[];
    },
    staleTime: 60_000,
  });
}

/** Post a status update. Server denormalizes project.current_status +
 *  status_updated_at in the same tx, so we refetch the project + history. */
export function usePostStatusUpdate(projectId: string) {
  const qc = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: async (vars: { status: string; title: string; body: string }) => {
      const { data, error } = await api.POST(
        "/api/v1/projects/{projectId}/status-updates",
        {
          params: { path: { projectId } },
          body: { status: vars.status, title: vars.title, body: vars.body },
        },
      );
      if (error) throw error;
      return (data?.data ?? null) as StatusUpdate | null;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: statusUpdatesKey(projectId) });
      qc.invalidateQueries({ queryKey: ["project", projectId] });
      qc.invalidateQueries({ queryKey: ["projects"] });
      // Any open portfolio roll-up reflects the new status.
      qc.invalidateQueries({ queryKey: ["portfolio-rollup"] });
      toast.success("Status posted");
    },
    onError: (err) => {
      toast.error(problemMessage(err, "Couldn't post the status update."));
    },
  });
}

/** Patch a project (used for inline start/end date edits on the Overview tab). */
export function useUpdateProject(projectId: string) {
  const qc = useQueryClient();
  const toast = useToast();
  const key = ["project", projectId];
  return useMutation({
    mutationFn: async (patch: ProjectPatch) => {
      const { data, error } = await api.PATCH("/api/v1/projects/{id}", {
        params: { path: { id: projectId } },
        // Cast: ProjectPatch allows null-to-clear; the generated type doesn't.
        body: patch as unknown as UpdateProjectBody,
      });
      if (error) throw error;
      return (data?.data ?? null) as Project | null;
    },
    onMutate: async (patch) => {
      await qc.cancelQueries({ queryKey: key });
      const prev = qc.getQueryData<Project>(key);
      if (prev) {
        const next: Project = { ...prev };
        if ("startDate" in patch) next.startDate = patch.startDate ?? undefined;
        if ("endDate" in patch) next.endDate = patch.endDate ?? undefined;
        if (patch.name !== undefined) next.name = patch.name;
        if (patch.description !== undefined)
          next.description = patch.description ?? undefined;
        if (patch.color !== undefined) next.color = patch.color;
        if (patch.ownerId !== undefined) next.ownerId = patch.ownerId;
        qc.setQueryData<Project>(key, next);
      }
      return { prev };
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev);
      toast.error(problemMessage(err, "Couldn't save that change."));
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: key });
      qc.invalidateQueries({ queryKey: ["projects"] });
    },
  });
}
