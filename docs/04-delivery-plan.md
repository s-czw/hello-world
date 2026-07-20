# Delivery Plan — Cairn MVP

**Document:** 04-delivery-plan.md
**Author:** Project Director
**Date:** 20 July 2026
**Status:** v1 — baselined against spec v1.1, architecture Draft v2, design v2 (all PD-1…PD-42 dispositions applied; no open rebuttals)
**Inputs:** 01-product-spec.md (v1.1) · 02-architecture.md (Draft v2) · 03-design.md (v2) · review-pmo.md · pm-tool-benchmark/research-notes.md

---

## 1. Plan on a page

**Goal.** Ship a usable internal MVP of Cairn — core work management (projects, tasks/subtasks, sections, list + board, comments, attachments) **plus** the portfolio layer (roll-up table, status updates, portfolio timeline) — in 6 weeks, self-hosted on our VM, and enter a 4-week pilot that retires the weekly status deck.

**Why we build (one line).** Every benchmarked vendor gates the portfolio/program layer behind a premium tier (Asana Advanced $24.99/user; Jira Premium; Monday Enterprise; Smartsheet Control Center). We make it a day-1 primitive on infrastructure we own.

| | Dates (2026) | Theme | Demo checkpoint |
|---|---|---|---|
| **Sprint 1** | Mon 20 Jul – Fri 31 Jul | Foundations: infra, schema, auth, tokens, list view + task peek | Demo 1 — Fri 31 Jul |
| **Sprint 2** | Mon 3 Aug – Fri 14 Aug | Collaboration + portfolio core: board, comments/attachments, My Tasks, roll-up + status composer | Demo 2 — Fri 14 Aug |
| **Sprint 3** | Mon 17 Aug – Fri 28 Aug | Timeline, notifications inbox, search UI, onboarding; **feature freeze end of week 5 (Fri 21 Aug)**; week 6 = hardening + pilot prep | Demo 3 — Fri 28 Aug = **pilot go/no-go** |
| **Pilot** | Mon 31 Aug – Fri 25 Sep | Whole team, ≥2 real programs as portfolios; §7 spec metrics measured from our DB | Pilot exit review w/c 28 Sep |

**MVP definition — FROZEN.** The MVP is spec v1.1 **Appendix A**, verbatim. In: A1–A4, B1–B5, C1–C4, D1–D5 (D5 with its ≤1-day auto-drop rule), E1–E2, F1. Stretch (Sprint-3 slack, in this priority order): (1) portfolio roll-up CSV export (PD-11), (2) SSE realtime with the pre-agreed cut to refetch-on-focus (PD-4). Out: everything in spec §5. Any change to this list goes through the scope-change rule in §4 — no exceptions, including from me.

**Capacity basis (PD-1).** ~27 effective Product Engineer days, ~12–14 part-time Tech Lead days (mostly consumed by infra/auth/CI/hard queries), Designer per the design doc §7 plan, PD part-time governance + pilot. The post-cuts scope sizes to ~28–30 PE-days — it fits **only** because Sprint 3 is deliberately under-committed (§3.3) and the PD-2/3/5/6/7/8 cuts stay cut.

**Launch criteria (pilot go/no-go, end of week 6 — all must be green; any red slips the pilot one week rather than shipping soft, PD-40):**

1. Playwright smoke path green **against the production compose stack** on the pilot VM.
2. Backup restore drill executed and verified (arch §6.2) — evidenced in the runbook, not assumed.
3. k6 run passed: p95 targets of arch §6.1 at 100 VUs (board read, reorder, portfolio timeline < 500 ms).
4. Zero open Sev-1 defects; no open `design-qa` blockers; accessibility DoD items signed by Designer (light theme, keyboard end-to-end, 200% zoom, reduced motion).
5. Two real pilot programs seeded as portfolios with their projects (owner: PD, spec open question 4).
6. Rollback tested: previous release tag redeployed on staging and verified (§7.3).
7. Invites work end-to-end (with SMTP if IT confirmed it; copy-paste links otherwise) and the admin runbook (`docs/runbook.md`) is current.
8. Pilot kickoff deck ready, including the honest-ledger framing of accepted losses vs Asana (spec §6, PD-16).

---

## 2. Workstreams & RACI

R = does the work · A = single accountable owner · C = consulted before/while · I = informed. One A per row.

