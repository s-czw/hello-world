# Build Plan — Building Cairn with Claude Code

**Document:** 05-claude-build-plan.md
**Date:** 21 July 2026
**Status:** v1 — execution model (D-025). Complements 04-delivery-plan.md.

---

## Context

Documents 01–04 assumed a four-person human team over three 2-week sprints. In reality the team is
**you (product owner + reviewer) and Claude Code (implementer)**. This document translates the *same*
frozen scope (spec v1.2 Appendix A, **single-org per D-028**), the architecture (v3.2), and the
pilot-readiness bar into an AI-assisted build: how to drive Claude Code, in what order, with what guardrails,
and where your judgment stays in the loop.

**This is the delivery plan of record (D-028).** The team is **you + Claude Code**; the four personas of
`docs/04` survive only as review lenses (§1), and `docs/04`'s human-team RACI, PE-day/TL-day capacity, and
2-week-sprint calendar are retired. The MVP is **single-org** — no multi-tenancy/RLS/SaaS work until the
gated commercialization phase.

Two things do **not** change because Claude writes the code: the **invariants** (license policy, plain-text,
single assignee, cursor pagination, expand→migrate→contract — see `CLAUDE.md`) and the **pilot-readiness
bar** (§7). If anything they matter more — Claude produces code faster than you can eyeball it, so the
automated gates (tests, CI license gate, the ordering-race test) are what keep quality honest.

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
the schema exists) can be built in parallel subagents; anything touching the schema or auth is built
serially so migrations don't collide. Adversarial verification (§4) is where multiple subagents earn their
keep.

---

## 2. Enabling setup — do this before any feature code (M1's first hour)

1. **`CLAUDE.md` is committed** (done) — Claude reads it every session; it encodes the invariants and DoD.
2. **`docs/` is the brief** — every build prompt references a spec section by number. Keep the decision log
   current; it's how Claude and you stay in sync across sessions.
3. **Guardrails wired into CI from the first commit**, so they gate everything after:
   - Lint, typecheck, unit, integration (Testcontainers Postgres+Valkey), Playwright smoke, `pnpm audit`.
   - **License gate** (license-checker allowlist — D-021).
   - **Org-filter check**: every tenant-owned read/write goes through the shared org-scoped query helper (lint/review); RLS is deferred to commercialization (D-028), but the `organization_id` column + filter ship now so it's never retrofitted.
4. **Permissions & skills for smooth sessions:**
   - Allowlist the safe, repeated commands (`pnpm test`, `pnpm lint`, `docker compose ...`, `git status`)
     so Claude isn't prompt-gated mid-flow (see the `fewer-permission-prompts` skill).
   - Use `/code-review` on each milestone diff, `/security-review` before anything externally reachable,
     and the `verify` skill to drive the running app instead of trusting tests alone.
   - A `SessionStart` hook that boots the compose DB + runs migrations keeps every session reproducible.
5. **You provide secrets, never Claude**: `.env.prod`, the VM, DNS, S3 bucket creds, Lark app id/secret.
   Claude reads env var *names* from the docs; you fill values on the box.

---

## 3. Build sequence — milestones (single-org, per D-028)

Dependency spine: **lean single-org foundations + schema (org_id + app filter) + auth → list view → task
peek → My Tasks** (that's the 2-week M1) **→ portfolio layer + board (M2) → collaboration + notifications +
polish (M3) → internal pilot**. No RLS/PgBouncer/worker-split/S3/metrics/multi-org UI in the MVP — those are
the gated commercialization phase (arch §10). Each milestone lists spec IDs, a paste-ready prompt, and an exit gate.

### M1 — Minimum viable product, ~2 weeks (the hard target)
**Goal:** a single-org tool you can start *dogfooding as a task manager* — sign in, make projects with
sections, work a task list, see everything assigned to you. **Spec:** A1–A2 (single-org), A4, B1–B3, C1, B5.
- **Lean foundations:** polyglot monorepo (Next.js pnpm web + Spring Boot Maven api + generated `api-client`);
  compose stack **traefik · web · api · postgres · valkey · backup** (no pgbouncer, no separate worker —
  jobs run in-process); CI = `mvn verify` + web build + Playwright smoke + OpenAPI-drift + license gate.
- **Schema (Flyway) + jOOQ codegen** for the M1 entities, with `organization_id` on tenant tables and the
  **app-layer org filter** (the cheap seam; RLS deferred). Single-org: first-run creates the one org + admin.
- **Auth:** session model + `LocalPasswordProvider` behind the `IdentityProvider` seam (framework kept so
  Lark/Entra drop in later); simple add-user / copy-paste invite. No `SIGNUP_MODE`/multi-org/org-switcher.
- **Core work management:** teams, projects (create/edit/archive), sections, **list view** (5 fixed columns,
  inline edit, drag reorder + "Move to…" menu, quick-add), **task detail side-peek**, **My Tasks** (Overdue-first).
> **Prompt:** *"Read `CLAUDE.md` and `docs/02-architecture.md` §1–§7 (single-org per D-028 — app-layer
> `organization_id` filter, no RLS/PgBouncer/worker/S3/metrics yet) and `docs/01-product-spec.md` A1–A2, A4,
> B1–B3, C1, B5. Scaffold the monorepo + lean compose stack + CI (license + OpenAPI-drift gates). Implement
> the Flyway schema (org_id + app filter) with jOOQ codegen, single-org first-run + local auth behind the
> IdentityProvider seam, then projects/sections/tasks, the list view, the task side-peek, and My Tasks.
> JUnit+Testcontainers tests for task CRUD + the move/reorder race; axe + keyboard pass. Deploy to the local
> prod compose stack and drive it with the verify skill. Stop and show me the compose topology + CI before feature code."*
- **Exit (= usable MVP):** on the running stack, sign in → create a project → add sections → quick-add and
  inline-edit tasks → drag reorder → open the side-peek → see My Tasks. CI green; `/code-review` clean;
  ordering-race test green. **You start using it for real work here.**

