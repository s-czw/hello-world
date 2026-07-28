"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@cairn/api-client";
import { api } from "./api";

export type Notification = components["schemas"]["NotificationResponse"];

/** The self-contained render payload the API stores on each notification (F1). */
export interface NotificationPayload {
  actorName?: string;
  verb?: string;
  objectTitle?: string;
  snippet?: string;
  projectId?: string;
  status?: string;
}

export function payloadOf(n: Notification): NotificationPayload {
  const p = n.payload;
  return (p && typeof p === "object" ? (p as NotificationPayload) : {});
}

/**
 * One-line event sentence, built entirely from the stored payload so the inbox
 * renders without extra fetches (docs/03 §4.h). Examples:
 *   "Sara assigned you a task 'Order signage'"
 *   "Ada posted a status update on 'Fit-out Phase 2'"
 */
export function sentence(n: Notification): string {
  const p = payloadOf(n);
  const actor = p.actorName ?? "Someone";
  const verb = p.verb ?? "updated";
  const connector = n.type === "status_update" ? " on " : " ";
  const object = p.objectTitle ? `${connector}“${p.objectTitle}”` : "";
  return `${actor} ${verb}${object}`;
}

/**
 * Where a notification row navigates when clicked (docs/03 §4.h):
 *   task rows  → the peek in project context (`?task=`)
 *   status rows → the project Overview tab
 * Returns null when the payload lacks the ids needed to route.
 */
export function deepLink(n: Notification): string | null {
  const p = payloadOf(n);
  if (n.resourceType === "task") {
    if (!p.projectId || !n.resourceId) return null;
    return `/projects/${p.projectId}/list?task=${n.resourceId}`;
  }
  if (n.resourceType === "project") {
    if (!n.resourceId) return null;
    return `/projects/${n.resourceId}/overview`;
  }
  return null;
}

/** Sidebar bell badge — polls the unread count. Cap "9+" is applied at render. */
export function useUnreadCount() {
  return useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/notifications/unread-count");
      if (error) throw error;
      return (data?.data?.count ?? 0) as number;
    },
    // D-028: no realtime channel — poll for the badge (plus refetch-on-focus).
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
}

export function useNotifications() {
  return useQuery({
    queryKey: ["notifications", "list"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/notifications");
      if (error) throw error;
      return (data?.data ?? []) as Notification[];
    },
  });
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const { error } = await api.POST("/api/v1/notifications/{id}/read", {
        params: { path: { id } },
      });
      if (error) throw error;
    },
    onMutate: async (id: string) => {
      await qc.cancelQueries({ queryKey: ["notifications", "list"] });
      const prev = qc.getQueryData<Notification[]>(["notifications", "list"]);
      qc.setQueryData<Notification[]>(["notifications", "list"], (old) =>
        (old ?? []).map((n) => (n.id === id ? { ...n, read: true } : n)),
      );
      const prevCount = qc.getQueryData<number>([
        "notifications",
        "unread-count",
      ]);
      if (prev?.some((n) => n.id === id && !n.read) && prevCount) {
        qc.setQueryData(["notifications", "unread-count"], prevCount - 1);
      }
      return { prev, prevCount };
    },
    onError: (_e, _id, ctx) => {
      if (ctx?.prev) qc.setQueryData(["notifications", "list"], ctx.prev);
      if (ctx?.prevCount !== undefined) {
        qc.setQueryData(["notifications", "unread-count"], ctx.prevCount);
      }
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}

export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const { error } = await api.POST("/api/v1/notifications/read-all");
      if (error) throw error;
    },
    onMutate: async () => {
      await qc.cancelQueries({ queryKey: ["notifications"] });
      const prev = qc.getQueryData<Notification[]>(["notifications", "list"]);
      qc.setQueryData<Notification[]>(["notifications", "list"], (old) =>
        (old ?? []).map((n) => ({ ...n, read: true })),
      );
      qc.setQueryData(["notifications", "unread-count"], 0);
      return { prev };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.prev) qc.setQueryData(["notifications", "list"], ctx.prev);
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}
