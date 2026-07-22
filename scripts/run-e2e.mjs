#!/usr/bin/env node
// E2E smoke orchestrator (root `pnpm test:e2e`).
//
// 1. Reset the target Postgres (scripts/reset-db.mjs) — clean schema.
// 2. Boot the API jar (Flyway migrates the fresh schema on start); wait for /readyz.
// 3. Boot the web server (`next start`); wait for :3000.
// 4. Run the Playwright smoke (apps/web, chromium from PLAYWRIGHT_BROWSERS_PATH).
// 5. Tear both servers down; propagate the Playwright exit code.
//
// Prereqs (the orchestrator does NOT build): the API jar must exist
// (`cd apps/api && ./mvnw -q -DskipTests package`) and web must be built
// (`pnpm --filter web build`). CI runs those steps before calling this.
//
// Env overrides: API_JAR, DB_URL, API_PORT (8080), WEB_PORT (3000),
// PLAYWRIGHT_BROWSERS_PATH (defaults to /opt/pw-browsers if unset).

import { spawn, spawnSync } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { setTimeout as sleep } from "node:timers/promises";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const API_DIR = path.join(ROOT, "apps", "api");
const WEB_DIR = path.join(ROOT, "apps", "web");

const API_PORT = process.env.API_PORT || "8080";
const WEB_PORT = process.env.WEB_PORT || "3000";
const DB_URL = process.env.DB_URL || "jdbc:postgresql://127.0.0.1:5432/cairn";
// Prefer an explicit PLAYWRIGHT_BROWSERS_PATH; fall back to the harness's
// pre-installed location only if it exists (local dev). In CI it is left unset so
// Playwright uses its own installed cache (~/.cache/ms-playwright).
const BROWSERS_PATH =
  process.env.PLAYWRIGHT_BROWSERS_PATH ||
  (existsSync("/opt/pw-browsers") ? "/opt/pw-browsers" : undefined);

const procs = [];
function shutdown() {
  for (const p of procs.splice(0)) {
    try {
      if (p.pid && !p.killed) process.kill(-p.pid, "SIGTERM");
    } catch {
      /* ignore */
    }
  }
}
process.on("exit", shutdown);
process.on("SIGINT", () => {
  shutdown();
  process.exit(130);
});

function findJar() {
  if (process.env.API_JAR) return process.env.API_JAR;
  const target = path.join(API_DIR, "target");
  if (!existsSync(target)) return null;
  const jars = readdirSync(target).filter(
    (f) => f.endsWith(".jar") && !f.endsWith("-sources.jar") && !f.includes("original"),
  );
  return jars.length ? path.join(target, jars[0]) : null;
}

async function waitFor(name, url, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(2000) });
      if (res.ok) {
        console.log(`[e2e] ${name} ready (${url})`);
        return;
      }
    } catch {
      /* not up yet */
    }
    await sleep(500);
  }
  throw new Error(`[e2e] timed out waiting for ${name} at ${url}`);
}

function run(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, { stdio: "inherit", ...opts });
  if (r.status !== 0) throw new Error(`${cmd} ${args.join(" ")} exited ${r.status}`);
}

async function main() {
  // 1) reset DB
  console.log("[e2e] === reset database ===");
  run("node", [path.join(__dirname, "reset-db.mjs")], {
    env: { ...process.env, DATABASE_URL: process.env.DATABASE_URL || "postgres://cairn:cairn@127.0.0.1:5432/cairn" },
  });

  // 2) boot API
  const jar = findJar();
  if (!jar || !existsSync(jar)) {
    throw new Error(
      "[e2e] API jar not found. Build it first: cd apps/api && ./mvnw -q -DskipTests package",
    );
  }
  console.log(`[e2e] === boot API (${path.basename(jar)}) ===`);
  const api = spawn("java", ["-jar", jar], {
    cwd: API_DIR,
    detached: true,
    stdio: "inherit",
    env: {
      ...process.env,
      SERVER_PORT: API_PORT,
      SPRING_DATASOURCE_URL: DB_URL,
      SPRING_DATASOURCE_USERNAME: process.env.SPRING_DATASOURCE_USERNAME || "cairn",
      SPRING_DATASOURCE_PASSWORD: process.env.SPRING_DATASOURCE_PASSWORD || "cairn",
      AUTH_RATELIMIT_ENABLED: "false",
    },
  });
  procs.push(api);
  await waitFor("API", `http://127.0.0.1:${API_PORT}/readyz`, 90_000);

  // 3) boot web
  console.log("[e2e] === boot web (next start) ===");
  const web = spawn("pnpm", ["--filter", "web", "start"], {
    cwd: ROOT,
    detached: true,
    stdio: "inherit",
    env: {
      ...process.env,
      PORT: WEB_PORT,
      API_ORIGIN: `http://127.0.0.1:${API_PORT}`,
    },
  });
  procs.push(web);
  await waitFor("web", `http://127.0.0.1:${WEB_PORT}/login`, 60_000);

  // 4) run Playwright
  console.log("[e2e] === run Playwright smoke ===");
  const pwEnv = {
    ...process.env,
    E2E_BASE_URL: `http://127.0.0.1:${WEB_PORT}`,
  };
  if (BROWSERS_PATH) pwEnv.PLAYWRIGHT_BROWSERS_PATH = BROWSERS_PATH;
  const pw = spawnSync("pnpm", ["--filter", "web", "exec", "playwright", "test"], {
    cwd: ROOT,
    stdio: "inherit",
    env: pwEnv,
  });

  shutdown();
  process.exit(pw.status ?? 1);
}

main().catch((e) => {
  console.error(e.message || e);
  shutdown();
  process.exit(1);
});
