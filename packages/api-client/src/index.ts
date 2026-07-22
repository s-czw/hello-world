import createFetchClient, { type Client } from "openapi-fetch";
import type { paths } from "./schema.js";

export type { paths } from "./schema.js";
export type { components } from "./schema.js";

export type CairnClient = Client<paths>;

/**
 * Create a typed Cairn API client.
 *
 * The web app talks to the API through a same-origin `/api/*` rewrite, so the
 * default `baseUrl` is empty (relative). Session state lives entirely in
 * httpOnly cookies, so every request sends credentials.
 *
 * @param baseUrl Origin/prefix to prepend to request paths (default: "" — same-origin).
 */
export function createClient(baseUrl = ""): CairnClient {
  return createFetchClient<paths>({
    baseUrl,
    credentials: "include",
  });
}