### M2 — The portfolio layer (the differentiator) + board, ~2 weeks
**Spec:** D1–D3, D4-floor, B4. **Goal:** the reason the product exists — group projects into a portfolio,
post <60s status updates, read a live roll-up; plus the board view of the same task data.
> **Prompt:** *"Implement B4 board (same data as list, drag between columns), then D1 portfolios, D2 roll-up
> table (status chips, 7-day staleness, progress, header summary), D3 status composer (4 colors,
> copy-previous, On-hold helper), and the D4 date-ordered schedule table with mini date-bars. The roll-up is
> a single jOOQ SQL aggregate — test it against a seeded 50-project/10k-task dataset and assert <500ms (R5)."*
- **Exit:** DoD met; roll-up perf asserted on the seeded dataset; board↔list share one data model.

### M3 — Collaboration, notifications & polish → pilot-ready, ~1–2 weeks
**Spec:** C2–C4, C3 activity, F1, E1, onboarding. **Goal:** the team can collaborate in it, and it's
hardened enough for the whole-team internal pilot.
> **Prompt:** *"Implement C2 subtasks, C3 comments + @mentions + activity stream, C4 attachments (local-disk
> StorageProvider, per-file limit, streamed through authz — never direct URLs), F1 in-app notifications
> (fan-out via in-process @Async + the inbox UI), E1 sidebar nav, and the first-run/empty-state onboarding.
> Run the backup + restore drill and the rollback rehearsal. Then walk me through the pilot readiness checklist."*
- **Exit:** pilot-readiness checklist green (see §7) → whole-team internal pilot.

### Commercialization phase (gated — only if the pilot proves value; arch §10, D-028)
Not built now. When/if the pivot is made: activate multi-tenancy (RLS + GUC, per-org quotas, `SIGNUP_MODE` +
org switcher), PgBouncer, worker-container split, S3 storage, `/metrics` + observability, SSE realtime at
scale, SSO (Lark then Entra via the auth seam), dashboards, **independent security review + pen test**,
billing. Each is a decision-log-gated step, not part of the internal MVP.

### Post-MVP, week 7: Lark sign-in
`LarkOAuthProvider` behind `AUTH_LARK_ENABLED` (arch §4.3) — the acceptance test proves it touches only
the api's `auth` module + the login page.

---

## 4. The review loop — how quality survives fast code generation

For each milestone diff, in order:
1. **`/code-review`** on the working diff — correctness + simplification.
2. **Adversarial verification for the risky slices** — spawn independent subagents *prompted to break it*,
   not confirm it:
   - **Ordering races (M1/M2):** "Construct a concurrent-drag sequence that corrupts or duplicates sort keys."
   - **Auth (M1):** "Reach data from a different org via the app-layer filter" (the single-org seam must
     still hold), and "find an unauthenticated path to a task/project."
   - **Attachments (M3):** "Fetch a file you shouldn't have access to; find a stored-XSS via upload."
3. **`verify` skill** drives the actual UI end-to-end (not just green tests) — the design/QA lens.
4. **`/security-review`** before the pilot (first multi-user surface). Note: the deep security bar —
   independent human review + pen test — is a **commercialization gate**, because single-org keeps the
   cross-tenant attack surface out of the MVP entirely (D-028).
5. **You** read the diff for the things machines miss: does it match the spec's *intent*, is it the
   simplest thing, did it quietly add scope or reach for deferred SaaS machinery.

---

## 5. Risk register — how "using Claude" changes it

Some `04` §5 risks shrink; new ones appear.

