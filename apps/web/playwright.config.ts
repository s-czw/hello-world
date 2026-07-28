import { defineConfig, devices } from "@playwright/test";

/**
 * E2E config for the Cairn smoke + a11y suites.
 *
 * The suite expects the API (:8080) and web (:3000) servers to already be running
 * against a freshly-reset `cairn` database — the orchestrator `scripts/run-e2e.mjs`
 * (root `pnpm test:e2e`) resets the DB, boots both servers, then invokes this.
 * We deliberately do NOT declare a `webServer` here: the DB must be reset *before*
 * the API boots (the smoke tests first-run bootstrap), which Playwright's webServer
 * lifecycle cannot guarantee relative to globalSetup.
 *
 * Two projects, both chromium:
 *   `smoke` — the functional happy path, which owns first-run bootstrap.
 *   `a11y`  — the axe scan; declared `dependencies: ["smoke"]` so it always runs
 *             *after* bootstrap has happened (it seeds its own content and logs in
 *             with the shared admin account, and bootstraps itself when run alone).
 * `--project=a11y` therefore still pulls the smoke in first; `pnpm test:e2e` runs both.
 *
 * Uses the pre-installed chromium via PLAYWRIGHT_BROWSERS_PATH (set by the harness /
 * the orchestrator); browsers are never downloaded here.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  outputDir: "test-results",
  use: {
    baseURL: process.env.E2E_BASE_URL || "http://127.0.0.1:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    viewport: { width: 1280, height: 900 },
  },
  projects: [
    {
      name: "smoke",
      testMatch: /smoke\.spec\.ts/,
      use: { ...devices["Desktop Chrome"], channel: undefined },
    },
    {
      name: "a11y",
      testMatch: /a11y\.spec\.ts/,
      dependencies: ["smoke"],
      // Deterministic layout for the contrast/target-size checks: no in-flight motion.
      use: {
        ...devices["Desktop Chrome"],
        channel: undefined,
        contextOptions: { reducedMotion: "reduce" },
      },
    },
  ],
});
