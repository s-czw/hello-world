"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@cairn/api-client";
import { api, problemMessage } from "./api";
import { useToast } from "@/components/ui/Toast";

export type Portfolio = components["schemas"]["PortfolioResponse"];
export type Rollup = components["schemas"]["RollupResponse"];
export type RollupProject = components["schemas"]["RollupProjectResponse"];
export type RollupSummary = components["schemas"]["RollupSummary"];

export const portfoliosKey = ["portfolios"] as const;
export const portfolioKey = (id: string) => ["portfolio", id] as const;
/** Prefixed so usePostStatusUpdate's invalidate(["portfolio-rollup"]) refreshes it. */
export const rollupKey = (id: string) => ["portfolio-rollup", id] as const;

// ── Queries ──────────────────────────────────────────────────────────────

export function usePortfolios() {
  return useQuery({
    queryKey: portfoliosKey,
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/portfolios", {
        params: { query: { limit: 200 } },
      });
      if (error) throw error;
      return (data?.data ?? []) as Portfolio[];
    },
  });
}

export function usePortfolio(id: string) {
  return useQuery({
    queryKey: portfolioKey(id),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/portfolios/{id}", {
        params: { path: { id } },
      });
      if (error) throw error;
      return (data?.data ?? null) as Portfolio | null;
    },
    enabled: !!id,
  });
}

/** The single-aggregate roll-up: portfolio + per-project rows + summary counts. */
export function useRollup(id: string) {
  return useQuery({
    queryKey: rollupKey(id),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/portfolios/{id}/rollup", {
        params: { path: { id } },
      });
      if (error) throw error;
      return (data?.data ?? null) as Rollup | null;
    },
    enabled: !!id,
  });
}

// ── Mutations ──────────────────────────────────────────────────────────────

export function useCreatePortfolio() {
  const qc = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: async (vars: {
      name: string;
      description?: string;
      color?: string;
    }) => {
      const { data, error } = await api.POST("/api/v1/portfolios", {
        body: {
          name: vars.name,
          description: vars.description || undefined,
          color: vars.color || undefined,
        },
      });
      if (error) throw error;
      return (data?.data ?? null) as Portfolio | null;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: portfoliosKey });
      toast.success("Portfolio created");
    },
    onError: (err) => {
      toast.error(problemMessage(err, "Couldn't create the portfolio."));
    },
  });
}

/** Add a project to a portfolio (409 if already a member — surfaced as a toast). */
export function useAddProjectToPortfolio(portfolioId: string) {
  const qc = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: async (projectId: string) => {
      const { error } = await api.POST("/api/v1/portfolios/{id}/projects", {
        params: { path: { id: portfolioId } },
        body: { projectId },
      });
      if (error) throw error;
      return projectId;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: rollupKey(portfolioId) });
    },
    onError: (err) => {
      toast.error(problemMessage(err, "Couldn't add that project."));
    },
  });
}

/** Remove a project from a portfolio (never deletes the project itself). */
export function useRemoveProjectFromPortfolio(portfolioId: string) {
  const qc = useQueryClient();
  const toast = useToast();
  const key = rollupKey(portfolioId);
  return useMutation({
    mutationFn: async (projectId: string) => {
      const { error } = await api.DELETE(
        "/api/v1/portfolios/{id}/projects/{projectId}",
        { params: { path: { id: portfolioId, projectId } } },
      );
      if (error) throw error;
      return projectId;
    },
    onMutate: async (projectId) => {
      await qc.cancelQueries({ queryKey: key });
      const prev = qc.getQueryData<Rollup>(key);
      if (prev?.projects) {
        qc.setQueryData<Rollup>(key, {
          ...prev,
          projects: prev.projects.filter((p) => p.projectId !== projectId),
        });
      }
      return { prev };
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev);
      toast.error(problemMessage(err, "Couldn't remove that project."));
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: key });
    },
  });
}

type UpdateProjectBody = components["schemas"]["UpdateProjectRequest"];

/**
 * Patch a project's start/end dates from the schedule "no dates" tray, then
 * refresh the roll-up so the bar appears. Nullable dates accept null to clear
 * (the generated type lacks nullability, so cast at the fetch boundary).
 */
export function useUpdateProjectDates(portfolioId: string) {
  const qc = useQueryClient();
  const toast = useToast();
  const key = rollupKey(portfolioId);
  return useMutation({
    mutationFn: async (vars: {
      projectId: string;
      startDate?: string | null;
      endDate?: string | null;
    }) => {
      const patch: Record<string, string | null> = {};
      if ("startDate" in vars) patch.startDate = vars.startDate ?? null;
      if ("endDate" in vars) patch.endDate = vars.endDate ?? null;
      const { error } = await api.PATCH("/api/v1/projects/{id}", {
        params: { path: { id: vars.projectId } },
        body: patch as unknown as UpdateProjectBody,
      });
      if (error) throw error;
      return vars.projectId;
    },
    onSuccess: (projectId) => {
      qc.invalidateQueries({ queryKey: key });
      qc.invalidateQueries({ queryKey: ["projects"] });
      qc.invalidateQueries({ queryKey: ["project", projectId] });
    },
    onError: (err) => {
      toast.error(problemMessage(err, "Couldn't save those dates."));
    },
  });
}
