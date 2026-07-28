#!/usr/bin/env node
// TEST-ONLY database reset for the E2E smoke. Drops and recreates the `public`
// schema on the target Postgres so the next API boot re-runs Flyway from clean
// (equivalent to `flyway clean` + migrate-on-boot). MUST run BEFORE the API is
// started — the smoke exercises first-run /setup bootstrap, which requires an
// empty organizations table.
//
// Never point this at a production database. Guarded to refuse anything that is
// not obviously a local/test database name unless CAIRN_RESET_FORCE=1.
//
// Config (env): DATABASE_URL, or PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE.
// Defaults to the local dev DB: postgres://cairn:cairn@127.0.0.1:5432/cairn

import { spawnSync } from "node:child_process";

const url = process.env.DATABASE_URL || "postgres://cairn:cairn@127.0.0.1:5432/cairn";
let host = process.env.PGHOST;
let port = process.env.PGPORT;
let user = process.env.PGUSER;
let password = process.env.PGPASSWORD;
let database = process.env.PGDATABASE;

if (!database) {
  try {
    const u = new URL(url);
    host = host || u.hostname;
    port = port || u.port || "5432";
    user = user || decodeURIComponent(u.username || "cairn");
    password = password || decodeURIComponent(u.password || "cairn");
    database = database || u.pathname.replace(/^\//, "") || "cairn";
  } catch (e) {
    console.error("[reset-db] invalid DATABASE_URL:", e.message);
    process.exit(2);
  }
}
host = host || "127.0.0.1";
port = port || "5432";
user = user || "cairn";
password = password || "cairn";

const SAFE = /(^|_)(cairn|test|dev|ci|smoke|e2e)($|_)/i;
if (!SAFE.test(database) && process.env.CAIRN_RESET_FORCE !== "1") {
  console.error(
    `[reset-db] refusing to reset database "${database}" (does not look local/test). ` +
      "Set CAIRN_RESET_FORCE=1 to override.",
  );
  process.exit(2);
}

const sql = "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;";
console.log(`[reset-db] resetting public schema on ${host}:${port}/${database} …`);

const r = spawnSync(
  "psql",
  ["-h", host, "-p", String(port), "-U", user, "-d", database, "-v", "ON_ERROR_STOP=1", "-c", sql],
  { env: { ...process.env, PGPASSWORD: password }, stdio: ["ignore", "inherit", "inherit"] },
);

if (r.error) {
  console.error("[reset-db] failed to run psql:", r.error.message);
  process.exit(2);
}
if (r.status !== 0) {
  console.error(`[reset-db] psql exited ${r.status}`);
  process.exit(r.status || 1);
}
console.log("[reset-db] done — schema is clean; API boot will re-run Flyway.");