| Workstream / module | Product Engineer | Tech Lead | Project Director | Designer |
|---|---|---|---|---|
| Monorepo scaffold, CI, compose stacks (dev/staging/prod), runbook | C | **A/R** | I | I |
| Data model, migrations, seed scripts (arch §3) | C | **A/R** | I | I |
| Auth & org setup — A1, A2 (backend + session model) | R (UI flows) | **A/R** | I | C (screens) |
| Invites & member admin — A3, A4 | R (UI) | **A/R** | C (pilot needs) | C |
| Teams, projects, sections, list + board views — B1–B4 | **A/R** | C (PR review) | I | R (specs + design QA) |
| My Tasks incl. PD-9 status-nudge block — B5 | **A/R** | C | C (nudge = flagship-metric mechanism) | R (spec) |
| Tasks: CRUD/peek, subtasks, comments, attachments — C1–C4 | **A/R** | C (StorageProvider, fan-out) | I | R (spec + QA) |
| Portfolio layer — D1, D2, D3, D5 (UI + composer) | **A/R** | R (roll-up SQL + perf dataset) | C (PMO usability) | R (spec + QA) |
| Portfolio timeline — D4 | **A/R** | C (timeline query) | C (scope police — PD-2 stays cut) | R (layout math is the top design risk) |
| Search — E2 (endpoint + Ctrl+K UI) | R (UI) | **A/R** (trigram endpoint) | I | C |
| Notifications — F1 (fan-out + inbox) | R (inbox UI) | **A/R** (events, purge job) | I | C |
| SSE realtime (stretch, last — PD-4) | C | **A/R** | I (owns the cut call with TL) | I |
| Design system: tokens, components, motion, a11y | C (implements) | I | I | **A/R** |
| Testing: unit/integration/E2E/k6 infra (arch §8) | R | **A/R** | I | I |
| Design QA (Tue/Thu in-app reviews, axe + keyboard passes) | C (fixes) | I | C (prioritizes vs pilot) | **A/R** |
| Governance: decision log, DoD, scope control, risk register | C | C | **A/R** | C |
| Pilot: seeding, kickoff, UAT, adoption push, metrics, go/no-go | C | C | **A/R** | R (feedback intake form, usability script) |

Standing agreements baked into the RACI:
- **No unreviewed merge to main** — TL reviews all PE PRs; PR review SLA **< 24 h**, with pre-delegated merge rights for the PE on low-risk areas (UI-only changes with green CI) so the part-time TL never blocks the critical path (PD-41).
- **Design specs land ≥ 3 working days before their build slot**; spec-readiness is a visible checklist in the Monday checkpoint (PD-41, design §7).

---

## 3. Sprint plan

Dependency spine (honored below): **schema + auth + design tokens → list view → task peek → board → portfolio roll-up (D1/D2/D3) → timeline (D4)**. Notifications backend before inbox UI; search endpoint before Ctrl+K UI; D3 composer before the B5 nudge block and before D5; SSE strictly last (PD-4).

### 3.1 Sprint 1 (20–31 Jul) — Foundations: "a project you can work in"

**Sprint goal:** from a fresh install, an admin creates the org, signs in, creates a project from a template, and works a task list — sections, quick-add, inline edit, drag reorder, side-peek — deployed on the staging compose stack.

**Day-1–2 gate:** spec v1.1 + architecture v2 + design v2 formally signed in `docs/decisions.md` (sign-off order per PD-38). TL scaffold work is not blocked by sign-off; feature code is.

| Who | Backlog (spec IDs) |
|---|---|
| **Tech Lead** (~5d) | Monorepo scaffold + CI pipeline (lint/type/unit/integration/build/smoke, arch §8) · dev + **staging** compose stacks (staging must exist by end of week 1 — DoD depends on it) · Prisma schema v1 + migrations + seed script (arch §3, incl. dormant-column CI greps) · **A1** first-run org creation · **A2** email/password auth, sessions, rate limiting, `IdentityProvider` seam |
| **Product Engineer** (~9d) | **B1** teams (1d) · **B2** projects + sections + templates + sample project (2d) · **B3** list view — fixed 5 columns, section grouping, inline edit, drag + "Move to…" menu, quick-add (4d) · **C1** task CRUD + side-peek panel + `/tasks/:id` routing (2d) |
| **Designer** | Day 1–2: `tokens.css` + vendored fonts (PE unblocked immediately) · component specs batch 1 · week 2: screen specs for app frame/sidebar, list view, side peek, My Tasks incl. nudge block · empty-state copy v1 · first Tue/Thu in-app reviews |
| **Project Director** | `docs/decisions.md` + `docs/definition-of-done.md` live day 1 (retro-log all PD rulings) · SMTP answer from IT (spec open Q2, end of week 1) · ceremonies stood up · confirm PE's D5 sizing (≤1d or cut) logged by Friday 24 Jul |