| Risk | Change | Mitigation |
|---|---|---|
| **Owner is the sole bottleneck** (the real bus factor now) | **New, high** — you are sole reviewer + product-decider + ops + pilot-runner; if you're out, everything halts, and if you rubber-stamp, the whole quality model collapses | Batch reviews on a fixed cadence; keep slices small and runnable so review is fast; lean on `/code-review` + `verify` so your read is the *last* check, not the only one; when a slice is over your depth (e.g. auth), spawn extra adversarial subagents and slow down |
| **Spec drift / silent scope creep** | **New, higher** — Claude can generate plausible extras or reach for deferred SaaS machinery | Every prompt cites a spec section; DoD's one-in-one-out; you review diffs for unrequested scope; `CLAUDE.md` "never expand scope / never add multi-tenancy machinery without a decision-log trigger" |
| **Hallucinated APIs / wrong library signatures** (esp. jOOQ/Spring, newer to the reviewer) | **New** | Tests as ground truth; `CLAUDE.md` "never invent a signature"; pinned versions; CI build catches it |
| **2-week M1 slips** — the aggressive target | **New** | M1 is deliberately minimal (list view, no board/portfolio/comments); if it runs long, cut *within* M1 (board and My Tasks polish move to M2) before moving the date — dates are flexible, the *minimal* bar is not |
| **License drift** (transitive dep, npm or Maven) | Same | CI license gate on every PR |
| Design-build drift | Same | `docs/03-design.md` tokens are ground truth; axe/keyboard pass in DoD |
| Pilot adoption failure | **Unchanged** — a people problem, not a code one; and now the honest test of whether to commercialize at all | The spec §7 adoption plan + kill/pivot criterion stand; still your job |

**The meta-risk:** trusting output you haven't verified because it looks right. The gates in §4 exist
precisely so "looks right" is never the bar — "adversarial pass clean, app driven, diff read" is.

---

## 6. What stays yours (human-in-the-loop)

- **Product decisions & scope** — what's in Appendix A, what gets cut when something runs long.
- **One-way-door approvals** — license swaps, anything in the arch decision summary, and the eventual
  **internal→commercial pivot** (which activates the deferred multi-tenant work).
- **The decision log** — you (or Claude at your direction) record every ratified choice.
- **Secrets & infrastructure** — VM, DNS, `.env.prod`, local storage volume (S3 creds only at commercialization).
- **Running the pilot** — seeding real projects/portfolios, the adoption push, the go/no-go and the kill/pivot call.
- **Merge authority** — nothing reaches `main`/staging as "done" without your review of a runnable slice.

---

## 7. Cadence & honest expectations

**Dates are flexible; the one hard target is a usable minimum viable product (M1) in ~2 weeks** (D-028).
Code production is not the bottleneck — **your review and verification throughput is.** So the calendar is
really "how many runnable slices can you review per week." Rough shape: M1 ~2 weeks, M2 ~2 weeks, M3 ~1–2
weeks, then the internal pilot. These are review-gated, not clock-gated — if a week is busy, the milestone
waits; it does not ship unreviewed.

For the 2-week M1 to be real, protect it: it is deliberately minimal (single-org, list view, My Tasks — **no
board, portfolio, comments, or notifications**). If it runs long, cut *within* M1 (defer My Tasks polish,
push board to M2) rather than slipping — and never backfill it with deferred SaaS machinery.

**Pilot-readiness checklist (end of M3, the internal go/no-go):** Playwright smoke green on the local prod
stack · backup+restore drill executed · rollback rehearsed · zero Sev-1s, no design-QA blockers, a11y DoD
signed · the app driven end-to-end by you · a couple of real projects/portfolios seeded · honest-ledger
framing ready for the team. (The old `04` §1 also listed a tenant-isolation criterion and a k6/2-replica
run — both **deferred with the multi-tenant work**, D-028.)

**Do not** let speed collapse the gates: a milestone isn't done because the code exists — it's done when it
meets the DoD, the adversarial passes are clean, and you've run it. That discipline is the whole plan.

---

## Appendix — first three prompts, verbatim

1. *"Read `CLAUDE.md` and `docs/`. Confirm you understand the invariants and the single-org MVP scope
   (spec Appendix A + D-028), then start M1: scaffold the monorepo, the lean compose stack, and CI gates.
   Stop and show me the CI config and the compose topology before writing feature code."*
2. *"Implement the M1 Flyway schema (with jOOQ codegen) — single-org, `organization_id` on tenant tables
   with the shared app-layer org-filter helper (no RLS yet, per D-028). Then single-org first-run + local
   auth behind the IdentityProvider seam. JUnit+Testcontainers tests for the auth flow and the org-filter
   helper."*
3. *"Implement projects/sections/tasks, the list view (inline edit, drag, quick-add), the task side-peek,
   and My Tasks. Test the move/reorder race. Then deploy the stack and walk me through creating a project
   and working a task list — I'll start using it."*
3. *"Implement A1/A2 auth with the local provider and the provider-toggle framework. Then deploy the stack
   and walk me through creating an org and signing in on staging."*
