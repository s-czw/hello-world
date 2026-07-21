# Build Plan — Building Cairn with Claude Code

**Document:** 05-claude-build-plan.md
**Date:** 21 July 2026
**Status:** v1 — execution model (D-025). Complements 04-delivery-plan.md.

---

## Context

Documents 01–04 assumed a four-person human team over three 2-week sprints. In reality the team is
**you (product owner + reviewer) and Claude Code (implementer)**. This document translates the *same*
frozen scope (spec v1.2 Appendix A), the *same* architecture (v3.0), and the *same* launch criteria into
an AI-assisted build: how to drive Claude Code, in what order, with what guardrails, and where your
judgment stays in the loop.

Two things do **not** change because Claude writes the code: the **invariants** (tenant isolation, license
policy, plain-text, etc. — see `CLAUDE.md`) and the **launch criteria** (`docs/04-delivery-plan.md` §1).
If anything, they matter more — Claude produces code faster than you can eyeball it, so the automated gates
(tests, isolation drills, CI license gate) are what keep quality honest.

---

## 1. Operating model

**Roles collapse into you + Claude, with the four personas surviving as review lenses.**

| Old role (docs 01–04) | Becomes, when building with Claude |
|---|---|
| Product Engineer | Claude Code's default implementer mode — writes the app, tests, migrations |
| Tech Lead | An **architecture-review pass** (a `general-purpose`/code-review subagent, or `/code-review`) run against each milestone's diff; plus you approving one-way-door decisions |
| Designer | The `docs/03-design.md` tokens/specs as ground truth + an **axe/keyboard QA pass** and the `verify` skill driving the real UI |
| Project Director | **You**, holding scope (one-in-one-out), the decision log, and go/no-go — Claude never expands scope unprompted |

**Unit of work = a vertical slice, not a time-box.** Each milestone below is one or a few Claude Code
sessions that end in something you can run and review. No 2-week sprints; the pace is set by *your review
throughput*, not engineering hours. A slice is done only when it meets the Definition of Done in `CLAUDE.md`.

**The loop for every slice:**
1. **You** point Claude at the spec section + say "build slice X to DoD."
2. **Claude** implements code + tests + migration, runs the suite, deploys to the staging compose stack.
3. **A review pass** runs (`/code-review` and, for risky slices, an adversarial subagent — see §4).
4. **You** run the app (or have Claude drive it via the `verify` skill), read the diff, decide: merge or iterate.
5. Decisions/cuts get logged in `docs/decisions.md`.

**Parallelism, used deliberately (not by default):** independent slices (e.g. two unrelated UI views after
the schema exists) can be built in parallel subagents; anything touching the schema, auth, or RLS is built
serially so migrations don't collide. Adversarial verification (§4) is where multiple subagents earn their
keep.

---

## 2. Enabling setup — do this before any feature code (Milestone 0's first hour)

1. **`CLAUDE.md` is committed** (done) — Claude reads it every session; it encodes the invariants and DoD.
2. **`docs/` is the brief** — every build prompt references a spec section by number. Keep the decision log
   current; it's how Claude and you stay in sync across sessions.
3. **Guardrails wired into CI from the first commit**, so they gate everything after:
   - Lint, typecheck, unit, integration (Testcontainers Postgres+Valkey), Playwright smoke, `pnpm audit`.
   - **License gate** (license-checker allowlist — D-021).
   - **RLS-policy check**: a migration adding a tenant table without an RLS policy fails CI (grep/assert).
4. **Permissions & skills for smooth sessions:**
   - Allowlist the safe, repeated commands (`pnpm test`, `pnpm lint`, `docker compose ...`, `git status`)
     so Claude isn't prompt-gated mid-flow (see the `fewer-permission-prompts` skill).
   - Use `/code-review` on each milestone diff, `/security-review` before anything externally reachable,
     and the `verify` skill to drive the running app instead of trusting tests alone.
   - A `SessionStart` hook that boots the compose DB + runs migrations keeps every session reproducible.
5. **You provide secrets, never Claude**: `.env.prod`, the VM, DNS, S3 bucket creds, Lark app id/secret.
   Claude reads env var *names* from the docs; you fill values on the box.

---

## 3. Build sequence — milestones

Dependency spine (unchanged from `04` §3): **schema + RLS + auth + tokens → list view → task peek →
board → portfolio roll-up → schedule view → notifications/hardening**. Each milestone lists the spec IDs,
an example opening prompt you can paste, and the exit gate.

