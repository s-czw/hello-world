/**
 * Fixed first-run credentials shared by the e2e specs.
 *
 * The suite runs against a freshly-reset `cairn` DB (scripts/run-e2e.mjs), so the
 * org is bootstrapped exactly once. Keeping the admin identity a constant lets a
 * later spec (a11y) attach to the session the smoke created instead of guessing a
 * randomly-generated address — and it still bootstraps itself when run alone.
 */
export const ADMIN = {
  org: "Acme Ops",
  name: "Ada Admin",
  email: "ada@cairn.test",
  password: "password123",
} as const;