**Demo 1 content (Fri 31 Jul, on staging):** fresh-install → create org → invite-free login → create project from "Simple ops checklist" → rename sections → quick-add 5 tasks → inline-edit assignee/due/priority → drag reorder across sections → open side peek → complete a task (with the motion moment) → sample project tour.

**Exit criteria:** core-entity schema migrated and frozen (post-freeze changes go through the decision log) · A1/A2/B1/B2/B3/C1 at DoD · design tokens frozen · CI green incl. integration tests on auth + task CRUD/move · staging stack is where the demo runs.

### 3.2 Sprint 2 (3–14 Aug) — Collaboration + portfolio core: "the Monday review works"

**Sprint goal:** the same data works as a board; tasks carry conversation and files; My Tasks lands with the status nudge; a portfolio shows a live roll-up with status updates posted through the composer. This is the sprint where the differentiator must exist.

| Who | Backlog (spec IDs) |
|---|---|
| **Product Engineer** (~9d) | **B4** board view — cards, drag between columns, same data as list (2.5d) · **C2** subtasks (1d) · **C3** comments + @mentions + activity stream (1.5d) · **C4** attachments UI over TL's StorageProvider (0.5d) · **D1** portfolios + membership (0.5d) · **D3** status composer incl. "Copy previous update" + On-hold helper copy (1d) · **D2** roll-up table UI: chips, 7-day staleness, progress, header summary (1d) · **B5** My Tasks incl. PD-9 owner-nudge block — built **after** D3 so the nudge opens a real composer (1.5d) |
| **Tech Lead** (~5d) | **A3** invites + **A4** member management (2d) · `StorageProvider` + upload/download endpoints, limits, sniffing (1d) · **portfolio roll-up SQL aggregate + seeded 50-project/10k-task perf dataset — Sprint 2, not Sprint 3, per arch risk #2** (1d) · notification fan-out backend (F1 events + 90-day purge job) writing rows; inbox UI comes in Sprint 3 (1d) · E2 trigram search endpoint if slack remains (else week 5) |
| **Designer** | Component specs batch 2 (kanban card, modal, date picker, timeline bar, status composer, search overlay) · screen specs: board, portfolio overview, **portfolio timeline (D4 — needed ≥3 days before its week-5 build slot)**, project Overview tab, notifications inbox · mid-sprint design QA pass 1 on Sprint-1 surfaces (contrast script + manual keyboard audit) |
| **Project Director** | Monday checkpoints: watch list-view/board actuals vs estimate (first scope-trigger checkpoints, §5 R2/R3) · begin shortlisting the two pilot programs · draft UAT script skeleton (§7.2) |

**Demo 2 content (Fri 14 Aug, on staging):** drag a card on the board and show the list reorder to match → subtask with own assignee appears in that person's My Tasks → @mention creates a notification row (shown via API/DB — inbox UI is Sprint 3) → attach a file, preview an image → My Tasks with Overdue-first groups and the "Your projects — N need a status update" block → create a portfolio, add 4 projects → post a status via the composer (copy-previous path) → roll-up table shows chips, staleness, progress → invite flow end-to-end.

**Exit criteria:** B4/B5/C2/C3/C4/D1/D2/D3/A3/A4 at DoD · **roll-up + timeline queries < 500 ms on the seeded perf dataset** (measured, recorded in the decision log) · design QA pass 1 findings triaged with severities · all remaining MVP screens spec'd (design §7 Sprint-2 exit) · notification rows being written for all six F1 event types.

### 3.3 Sprint 3 (17–28 Aug) — Timeline, hardening, pilot prep: "ready to be lived in"

**Deliberately under-committed: committed feature work ≈ 60% of PE capacity (~5.5 of 9 days). The remaining ~40% is integration, bug burn-down, and the stretch ladder — in that order. Do not fill it.**

**Sprint goal:** portfolio timeline ships read-only per PD-2; notifications inbox and search close the loop; the product is frozen, hardened, verified, and seeded for pilot.