### M0 — Foundations & the walls (biggest, most important)
**Spec:** A1–A2, arch §1–§7, §10 invariants. **Goal:** a fresh install where an admin creates an org and
signs in, on the full compose stack, with tenant isolation and CI gates real from commit #1.
- Monorepo (pnpm), Next.js + NestJS skeletons, `packages/shared`.
- Compose: traefik, web, api, worker, pgbouncer, postgres, valkey, backup sidecar; dev + prod files.
- CI with all gates incl. license + RLS-policy checks.
- Prisma schema for the full MVP ERD (arch §3) + **RLS policies + per-transaction GUC middleware**.
- Auth: session model + `IdentityProvider` framework + `LocalPasswordProvider`; A1 first-run org with
  `SIGNUP_MODE`; seed script (demo org, sample project).
> **Prompt:** *"Read `CLAUDE.md` and `docs/02-architecture.md` §1–§7 and §3. Scaffold the pnpm monorepo,
> the compose stacks, and CI with the license and RLS-policy gates. Then implement the Prisma schema for
> the MVP ERD with RLS on every tenant table (per-transaction GUC), and A1/A2 auth with the local provider
> and the provider-toggle framework. Write integration tests including the tenant-isolation suite with the
> layer-1 bug drill. Deploy to the staging compose stack and show me the smoke passing."*
- **Exit:** fresh install → create org → log in works on staging; isolation suite (incl. bug drill) green;
  all CI gates green. **This is the gate that de-risks everything; don't rush past a red isolation test.**

### M1 — Work core: projects, sections, list view, task peek
**Spec:** A3–A4, B1–B3, C1. **Goal:** create a project, structure it with sections, work a task list with
inline edit + drag + quick-add, open the side-peek task panel.
> **Prompt:** *"Per `docs/01-product-spec.md` B1–B3 and C1 and `docs/03-design.md` §4.a/§4.c, implement
> teams, projects (create/edit/archive/overview), sections, the list view (5 fixed columns, inline edit,
> drag reorder + 'Move to…' menu, quick-add), and the task detail side-peek. Reuse the shared zod schemas.
> Integration tests for task CRUD + the move/reorder race; axe + keyboard pass on the list. Deploy and
> drive it with the verify skill."*
- **Exit:** DoD met; ordering race test green; keyboard path works; `/code-review` clean.

### M2 — Collaboration & My Tasks
**Spec:** B5, C2–C4, F1 (fan-out). **Goal:** board view, subtasks, comments/@mentions/activity,
attachments to S3, My Tasks (Overdue-first + status-nudge), notification rows being written.
> **Prompt:** *"Implement B4 board (same data as list), C2 subtasks, C3 comments + @mentions + activity
> stream, C4 attachments via the S3 StorageProvider with per-org quota, and B5 My Tasks including the PD-9
> owner status-nudge block. Wire F1 notification fan-out in the worker (rows only; inbox UI is M4). Tests
> per DoD; attachments stream through authz, never direct URLs."*
- **Exit:** DoD met; attachment authz test green; My Tasks nudge computed on read.

### M3 — Portfolio layer (the differentiator)
**Spec:** D1–D3, D4-floor. **Goal:** portfolios, live roll-up table, <60s status composer, date-ordered
schedule view.
> **Prompt:** *"Implement D1 portfolios, D2 roll-up table (status chips, 7-day staleness, progress,
> header summary), D3 status composer (4 colors, copy-previous, On-hold helper), and the D4 schedule-table
> floor with mini date-bars. The roll-up is a single SQL aggregate — test it against a seeded
> 50-project/10k-task dataset and assert <500ms."*
- **Exit:** DoD met; roll-up perf asserted on the seeded dataset (arch risk R5); org-scoped under RLS.

