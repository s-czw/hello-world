import React from "react";

// Match http(s):// and bare www. URLs; trailing punctuation is trimmed below.
const URL_RE = /(https?:\/\/[^\s]+|www\.[^\s]+)/gi;
const TRAILING = /[.,;:!?)\]}'"]+$/;

/**
 * Render plain text with URLs auto-linked (docs/03 §2.6, PD-8 — no rich text in
 * the schema; linking is a display-only concern). Preserves newlines.
 */
export function linkify(text: string): React.ReactNode {
  if (!text) return null;
  const out: React.ReactNode[] = [];
  let key = 0;
  for (const line of text.split("\n")) {
    if (key > 0) out.push(<br key={`br-${key}`} />);
    let last = 0;
    for (const m of line.matchAll(URL_RE)) {
      const start = m.index ?? 0;
      let url = m[0];
      let trail = "";
      const tm = url.match(TRAILING);
      if (tm) {
        trail = tm[0];
        url = url.slice(0, url.length - trail.length);
      }
      if (start > last) out.push(line.slice(last, start));
      const href = url.startsWith("www.") ? `https://${url}` : url;
      out.push(
        <a key={`a-${key}-${start}`} href={href} target="_blank" rel="noopener noreferrer">
          {url}
        </a>,
      );
      if (trail) out.push(trail);
      last = start + m[0].length;
    }
    if (last < line.length) out.push(line.slice(last));
    key++;
  }
  return out;
}