**Week 5 (17–21 Aug) — last feature week. Feature freeze Fri 21 Aug (PD-40).**

| Who | Backlog (spec IDs) |
|---|---|
| **Product Engineer** | **D4** portfolio timeline — read-only bars, fixed month axis, today line, −6/+18-month window, "No dates" tray with inline date editing (2.5d; the PD-2 cut list stays cut) · **F1** notifications inbox UI + sidebar bell/badge (1.5d) · **E2** Ctrl+K search UI over TL's endpoint (0.5d) · **D5** portfolio status — same composer retargeted; **auto-drops without a meeting if it exceeds the 1-day box** (≤1d) · first-run/onboarding (design §4.i: checklist card, one-shot hints, seeded sample) (0.5–1d) |
| **Tech Lead** | E2 endpoint if not landed · k6 script + run against staging (arch §6.1 targets) · backup sidecar verified; **restore drill executed** · error-tracking decision (GlitchTip vs logs) only if slack remains — not critical path |
| **Designer** | Onboarding + final copy pack · empty states verified in-app · **conditional** motion pairing only if the sprint opens green (PD-7), else polish burn-down · pilot-feedback intake form + 30-min usability session script |
| **Project Director** | Week-5 checkpoint: bring the two pilot programs (spec open Q4) · freeze decision Fri 21 Aug · stretch-ladder call (below) |

**Stretch ladder (only from genuine slack, in order, per spec Appendix A):** 1) CSV export of the portfolio roll-up (PD-11, ~0.5d, reuses the D2 query) → 2) SSE realtime (TL, PD-4). **Pre-agreed cut line, no meeting required:** if week 5 opens with any Sprint-2 exit criterion red, SSE is cut and optimistic-update + refetch-on-focus ships as the realtime story (AC0.2 holds either way).

**Week 6 (24–28 Aug) — hardening, zero new features.**

- Bug burn-down of `design-qa` and functional issues, PD prioritizing against pilot readiness.
- Design QA pass 2 — full-product sweep: light-theme contrast, 200% zoom, reduced motion, keyboard-only end-to-end run (create project → tasks → board → portfolio → status update → notification).
- Playwright smoke green against the **prod** stack on the pilot VM; k6 results recorded; restore drill evidence in runbook; rollback rehearsal (§7.3).
- Seed the two pilot programs; dry-run the UAT script (§7.2) with the PD as first user; pilot comms + kickoff deck (honest ledger, PD-16).

**Demo 3 content (Fri 28 Aug) = go/no-go review:** full walk of the launch criteria (§1) with evidence, live end-to-end run of the UAT happy path on the prod stack, review of open defects, explicit go/no-go decision recorded in `docs/decisions.md`. Any red criterion slips the pilot one week — pre-agreed, not debated on the day.

**Exit criteria:** launch criteria 1–8 all green · MVP = Appendix A shipped (minus any auto-dropped D5/stretch, logged) · pilot begins Mon 31 Aug.

---

## 4. Ceremonies & governance

Sized for a team of four — five recurring events total, everything else is async.

| Ceremony | Cadence | Who / format |
|---|---|---|
| **PD checkpoint** | Mon, 30 min | All four. Scope vs plan, risk register review (§5 triggers checked), decision log review, design spec-readiness checklist (≥3-day rule), blockers. |
| **Design review in the running app** | Tue + Thu, 30 min | Designer + PE (PD optional). Findings filed as `design-qa` issues with severity: blocker / before-pilot / polish. |
| **Sprint demo** | Last Fri of sprint, 60 min | All four, live app **on staging** (demo 3 on prod). Walk the ACs story by story — stories are done or visibly are not. Demo 3 doubles as pilot go/no-go. |
| **Async standup** | Daily, written | Three lines in the team channel: yesterday / today / blocked. No meeting. |
| **Sprint re-plan** | First Mon of sprint (inside the checkpoint, +30 min) | Carry-over triage: anything unfinished displaces something from the new sprint — carry-over is not free. |

**Decision log — `docs/decisions.md` (ADR-lite).** Numbered entries: date, decision, context (2–3 lines), owner, docs affected, status (proposed/ratified/reversed). Retro-logged on day 1: all PD-1…PD-42 rulings, side-peek-not-modal (ratified), command palette (reversed), Cairn naming (ratified), URL/ID scheme (PD-29), status enum (PD-17). This file is the single source of truth for "what did we decide" — the review's `DECISIONS.md` naming is superseded by this canonical path. Anything "decided" outside this file is not decided.

