# CLAUDE.md — Cairn operating manual

You (Claude Code) are the implementer of **Cairn**, a self-hosted, multi-tenant program & project
management tool. This file is the standing contract for every build session. The authoritative
detail lives in `docs/`; this file is the always-loaded summary plus the rules you must not break.

## What Cairn is (one paragraph)

An Asana-inspired work + portfolio tool for 10–50-person ops teams, self-hosted, where the portfolio
layer is a day-1 primitive (not a paywall). Built multi-tenant from the start so it can become a SaaS
without a rewrite. Full rationale: `docs/README.md` → `docs/01-product-spec.md`.

## Source of truth & precedence

1. `docs/01-product-spec.md` — what to build (MVP = its **Appendix A**, frozen).
2. `docs/02-architecture.md` — how it's built (ERD, API, auth, deployment, §10 scale tiers).
3. `docs/03-design.md` — UI/UX, tokens, motion, accessibility.
4. `docs/decisions.md` — the decision log (ADR-lite). **If code and a doc disagree, the doc wins; if
   two docs disagree, the spec wins; if you believe a decision is wrong, propose a new `docs/decisions.md`
   entry — do not silently diverge.**

## Stack (locked — D-002, D-018, D-019)

TypeScript end-to-end, pnpm monorepo. Next.js (App Router) web · NestJS API · PostgreSQL 16 ·
Valkey 8 (Redis-protocol; **never** Redis ≥7.4) · Traefik v3 TLS · PgBouncer · BullMQ worker ·
Prisma ORM · S3-compatible object storage in prod (local disk dev only). `packages/shared` holds
zod schemas + DTO types imported by both web and api — one definition, two enforcement points.

## Non-negotiable invariants (breaking one is a release blocker)

- **Tenant isolation is double-walled (D-023).** Every tenant-owned query filters by `organization_id`
  in app code AND every tenant table has a Postgres RLS policy; the API sets `app.current_org_id` per
  transaction (PgBouncer-safe). **A new table touching tenant data cannot merge without an RLS policy.**
  Never return, count, or mutate another tenant's rows.
- **Permissive licenses only (D-021).** Shipped runtime deps must be MIT/BSD/Apache/ISC/PostgreSQL/OFL/CC0.
  No GPL/AGPL/SSPL/RSAL/BUSL/FSL in shipped code. The CI license gate enforces this — don't add a dep
  that fails it (notably: no MinIO, no Redis ≥7.4, no Sentry server).
- **Single assignee, project-level status (D-009).** One task → one nullable `assignee_id` (no M:N).
  Status enum `on_track|at_risk|off_track|on_hold` lives on projects, never tasks.
- **Plain text everywhere (D-011).** Descriptions, comments, status bodies = plain text + client-side
  URL auto-linking. No rich-text serialization in the schema until Phase 2.
- **Cursor pagination only.** Never offset pagination.
- **Migrations are expand → migrate → contract** — always one version backward-compatible so rolling
  deploys and rollback-by-redeploy work.
- **Stateless api & worker.** No request state outside Postgres/Valkey/object storage. No local disk in prod.
- **UUIDv7 ids; web routes `/tasks/:id` + `?task=` overlay; no vanity slugs (D-008).**
- **Auth is a multi-provider framework (D-020).** Providers toggle via env (`AUTH_LOCAL_ENABLED`,
  `AUTH_LARK_ENABLED`); adding/toggling one touches only `apps/api/src/auth/**` + the login page. Never
  pass an IdP's token downstream — we mint our own first-party session.

## Definition of Done (per change — `docs/04-delivery-plan.md` §4)

1. Acceptance criteria demonstrated in the running app (not just a branch).
2. Tests for the layer touched: integration test for any new endpoint (real Postgres+Valkey via
   Testcontainers); unit tests for ordering/authz/roll-up logic; **new endpoints on tenant data get an
   isolation test**. CI green (lint, typecheck, unit, integration, build, Playwright smoke, `pnpm audit`,
   license gate).
3. Deployed to the staging compose stack.
4. Accessibility: axe scan + keyboard pass on new UI surfaces (WCAG 2.2 AA, light theme).
5. Scope: if it's not in spec Appendix A, it needs a `docs/decisions.md` entry + an equal-effort cut
   (one-in-one-out). Don't gold-plate.

## Commands (once the monorepo exists — M0 creates these)

- `pnpm dev` — web+api hot reload against compose postgres/valkey.
- `pnpm test` / `pnpm test:int` / `pnpm test:e2e` — Vitest unit / Testcontainers integration / Playwright.
- `pnpm lint && pnpm typecheck` — must pass before commit.
- `docker compose -f docker-compose.prod.yml up -d` — full staging/prod stack.
- `pnpm prisma migrate dev` — new migration (review the generated SQL).

## Conventions

- Controllers thin (parse → authorize → delegate → shape); business rules in domain services; cross-module
  comms via `EventEmitter2` domain events. Never import another module's service except through its exports.
- Validate at the edge with the shared zod schemas. RFC 9457 problem-details for errors; 404 for both
  missing and inaccessible (no existence leak).
- Match surrounding code; keep comment density as-is; no secrets in code or commits.

## Never do

- Never weaken or skip an RLS policy, or query tenant data without the org filter.
- Never add a copyleft/source-available dependency.
- Never expand MVP scope without a decision-log entry + a cut.
- Never invent an API/library signature — check the installed version; if unsure, say so.
- Never push to a branch other than the one you're told; never open a PR unless asked.
