import React from "react";
import type { User } from "./tasks";
import styles from "@/components/task/task.module.css";

const UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const EMAIL = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";
// A mention token (@uuid / @email) OR a URL. Mentions come first so the "@"
// isn't swallowed by an email-inside-URL edge.
const TOKEN_RE = new RegExp(
  `@(?:${UUID}|${EMAIL})|https?:\\/\\/[^\\s]+|www\\.[^\\s]+`,
  "gi",
);
const TRAILING = /[.,;:!?)\]}'"]+$/;

function displayName(token: string, users: User[]): string | null {
  const raw = token.slice(1); // drop leading "@"
  const byId = users.find((u) => u.id === raw);
  if (byId) return byId.name ?? byId.email ?? raw;
  const lower = raw.toLowerCase();
  const byEmail = users.find((u) => (u.email ?? "").toLowerCase() === lower);
  if (byEmail) return byEmail.name ?? byEmail.email ?? raw;
  return null;
}

/**
 * Render a plain-text comment body: @mention tokens (`@<uuid>` / `@<email>`, the
 * forms the API parses) become `@Name` chips, and URLs auto-link (PD-8, D-011 —
 * the stored body stays plain text; this is a display-only concern). Newlines
 * are preserved.
 */
export function renderCommentBody(text: string, users: User[] = []): React.ReactNode {
  if (!text) return null;
  const out: React.ReactNode[] = [];
  let key = 0;
  for (const line of text.split("\n")) {
    if (key > 0) out.push(<br key={`br-${key}`} />);
    let last = 0;
    for (const m of line.matchAll(TOKEN_RE)) {
      const start = m.index ?? 0;
      const tok = m[0];
      if (start > last) out.push(line.slice(last, start));

      if (tok.startsWith("@")) {
        const name = displayName(tok, users);
        if (name) {
          out.push(
            <span key={`mn-${key}-${start}`} className={styles.mention}>
              @{name}
            </span>,
          );
        } else {
          out.push(tok); // unknown mention → show as typed
        }
      } else {
        let url = tok;
        let trail = "";
        const tm = url.match(TRAILING);
        if (tm) {
          trail = tm[0];
          url = url.slice(0, url.length - trail.length);
        }
        const href = url.startsWith("www.") ? `https://${url}` : url;
        out.push(
          <a
            key={`a-${key}-${start}`}
            className="autolink"
            href={href}
            target="_blank"
            rel="noopener noreferrer"
          >
            {url}
          </a>,
        );
        if (trail) out.push(trail);
      }
      last = start + tok.length;
    }
    if (last < line.length) out.push(line.slice(last));
    key++;
  }
  return out;
}