**Scope-change rule (PD-39).** Any user-facing capability not in spec v1.1 Appendix A requires (a) PD sign-off logged in `docs/decisions.md` **and** (b) a named, equal-effort cut from the same sprint — one-in-one-out. Mid-sprint additions displace, never append. The rule binds all four roles, including the PD.

**Definition of Done — `docs/definition-of-done.md` (PD-36), per story:**
1. ACs demonstrated in the running app (not a local branch).
2. Tests per architecture §8 for the layer touched: integration test for any new endpoint (real Postgres via Testcontainers); unit tests for ordering/authz/roll-up logic; CI green.
3. Code review: TL-approved PR; no unreviewed merge to main (24 h SLA; pre-delegated low-risk merges allowed).
4. Deployed to the **staging compose stack**.
5. Design QA: axe scan + manual keyboard pass on new surfaces; no open `design-qa` blocker on the story.

**Feature freeze (PD-40):** end of week 5 (Fri 21 Aug). Week 6 accepts only bug fixes, copy, and a11y/polish burn-down.

---

## 5. Risk register — top 8 delivery risks

Merged: Tech Lead's technical register (arch §9) + PMO delivery risks. Reviewed every Monday checkpoint; a fired trigger activates the fallback that week, not after debate.

| # | Risk | Owner | L / I | Mitigation | Trigger → fallback |
|---|---|---|---|---|---|
| R1 | **Single-engineer bus factor** — PE is the only full-time builder; illness or a 3-day rabbit hole stalls the critical path | PD | Med / **High** | TL reviews every PR (<24 h SLA) so context is always shared; runbook + seed scripts current so anyone can run the stack; TL pre-briefed on list/board internals; no long-lived branches (merge ≤2 days) | PE unavailable > 2 consecutive days, or any story > 2× its estimate → TL takes over the in-flight story; PD applies one-in-one-out to shed equal scope the same week |
| R2 | **Scope creep on the portfolio timeline** — D4 regrows toward Gantt (drag, zoom, dependencies); it was the single biggest estimate range (4–8d pre-cut) | PD | Med / **High** | PD-2 cut list stated in spec, design, and this plan; timeline is a Sprint-3 week-5 item with a fixed 2.5d box; Designer's fallback (sorted date-bar table) pre-agreed as the floor | D4 exceeds 2.5 PE-days or is not demoable by Wed 19 Aug → ship the "Not scheduled"-style sorted date-bar table for pilot; bars return post-pilot; logged, no meeting |
| R3 | **List/board interaction depth blows Sprint 1–2** — inline edit + DnD + a11y path is the heaviest core-work item (PD-3 already trimmed it once) | PE | Med / High | Fixed 5-column set, section-grouping only, reduced keyboard map, menu-based drag alternative — already cut to the bone; TL owns the `/move` API so ordering logic isn't reinvented client-side | B3 not at DoD by end of Sprint 1 → B4 board slips to mid-Sprint 2 and C4 attachments moves to Sprint 3 slack; if board also slips, the Sprint-3 stretch ladder is cancelled entirely |
| R4 | **Design-build drift / spec latency** — PE builds ahead of specs or diverges from them; v1 docs already showed 10+ divergences (PD-17…PD-35) | Designer | Med / Med | Specs ≥3 working days before build slot, tracked in the Monday checkpoint; Tue/Thu in-app reviews catch drift within 72 h; tokens frozen after Sprint 1; all divergences resolved only via `docs/decisions.md` | Any spec misses its 3-day lead twice, or a Tue/Thu review finds a built surface contradicting a spec → PE builds only spec'd surfaces (infra/API work fills the gap) until the spec debt is cleared; PD arbitrates within 24 h |
| R5 | **Roll-up/timeline query cost** — the differentiating read gets slow or is built as N+1 (arch risk #2) | TL | Med / **High** | Single SQL aggregate, denormalized `current_status`, indexes per arch §3.3; **perf-tested against the seeded 50-project/10k-task dataset in Sprint 2, not Sprint 3**; k6 assertion < 500 ms | Sprint-2 measurement > 500 ms → enable the pre-built 60 s Redis cache flag on the roll-up read and record it; if still red at k6 time, cache TTL rises and a Phase-1 optimization ticket is opened — pilot is not blocked |
| R6 | **Board ordering conflicts** — concurrent drags corrupt or visibly "jump" ordering (arch risk #1) | TL | Med / Med | Server-computed fractional keys only (`/move` API); deterministic tie-breaks; background rebalance; dedicated race-condition integration tests in CI from Sprint 1 | Race tests flaky or a reproducible jump found in Sprint 2 → drop to coarse-grained per-section optimistic locking (409 + refetch on conflict) for MVP; smoother merge behavior becomes Phase-1 work |
| R7 | **Self-hosting ops failure** — VM not ready, backups don't restore, or upgrade path breaks during pilot (arch risk #5 + delivery) | TL | Low–Med / **Critical** | VM provisioned week 1 (staging on it from Sprint 1); nightly `pg_dump` + attachment snapshots + off-VM sync; restore drill **executed** before go-live (launch criterion 2); weekly CI job restores latest dump + runs migrations; expand→migrate→contract migration discipline; RPO 24 h / RTO 2 h documented | Restore drill fails or backup job misses 2 nights → pilot does not start (launch criterion), or if in-pilot: freeze deploys, fix backup chain within 48 h, PD informs pilot users of the risk window |
| R8 | **Pilot adoption failure** — the status ritual dies by week 3; flagship metric (≥80% ≤7-day status) starves (the product-level kill risk, spec §7) | PD | Med / **Critical** | PD-9 nudge block + 7-day staleness chips + copy-previous composer are all shipped MVP mechanisms; PD runs the adoption push: kickoff with honest-ledger framing, program leads run Monday reviews **from the portfolio screen**, weekly metric readout from the DB to the Ops Director | Week-8 readout: status cadence < 50% or WAU < 40% → PD triggers the pre-agreed intervention (Ops Director mandates the tool for the two pilot programs, retires the parallel deck immediately); if still failing at pilot exit, the spec §7 kill/pivot criterion fires — stop building, revisit buy (Asana Advanced, $625/mo) |

Watchlist (not top-8, reviewed monthly): Prisma raw-SQL drift on the two hand-written queries (integration-tested); framework version churn (pin minors, upgrade between sprints); auth-seam erosion (arch §4.3 acceptance test); attachment volume growth (quota + admin view from day 1).

---

## 6. Post-MVP roadmap

Numbering note: this plan counts the MVP as Phase 1, so spec §5's "Phase 1/2/3/4" map to Phases 2/3/4/4 below (spec Phases 3+4 are combined into Phase 4 here). The spec §5 cut table remains the authoritative feature-to-phase mapping.

**No phase starts before its decision gate passes — each gate is a PD-chaired review logged in `docs/decisions.md`.**

### Phase 2 — Dashboards & reporting (spec "Phase 1") — ~3 sprints (6 weeks)
- **Headline:** dashboards/charts over projects & portfolios; custom fields (text/number/select, on the field-registry pattern the MVP priority field established); filters + saved views; full-text search (tsvector on descriptions/comments); rich text (descriptions, comments, status bodies); general CSV/JSON export UI; timeline drag-edit + zoom presets (the PD-2 backlog); Phase-1 polish backlog (vanity slugs, command-palette verbs, dark theme).
- **Gate:** pilot passed — kill/pivot criterion not fired; ≥70% "keep and extend" on the exit survey; status-cadence ≥ 80% weeks 9–10; Ops Director has retired the status deck; top-10 pilot feedback items triaged into this phase's backlog.

### Phase 3 — Entra ID SSO + Teams/Outlook (spec "Phase 2") — ~3 sprints (6 weeks)
- **Headline:** Entra ID OIDC sign-in via the `IdentityProvider` seam (acceptance test: touches only `auth` module + login page); SCIM-style deprovisioning; email notifications/digests; Teams tab (project/portfolio views) + message→task; Outlook add-in (email→task); calendar sync (read-only first). Benchmark M365 specifics are the blueprint.
- **Gate:** Phase 2 shipped and adopted (dashboards viewed weekly by program leads); IT approves the Entra app registration + consent model; the auth-seam acceptance test still passes (no erosion during Phases 1–2); org confirms 6 more weeks of build funding vs buying.

### Phase 4 — Forms, automations, goals, workload, guests (spec "Phases 3+4") — ~4–5 sprints, sequenced by pull
- **Headline:** intake forms; rules/automation (incl. status-update reminders that replace the staleness-chip social mechanism); recurring tasks; task dependencies + milestones (the biggest deliberate MVP cut); goals/OKRs; workload/capacity views; guest/external access with private projects + per-project permissions (activating the dormant `projects.private` column); nested portfolios.
- **Gate:** demonstrated demand — each feature cluster needs named internal users asking for it with a concrete workflow (no speculative builds; the ClickUp cautionary tale from the benchmark); security review before guest access (first externally-facing surface); team size/tooling budget re-confirmed by the Ops Director.

---

## 7. Verification & quality

### 7.1 Test pyramid (from architecture §8 — enforced via DoD)

| Layer | Tooling | MVP-mandatory scope |
|---|---|---|
| Unit | Vitest | Fractional-index ordering (exhaustive incl. ties/rebalance), authz policies, roll-up calculators, IdentityService |
| Integration | Vitest + Testcontainers (real Postgres + Redis) | Per-module contracts: auth flows (login, invite accept, reset), task CRUD + move/reorder **races**, roll-up + timeline queries, RBAC matrix as a table-driven test (incl. any-member-can-post-status), pagination cursors |
| E2E | Playwright | **One smoke path** (tripwire, not a spec): login → create project → sections/tasks → drag reorder → assign → comment + attach → create portfolio → roll-up + timeline → status update appears |
| Load | k6 | One Sprint-3 run: 100 VUs on board read + reorder + portfolio timeline; assert arch §6.1 targets |

CI per PR (~6–8 min): lint + typecheck → unit → integration → image build → Playwright smoke against the compose stack → `pnpm audit`. Weekly scheduled job: restore latest prod dump + run migrations against it — backup and migration verification in one (feeds R7).

### 7.2 UAT script (pilot week 0 — PD runs it with 4–6 pilot users before kickoff; full script in `docs/uat-script.md`)

Persona-based, mapped to spec ACs; each step pass/fail with notes:

1. **Admin path (Amira):** fresh login → invite 3 users (SMTP or copy-link) → Members page: promote/demote, deactivate → verify the at-least-one-admin guard. (A2–A4)
2. **Project lead path (Sofia):** create project from "Simple ops checklist" < 2 min → restructure sections → assign tasks + due dates in list view → run a mock stand-up on the board (drag across columns) → post a status update < 60 s using copy-previous. (B2–B4, D3)
3. **Program lead path (Daniel):** create portfolio → add 4 projects → read the roll-up (statuses, staleness, progress) → open the timeline, fix an undated project from the tray → post a portfolio status (if D5 shipped). (D1, D2, D4, D5)
4. **Member path (Omar):** land on My Tasks → Overdue-first check → complete a task → get @mentioned and follow the notification deep-link → upload an attachment → find a task via Ctrl+K. (B5, C1, C3, C4, E2, F1)
5. **Hygiene checks:** stale project shows the nudge block to its owner; keyboard-only run of path 2; one session at 200% zoom.

Exit: all critical paths pass; failures triaged as launch blockers vs pilot-week fixes. UAT feedback lands in the pilot intake form (Designer).

### 7.3 Rollback plan

- **Releases:** every deploy is a git tag; CI builds images tagged SHA + version; the VM only pulls, never builds.
- **Rollback = redeploy previous tag** (`docker compose pull && up -d`), safe because migrations are backward-compatible one version (expand → migrate → contract, arch §7.5). Destructive migrations require a scripted pre-upgrade backup checkpoint.
- **Rehearsed, not assumed:** rollback executed once on staging in week 6 (launch criterion 6); restore drill per arch §6.2 (launch criterion 2). RPO 24 h / RTO 2 h — stated in the pilot kickoff so nobody assumes better.
- **In-pilot failure ladder:** Sev-1 → fix-forward if < 2 h, else roll back to previous tag; data-integrity issue → freeze writes (maintenance page via Caddy), restore from latest verified dump, PD communicates the data-loss window against the RPO.
- **Ultimate fallback (pre-agreed in spec §7):** if the pilot kill/pivot criterion fires, the team stops building and the org revisits the buy decision — Asana Advanced remains the benchmarked fallback at $625/mo. Owning the tool only pays if the team lives in it.

---

*Change control for this document: material changes (sprint dates, MVP definition, launch criteria, risk fallbacks) go through `docs/decisions.md` like everything else.*
