"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { components } from "@cairn/api-client";

export type Me = components["schemas"]["MeResponse"];

/**
 * Current session. Returns the Me payload when authenticated, null on 401.
 * Used by the shell and admin gates; refetches on window focus (D-028: no
 * realtime channel — refetch-on-focus is the freshness model).
 */
export function useMe() {
  return useQuery<Me | null>({
    queryKey: ["me"],
    queryFn: async () => {
      const { data, error, response } = await api.GET("/api/v1/auth/me");
      if (response.status === 401) return null;
      if (error) throw error;
      return (data?.data ?? null) as Me | null;
    },
    staleTime: 30_000,
    retry: false,
  });
}

export function isAdmin(me: Me | null | undefined): boolean {
  return me?.role === "admin";
}
