import createFetchClient, { type Client } from "openapi-fetch";
import type { paths } from "./schema.js";

export type { paths } from "./schema.js";
export type { components } from "./schema.js";

export type CairnClient = Client<paths>;

/** Name of the non-httpOnly cookie the API plants when it issues a session. */
export const CSRF_COOKIE = "cairn_csrf";
/** Header the API compares that cookie against on state-changing requests. */
export const CSRF_HEADER = "X-CSRF-Token";

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

/**
 * Read the double-submit CSRF token the API set alongside the session
 * (arch §6.4). Returns null on the server, or before a session exists.
 */
export function readCsrfToken(): string | null {
  if (typeof document === "undefined") return null;
  for (const part of document.cookie.split(";")) {
    const eq = part.indexOf("=");
    if (eq < 0) continue;
    if (part.slice(0, eq).trim() === CSRF_COOKIE) {
      return decodeURIComponent(part.slice(eq + 1).trim()) || null;
    }
  }
  return null;
}

/**
 * Headers echoing the CSRF token, for the few calls that bypass this client
 * (multipart uploads, plain `fetch`). Empty when there is no token to send.
 */
export function csrfHeaders(): Record<string, string> {
  const token = readCsrfToken();
  return token ? { [CSRF_HEADER]: token } : {};
}

/**
 * Create a typed Cairn API client.
 *
 * The web app talks to the API through a same-origin `/api/*` rewrite, so the
 * default `baseUrl` is empty (relative). Session state lives entirely in
 * httpOnly cookies, so every request sends credentials.
 *
 * Every state-changing request automatically echoes the `cairn_csrf` cookie in
 * the `X-CSRF-Token` header — the API rejects authenticated POST/PUT/PATCH/
 * DELETE without it (403). Reads and the pre-auth endpoints need no token.
 *
 * @param baseUrl Origin/prefix to prepend to request paths (default: "" — same-origin).
 */
export function createClient(baseUrl = ""): CairnClient {
  const client = createFetchClient<paths>({
    baseUrl,
    credentials: "include",
  });
  client.use({
    onRequest({ request }) {
      if (MUTATING_METHODS.has(request.method.toUpperCase())) {
        const token = readCsrfToken();
        if (token) request.headers.set(CSRF_HEADER, token);
      }
      return request;
    },
  });
  return client;
}