### M4 — Platform hardening & pilot readiness
**Spec:** E1 nav, F1 inbox, arch §6/§10. **Goal:** notifications inbox + sidebar (incl. org switcher for
multi-org users), `/metrics`, k6 with 2 api replicas through PgBouncer, backup+restore drill, rollback
rehearsal, seed the two pilot programs.
> **Prompt:** *"Implement F1 notifications inbox + the sidebar/org-switcher (E1), add the /metrics endpoint,
> write the k6 script and run it against 2 api replicas behind Traefik through PgBouncer, and produce the
> backup/restore + rollback runbook steps. Then walk me through launch criteria 1–9 with evidence."*
- **Exit:** **all launch criteria (`04` §1, incl. #9 isolation) green** → pilot go/no-go.

### Post-MVP, week 7: Lark sign-in
`LarkOAuthProvider` behind `AUTH_LARK_ENABLED` (arch §4.3) — the acceptance test proves it touches only
`apps/api/src/auth/**` + the login page.

---

## 4. The review loop — how quality survives fast code generation

For each milestone diff, in order:
1. **`/code-review`** on the working diff — correctness + simplification.
2. **Adversarial verification for the risky slices** — spawn independent subagents *prompted to break it*,
   not confirm it:
   - **Tenant isolation (every milestone touching tenant data):** "Try to read/mutate org B's data as an
     org A member — find one path that leaks." A leak found = release blocker (R9).
   - **Ordering races (M1/M2):** "Construct a concurrent-drag sequence that corrupts or duplicates sort keys."
   - **Auth (M0, week 7):** "Find a way to disable local auth and lock out every admin" (proves the
     break-glass CLI) and "reach a downstream route with only an IdP token."
3. **`verify` skill** drives the actual UI end-to-end (not just green tests) — the Designer/QA lens.
4. **`/security-review`** before the pilot (first multi-user surface) and before any external reachability.
5. **You** read the diff for the things machines miss: does it match the spec's *intent*, is it the
   simplest thing, did it quietly add scope.

---

## 5. Risk register — how "using Claude" changes it

Some `04` §5 risks shrink; new ones appear.

| Risk | Change | Mitigation |
|---|---|---|
| Single-engineer bus factor (R1) | **Gone** — Claude is reproducible; any session resumes from `docs/` + `CLAUDE.md` | Keep the docs the source of truth |
| **Spec drift / silent scope creep** | **New, higher** — Claude can generate plausible extras or diverge from the spec fast | Every prompt cites a spec section; DoD's one-in-one-out; you review diffs for unrequested scope; `CLAUDE.md` "never expand scope" |
| **Hallucinated APIs / wrong library signatures** | **New** | Tests as ground truth; `CLAUDE.md` "never invent a signature"; pinned versions; CI build catches it |
| **RLS/isolation gap slips in** (R9) | Same severity, **faster to introduce** | RLS-policy CI gate + adversarial isolation subagent every relevant milestone; leak = blocker |
| **License drift** (transitive dep) | Same | CI license gate on every PR |
| Re-architecture eats runway (R10) | **Softer** — code throughput is high | Your *review* throughput is now the real limit — batch reviews, don't let diffs pile unread |
| Design-build drift (R4) | Same | `docs/03-design.md` tokens are ground truth; axe/keyboard pass in DoD |
| Pilot adoption failure (R8) | **Unchanged** — this is a people problem, not a code one | The `04` §5 R8 plan stands; still your job |

**The meta-risk:** trusting output you haven't verified because it looks right. The gates in §4 exist
precisely so "looks right" is never the bar — "isolation drill passed, app driven, diff read" is.

---

## 6. What stays yours (human-in-the-loop)

- **Product decisions & scope** — what's in Appendix A, what gets cut when something runs long.
- **One-way-door approvals** — tenancy model, license swaps, anything in the arch decision summary.
- **The decision log** — you (or Claude at your direction) record every ratified choice.
- **Secrets & infrastructure** — VM, DNS, `.env.prod`, S3 creds, Lark app credentials.
- **Running the pilot** — seeding real programs, the adoption push, the go/no-go and the kill/pivot call.
- **Merge authority** — nothing reaches `main`/staging as "done" without your review of a runnable slice.

---

## 7. Cadence & honest expectations

The old plan's calendar (6 weeks) was human-velocity. With Claude Code, **code production stops being the
bottleneck; your review and verification throughput becomes it.** Expect milestones to land in focused
sessions (M0 is the heaviest — the walls and the schema — and worth the most care), with the calendar set
by how fast you can review runnable slices and make decisions. The 28 Aug pilot target from `04` stays
comfortable; if anything you gain slack — spend it on the isolation drills, the restore drill, and driving
the real UI, not on pulling deferred scope forward.

**Do not** let speed collapse the gates: a milestone isn't done because the code exists — it's done when it
meets the DoD, the adversarial passes are clean, and you've run it. That discipline is the whole plan.

---

## Appendix — first three prompts, verbatim

1. *"Read `CLAUDE.md` and `docs/`. Confirm you understand the invariants and the MVP scope (spec Appendix A),
   then start Milestone 0: scaffold the monorepo, compose stacks, and CI gates. Stop and show me the CI
   config and the compose topology before writing feature code."*
2. *"Implement the M0 Prisma schema with RLS on every tenant table and the per-transaction GUC middleware.
   Write the tenant-isolation integration suite including the layer-1 bug drill. Show me a failing bug-drill
   test first (to prove it catches leaks), then make it pass with RLS."*
3. *"Implement A1/A2 auth with the local provider and the provider-toggle framework. Then deploy the stack
   and walk me through creating an org and signing in on staging."*
