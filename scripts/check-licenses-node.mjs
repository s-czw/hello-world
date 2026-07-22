#!/usr/bin/env node
// Cairn license gate (Node / pnpm workspace) — enforces D-021.
//
// Shipped runtime deps must be permissive (MIT/BSD/Apache/ISC/PostgreSQL/OFL/CC0
// and equivalents). Strong copyleft / source-available licenses (GPL/AGPL/SSPL/
// RSAL/BUSL/FSL) are a hard FAIL. Weak copyleft (MPL/LGPL/EPL/CDDL) is a WARN —
// permitted only for dev tooling, never a shipped runtime dep. Unknown licenses
// FAIL so a mis-declared dep can never slip through silently.
//
// Usage:
//   node scripts/check-licenses-node.mjs            # scan whole workspace
//   node scripts/check-licenses-node.mjs --json     # machine-readable summary
//
// Exit 0 = clean (warnings allowed), non-zero = a disallowed license was found.

import { init as licenseInit } from "license-checker-rseidelsohn";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const AS_JSON = process.argv.includes("--json");

// ---------------------------------------------------------------------------
// Policy
// ---------------------------------------------------------------------------

// Permissive SPDX identifiers we ship without question (D-021).
const ALLOW = new Set([
  "MIT",
  "MIT-0",
  "ISC",
  "0BSD",
  "BSD",
  "BSD-2-Clause",
  "BSD-3-Clause",
  "BSD-3-Clause-Clear",
  "Apache-2.0",
  "Apache 2.0",
  "PostgreSQL",
  "Python-2.0",
  "Unlicense",
  "WTFPL",
  "CC0-1.0",
  "CC-BY-3.0",
  "CC-BY-4.0",
  "OFL-1.1",
  "SIL OFL 1.1",
  "Zlib",
  "BlueOak-1.0.0",
]);

// Weak copyleft — allowed for dev/build tooling only, surfaced as a WARN.
const WARN = new Set([
  "MPL-2.0",
  "MPL-1.1",
  "EPL-1.0",
  "EPL-2.0",
  "CDDL-1.0",
  "CDDL-1.1",
  "LGPL-2.1",
  "LGPL-2.1-only",
  "LGPL-2.1-or-later",
  "LGPL-3.0",
  "LGPL-3.0-only",
  "LGPL-3.0-or-later",
]);

// Strong copyleft / source-available — hard FAIL under D-021.
const FAIL_SUBSTRINGS = [
  "GPL-2.0",
  "GPL-3.0",
  "GPL-1.0",
  "AGPL",
  "SSPL",
  "RSAL",
  "BUSL",
  "Business Source",
  "FSL-",
  "Commons Clause",
  "CC-BY-NC",
  "CC-BY-SA",
  "Elastic-2.0",
  "Prosperity",
];

// Our own private workspace packages report as UNLICENSED — never our concern.
const EXCLUDE_PACKAGES = new Set(["cairn", "web", "@cairn/api-client"]);

// Per-package overrides for genuinely permissive deps that ship an ambiguous or
// missing SPDX id. Key is "name@version" (or "name" for any version).
const PACKAGE_EXCEPTIONS = {
  // (none needed at present — every dep resolves cleanly against ALLOW/WARN)
};

// ---------------------------------------------------------------------------

/** Split a compound SPDX expression into its atomic license tokens. */
function atoms(expr) {
  return expr
    .replace(/[()]/g, " ")
    .split(/\s+(?:OR|AND|WITH)\s+/i)
    .map((s) => s.trim())
    .filter(Boolean);
}

function classify(name, versioned, licenseExpr) {
  const key = versioned;
  const bare = name;
  if (EXCLUDE_PACKAGES.has(bare)) return { level: "skip" };
  if (PACKAGE_EXCEPTIONS[key] || PACKAGE_EXCEPTIONS[bare]) return { level: "allow" };

  const expr = (licenseExpr || "UNKNOWN").toString();

  // Hard fail wins over everything.
  for (const bad of FAIL_SUBSTRINGS) {
    if (expr.toUpperCase().includes(bad.toUpperCase())) {
      return { level: "fail", reason: `matches banned license '${bad}'` };
    }
  }

  const parts = atoms(expr);
  // An OR expression is satisfied if ANY branch is permissive.
  const isOr = /\sOR\s/i.test(expr);
  const permissive = parts.filter((p) => ALLOW.has(p));
  const weak = parts.filter((p) => WARN.has(p));

  if (isOr && permissive.length > 0) return { level: "allow" };
  // AND / single: every atom must be at least permissive; weak downgrades to warn.
  const unknown = parts.filter((p) => !ALLOW.has(p) && !WARN.has(p));
  if (unknown.length > 0) {
    return { level: "fail", reason: `unrecognized license '${expr}'` };
  }
  if (weak.length > 0) return { level: "warn", reason: `weak copyleft '${expr}' (dev/build only)` };
  return { level: "allow" };
}

licenseInit({ start: ROOT }, (err, packages) => {
  if (err) {
    console.error("[licenses:node] license-checker failed:", err.message || err);
    process.exit(2);
  }

  const fails = [];
  const warns = [];
  let allowed = 0;
  let skipped = 0;

  for (const [versioned, info] of Object.entries(packages)) {
    const at = versioned.lastIndexOf("@");
    const name = at > 0 ? versioned.slice(0, at) : versioned;
    const res = classify(name, versioned, info.licenses);
    if (res.level === "skip") {
      skipped++;
    } else if (res.level === "fail") {
      fails.push({ pkg: versioned, license: String(info.licenses), reason: res.reason });
    } else if (res.level === "warn") {
      warns.push({ pkg: versioned, license: String(info.licenses), reason: res.reason });
      allowed++;
    } else {
      allowed++;
    }
  }

  if (AS_JSON) {
    console.log(JSON.stringify({ allowed, skipped, warns, fails }, null, 2));
  } else {
    console.log(
      `[licenses:node] scanned ${Object.keys(packages).length} packages ` +
        `(${allowed} permissive, ${skipped} own, ${warns.length} warn, ${fails.length} fail)`,
    );
    for (const w of warns) console.log(`  WARN  ${w.pkg} — ${w.reason}`);
    for (const f of fails) console.log(`  FAIL  ${f.pkg} — ${f.reason}`);
  }

  if (fails.length > 0) {
    console.error(
      `\n[licenses:node] ${fails.length} disallowed license(s) found — see D-021 in CLAUDE.md.`,
    );
    process.exit(1);
  }
  console.log("[licenses:node] OK — all runtime licenses are permissive.");
  process.exit(0);
});
