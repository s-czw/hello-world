"use client";

import { createClient, type CairnClient } from "@cairn/api-client";

/**
 * Single browser-side API client. baseUrl is empty so requests hit the
 * same-origin `/api/*` path, which Next rewrites to the API (see next.config).
 * Session lives in httpOnly cookies; the client sends credentials on every call.
 */
export const api: CairnClient = createClient("");

/**
 * Refresh-on-401: the access JWT is short-lived (15 min) but the refresh
 * cookie lasts 7 days. When an authenticated GET comes back 401, silently
 * rotate the session once via /auth/refresh and replay the request. A single
 * in-flight refresh is shared across concurrent 401s. Auth endpoints are
 * excluded so a failed refresh can't loop.
 */
let refreshing: Promise<boolean> | null = null;

function refreshOnce(): Promise<boolean> {
  if (!refreshing) {
    refreshing = fetch("/api/v1/auth/refresh", {
      method: "POST",
      credentials: "include",
    })
      .then((r) => r.ok)
      .catch(() => false)
      .finally(() => {
        // Allow the next 401 (after this window) to trigger a fresh attempt.
        setTimeout(() => {
          refreshing = null;
        }, 0);
      });
  }
  return refreshing;
}

api.use({
  async onResponse({ request, response }) {
    if (
      response.status === 401 &&
      request.method === "GET" &&
      !request.url.includes("/api/v1/auth/")
    ) {
      const ok = await refreshOnce();
      if (ok) {
        return fetch(request.clone());
      }
    }
    return response;
  },
});

/** Narrow a Problem+JSON error body to a human message. */
export function problemMessage(error: unknown, fallback: string): string {
  if (error && typeof error === "object") {
    const e = error as Record<string, unknown>;
    if (typeof e.detail === "string" && e.detail) return e.detail;
    if (typeof e.title === "string" && e.title) return e.title;
    if (Array.isArray(e.errors) && e.errors.length > 0) {
      const first = e.errors[0] as Record<string, unknown>;
      if (typeof first?.message === "string") return first.message;
    }
  }
  return fallback;
}
