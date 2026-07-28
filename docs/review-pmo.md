# PMO Review — Dev-Plan Drafts (01-product-spec, 02-architecture, 03-design)

**Reviewer:** Project Director · **Date:** 20 July 2026 · **Status:** Consolidated adversarial review v1
**Inputs reviewed:** 01-product-spec.md (Draft v1), 02-architecture.md (self-marked "Approved for Sprint 1"), 03-design.md (v1), pm-tool-benchmark/research-notes.md
**Findings are numbered PD-1…PD-42 for traceability. Verdicts and per-document change lists are in §5.**

## Verdict summary

| Document | Verdict | Core reason |
|---|---|---|
| 01-product-spec.md | **APPROVE-WITH-CHANGES** | Scope discipline is genuinely good; fix internal contradictions (staleness threshold, rich text) and absorb the feasibility cuts below |
| 02-architecture.md | **APPROVE-WITH-CHANGES** | Sound engineering, but the ERD contradicts the spec's locked task model and is missing entities the spec requires; also re-litigates a settled scope decision (private projects) |
| 03-design.md | **REWORK** | High craft, but it designs a materially different product in ~10 places — including the status model, which is the reason this product exists — and silently adds ~8 unscoped features on a 6-week clock |

---

## 1. Feasibility vs the 6-week clock

**PD-1 — Capacity math says the MVP fits only after the cuts below.** Real capacity is ~26–27 effective Product Engineer days (30 minus ceremonies/reviews) plus ~10–12 part-time Tech Lead days, which the runbook/CI/auth/infra work in 02-architecture will mostly consume. My bottom-up sizing of Appendix A as currently written (with the design doc's embellishments): auth/invites/admin 4–5d, teams/projects/sections 3d, list view with full inline edit + DnD 5–6d, board 3–4d, task panel/subtasks/comments/attachments 5d, My Tasks 1–2d, portfolios + status + roll-up 4d, timeline 4–8d, search 1–2d, notifications 2–3d, SSE 2d ⇒ **35–45 PE-days against ~27**. The spec is buildable in 3 sprints **only** at the "simplify" level of PD-2…PD-8, and only if the design doc's additions (PD-30, PD-31) are struck. This is a conditional pass, not a pass.

The five highest-effort items, with rulings:

**PD-2 — Portfolio timeline (D4 / design 4.e): SIMPLIFY — ship read-only bars at a single fixed Month zoom.** This is the largest single line item and the design doc grows it further (drag-move + drag-resize, cursor-anchored animated zoom, ±2-year virtualized scroll, weekend shading, keyboard nudge/resize). Ruling: MVP = one fixed month-granularity axis, one bar per project colored by status, today line, "No dates" tray with **inline date editing in the tray/table** (that is how dates get fixed, not by dragging bars), horizontal scroll without virtualization over a bounded window (e.g. −6/+18 months). Cut for MVP: bar drag/resize, zoom presets entirely, zoom animation, weekend shading, virtualization. The spec's own fallback ("read-only bars are the committed floor") becomes the plan, not the floor — and the decision date "end of Sprint 2" is too late: that is when the thing must be *built*. Decide now; drag-editing goes to the Phase-1 backlog. This alone returns ~3–4 PE-days.

**PD-3 — List view as spreadsheet-grade grid (B3 / design 4.a): SIMPLIFY — fixed columns, section grouping only.** Inline edit on every cell + drag reorder + ARIA grid pattern + per-user column widths + show/hide columns + group-by (Section/Assignee/Due) + filter popover is a mini-Airtable. Ruling: keep inline edit for the five spec fields and drag reorder (they are the product); **cut** column resize/show-hide/persistence, group-by other than Section, and the filter popover (spec already parked filters in Phase 1 — the design re-added them, PD-30). Keyboard scope: ↑/↓, Enter, x, Esc, quick-add — not the full §3.3 chord map. WCAG 2.5.7 drag alternative = the "Move to section/position…" ⋯-menu (already specced), **not** the Space-pickup keyboard-DnD state machine; that satisfies AA at a fraction of the cost.

**PD-4 — Realtime SSE: KEEP, but sequence it last with a pre-agreed cut line.** The architecture's own risk #4 says it: refetch-on-focus already gives correctness. Ruling: SSE is built in Sprint 3 only after board/portfolio/notifications are demo-complete; if week 5 starts with any red feature, SSE is dropped for MVP without a meeting. AC0.2 is already satisfiable by optimistic update + refetch.

**PD-5 — Command palette (design §3.4): CUT to spec E2.** Spec asked for Ctrl+K typeahead search over titles/names. The design unilaterally "decided" a command palette with creation verbs, grouped results, and `aria-activedescendant` listbox mechanics. Ruling: ship spec E2 exactly; palette verbs go to backlog. (Also a governance point — see PD-38.)

**PD-6 — Dark theme: CUT from the MVP gate.** The spec never asked for it; the design makes it a CI-failing acceptance criterion with dual-theme validation of every token pair, doubling design-QA per sprint. Ruling: keep the semantic-token architecture (correct and cheap), ship light theme only, dark theme lands post-pilot when it is nearly free. Remove the theme toggle from the avatar menu.

**PD-7 — Motion spec (design §5, 9 anime.js choreographies + a week-5 tuning pairing): SIMPLIFY to three JS moments.** Keep #1 task-complete, #3 drawer open/close, #8 inline-commit flash. Everything else (card-drop spring, timeline zoom sync, toast choreography, staggered content swap-in) is CSS transitions or nothing. The dedicated Designer+PE pairing week in Sprint 3 happens only if Sprint 3 opens green; otherwise that week is polish burn-down and pilot prep.

**PD-8 — Rich text: SIMPLIFY — plain text everywhere in MVP.** The spec wants rich-ish task descriptions (C1) and rich status bodies (D3); the design explicitly refuses a rich-text editor and draws plain-text fields (§2.6, 4.c). The design is right on the economics: a rich editor is a 3–5 day sinkhole (serialization, sanitization, paste handling). Ruling: plain text + auto-linkified URLs for descriptions, comments, and status bodies in MVP; rich text goes to Phase 1. This resolves the three-document contradiction (PD-24) in the cheap direction. Spec must be amended.

---

## 2. Usability for a real PMO running programs

What would make me, as a program director, refuse to leave an Asana-class tool — and what is cheap to fix:

**PD-9 — The status ritual has no pull mechanism; it will die by week 3 of the pilot.** In-app-only notifications mean a project lead who doesn't open the tool never learns their status is stale; the staleness chip (D2) is visible only to people already looking at the portfolio — i.e., me, not the delinquent lead. Asana's habit loop runs on inbox + email nudges. The spec defers reminders to Phase 3 and email to Phase 2, which starves the **flagship pilot metric** (≥80% of projects with a status ≤7 days old). Cheap fix, recommend for MVP: a computed-on-read "Your projects — 2 need a status update" block at the top of **My Tasks** for project owners (one query against `project_status_updates`, no cron, no SMTP, ~half a day). This is the single highest-leverage half-day in the whole plan.

**PD-10 — Staleness threshold contradicts the success metric.** D2 flags "no update in the last **14 days**" grey, while §7's flagship metric demands updates ≤**7 days** old and Principle 3 says weekly. A tool that only shames at 14 days will plateau the metric at 14 days. Set the chip threshold to 7 days (or two-stage: amber >7d, grey >14d — but one threshold is fine).

**PD-11 — No CSV export until Phase 1 blocks steering-committee use.** "Ad-hoc SQL against live Postgres" is not a PMO workflow — I am not shelling into the VM before a SteerCo. Cheap fix: one endpoint, "Export portfolio table as CSV," reusing the exact D2 roll-up query (~half a day). Recommend as MVP stretch, first item of Sprint-3 slack.

**PD-12 — Portfolio-level status is drawn but not specced — decide it.** The design's 4.d shows an "overall status chip (manual, set by owner)" on the portfolio header. The spec has no portfolio status at all. As a program lead I *do* report upward, so I want this — but it is a scope addition and must be decided, not smuggled. Ruling: if it is literally the same status-update component pointed at a portfolio (≤1 day), add it to the spec; otherwise the Designer removes it. Product Engineer to size it this week.

**PD-13 — Add "copy previous update" to the status composer.** The prefilled title is good; prefilling the *body* from last week's update (explicitly, via a button) is how weekly updates actually get written in under 60 seconds. Trivial to build; directly serves Principle 3.

**PD-14 — Present but will not get used (accept or trim, don't polish):** (a) **Priority** — it cannot be filtered or sorted by (spec sorts by due/assignee only; filters are Phase 1), so it is a colored decoration until Phase 1; either allow sort-by-priority in B3 (cheap) or accept it is dormant and spend nothing on it (design currently gives it colors, flags, chips, and a keyboard key). (b) The **full keyboard chord map** (`g h`, `a`, `d`, `s`…) — this is a general-ops team; Ctrl+K/Enter/Esc/x is the realistic ceiling (aligns with PD-3). (c) **Subtask "promote to task"** — rare; fine to keep only because it's small. (d) **"On hold"** status will be confused with archiving — one line of helper copy in the status picker prevents a pilot-support headache.

**PD-15 — My Tasks must lead with Overdue.** The spec's grouping (Overdue/Today/Upcoming/Later/No date) is right and is exactly what makes P4-Omar succeed; the design's version drops Overdue and No date (§3.1). Overdue-first is the point of the view. Align design to spec (also logged as consistency PD-32).

**PD-16 — Accepted losses, restated for the pilot kickoff:** no mobile, no email, no dependencies/milestones, no guest access, plain-text everywhere. Per spec §6's honest ledger, I will state these at kickoff so the pilot survey measures the product we shipped, not the Asana memory. No change requested — but the kickoff deck owns this framing (action: PD, week 6).

What is genuinely strong and should not be touched: the two-role model, one-assignee-one-due-date, sections=columns as one structure, the staleness-visible-by-default principle, the default "General" team, the kill/pivot criterion in §7. This is the right 20% of Asana.

---

## 3. Cross-document consistency

**PD-17 — Status enum three ways (CRITICAL — this is the roll-up atom).** Spec + architecture: `on_track / at_risk / off_track / on_hold`. Design: On track / At risk / Off track / **Blocked** (+ "No status/draft"), with purple-vs-red semantics, and portfolio sort order "blocked, off-track, …". The status vocabulary is the product's core primitive; three documents must carry one enum. Ruling: spec's enum stands; Designer replaces Blocked with On hold everywhere (colors, sort order, chips, timeline bars) or formally proposes the enum change through the decision log (PD-38) this week.

**PD-18 — The design invents a task-level "Status" field.** List-view columns (4.a), the task panel field grid (4.c), and the `s` shortcut all show Status **on tasks**. In the spec, status exists on *projects* only; tasks have completed + priority. Remove the task Status field/column/shortcut from the design, or if "Status" meant Section, rename it.

**PD-19 — Priority is inconsistent in all three documents.** Spec: `none/low/medium/high` (§3.2, with field-registry modeling note). Design: `Urgent/High/Medium/Low`. Architecture: **no priority column at all** in the tasks table. One enum, present in the ERD, honoring the spec's field-registry note. Spec's enum stands.

**PD-20 — Architecture breaks the locked single-assignee model.** The ERD has a `task_assignees` M:N join table (plus an index plan for it), directly contradicting Principle 2 ("one task, one assignee — the tool refuses to create ambiguity") and task field spec C1 (single-select). Replace with a nullable `assignee_id` FK on tasks (or, if the join table is kept for future collaborators/followers, add a uniqueness guarantee on `task_id` for role=assignee and say so). As written, the schema permits exactly the failure mode the product exists to prevent.

**PD-21 — The design defers notifications out of the MVP.** Design §3.1 topbar: "notifications bell (post-MVP: hidden)". Spec F1 is a full in-MVP notifications module with 6 event types, and E1 puts the bell + unread badge in the **sidebar**. Notifications are load-bearing for P4 and for PD-9. Restore to MVP; one placement (spec says sidebar; I have no objection if the team standardizes on topbar — log the decision).

**PD-22 — Two different landing pages.** Spec B5: My Tasks is the post-login landing page. Design §3.1: a "Home" surface — "dashboard-lite: my tasks due this week, recent projects, portfolio statuses" — which is unscoped Phase-1 dashboard creep with its own queries and empty states. Cut Home entirely; My Tasks lands, sidebar top slot is My Tasks. (The first-run welcome checklist from 4.f can live on My Tasks.)

**PD-23 — Timeline zoom presets differ** (spec: Quarter/Half/Year; design: Weeks/Months/Quarters). Superseded by PD-2: one fixed Month zoom in MVP. Both documents update to match.

**PD-24 — Rich text vs plain text.** Spec C1/D3 rich vs design "deliberately not in MVP." Resolved by PD-8 (plain text everywhere); spec amends C1 and D3, design stands.

**PD-25 — Architecture re-litigates private projects.** Spec §3.3/§5: no private projects in MVP, privacy is Phase 4 — a locked cut. Architecture §6.4 adds a `private` flag ("the one access-control refinement worth its cost now") and an RBAC column for non-members of private projects. This is a settled decision being reopened in a footnote. Ruling: out of MVP behavior — no flag in the RBAC matrix, no authz branch, no UI. I will accept a dormant boolean column defaulting false if the Tech Lead insists it de-risks Phase 4, but nothing may read it.

**PD-26 — Per-project/team permission gating contradicts the two-role model.** Architecture: `team_members.role lead|member`, RBAC rows gating "edit/archive project, post status update" to "project owner/lead", and business-rule example "status update requires portfolio membership." Design 4.d: status chip "inline-editable by leads." Spec §2.1 is explicit: two org roles; lead is a *persona*, any member can edit projects and post status. Remove the lead gating and the team_members.role column (or make it label-only with zero authz meaning, documented as such). The "requires portfolio membership" rule is simply wrong — status updates are project-level.

**PD-27 — ERD is missing entities/fields the spec requires:** (a) **invites table** (A3: tokenized invites, expiry, revoke) and **password-reset tokens** (A2) — nowhere in the schema or auth module; (b) `projects.owner_id` (spec B2: owner field; the arch's own RBAC references "project owner" against a table with no owner); (c) `projects.default_view` (B2); (d) task **priority** (PD-19); (e) `portfolios.description` (D1). Also **extra**: `tasks.start_date` — spec says tasks have a due date only; remove or mark dormant. The Tech Lead should regenerate the ERD against §3.1 of the spec line by line.

**PD-28 — Search mechanism mismatch.** Spec E2: prefix + substring match on titles/names, full-text explicitly Phase 1. Architecture: `tsvector` column + GIN index (word-stem full-text — which does *not* do substring matching, and builds the Phase-1 feature early). Align: `ILIKE`/`pg_trgm` on titles/names for MVP; drop `search_vector` from the MVP schema (add in Phase 1 with descriptions/comments).

**PD-29 — Three URL/ID schemes.** Spec: `/task/{id}`. Architecture: UUIDv7 everywhere. Design §3.2: short slugs (`PRJ-8fk2`), `/tasks/:taskId`, plus `?task=` overlay params. Deep links are foundational (notifications, search, Teams pastes) and routing is week-1 work. Decide one scheme in week 1 (my default ruling unless argued: UUIDv7 in API, design's path structure `/tasks/:id` + `?task=` overlay for the web, no vanity slugs in MVP) and update all three docs.

**PD-30 — Design re-adds features the spec cut to Phase 1:** filter popover (assignee/status/due) and group-by (Assignee/Due) in the list toolbar (4.a), and filter serialization to query params (§1 principle 4). Spec §5 parks filters/saved views in Phase 1. Remove from MVP screens.

**PD-31 — Unscoped design additions, batch ruling.** Never requested by the spec: starred projects (needs a new table), resizable + width-remembering drawer, per-user column widths, natural-language date parsing ("tomorrow", "fri"), project-create templates + seeded sample project, one-shot hint state, theme toggle. Rulings: **cut** NL date parsing, starring, drawer/column width persistence (plain date picker, fixed 520px drawer, fixed columns); **keep** the create-project templates ("Blank" + "Simple ops checklist") and the seeded sample project — they are cheap and directly serve pilot onboarding — now added to the spec (B2) via the decision log; hint state goes to localStorage (no schema). Note the reverse miss: spec E1's "Recent projects" cluster in the sidebar is *absent* from the design — restore it (it is cheaper than starring and serves the same need).

**PD-32 — My Tasks groups differ** (design drops Overdue/No date). Align to spec — see PD-15.

**PD-33 — Two different status-update rituals.** Spec D3: an "Update status" composer (color + prefilled title + body), history on the Overview tab. Design 4.d: inline chip editing in the portfolio table with an "optional one-line note" in the picker, logged to a "lightweight status log." These are different data models and different habits. Ruling: spec's composer is canonical (title+body survive to the Overview history and §7 metrics depend on it); the design's inline chip may stay **as an entry point that opens the same composer** prefilled with the chosen color. Designer updates 4.d.

**PD-34 — Avatars:** spec says initials-only in MVP; architecture carries `avatar_url`; design specs "image when set" with no upload flow anywhere. Initials only; the column may stay dormant.

**PD-35 — Spec-internal wobble to fix while editing:** (a) D2 staleness 14d vs §7 metric 7d (PD-10); (b) B2 names a third view tab "Overview" while §1.2/design speak of List|Board — Overview is fine but must appear in the view-switcher spec consistently (design 4.d has tabs only on portfolios, not the project Overview tab from B2 — Designer to add the project Overview tab, which hosts the status history per D3).

---

## 4. Governance gaps

**PD-36 — No Definition of Done anywhere.** Adopt, per story: (1) AC pass demoed in the running app; (2) tests per architecture §8 for that layer (integration test for any new endpoint; unit for ordering/authz logic); (3) axe scan + keyboard pass on new surfaces (Designer's §6 checklist, scoped to the story); (4) deployed to the staging compose stack; (5) no open `design-qa` blockers. Written into the repo as `DEFINITION-OF-DONE.md`, enforced at sprint review.

**PD-37 — No demo cadence or delivery checkpoint.** The design doc has Tue/Thu design reviews (good — keep) but there is no whole-team ritual. Adopt: **end-of-sprint demo** (60 min, all four, live app, walk the ACs — the sprint's stories are done or they visibly are not), plus a **weekly 30-min PD checkpoint** (scope/risk/decision review, Mondays). Three demos total before pilot; demo 3 (end week 6) doubles as the pilot go/no-go (PD-40).

**PD-38 — No decision log, and unowned "decisions" are already accumulating.** The design doc "decides" a command palette and side-peek; the architecture self-approves; the spec provisionally names the product. Adopt a single `DECISIONS.md` (numbered, date, decision, owner, docs affected). Retro-log immediately: side-peek-not-modal (ratified — it is genuinely right), command palette (reversed per PD-5), Cairn naming (see PD-42), plus every ruling in this review once the team responds. Related: **02-architecture must revert its status from "Approved for Sprint 1" to Draft** — nothing is approved before the spec it depends on is signed; sign-off order is spec → architecture/design → build.

**PD-39 — No scope-change rule.** For the remaining 6 weeks: **any user-facing capability not in spec Appendix A requires (a) PD sign-off in the decision log and (b) a named, equal-effort cut from the same sprint** ("one-in-one-out"). The Designer's "tokens frozen after Sprint 1, changes versioned not silent" is exactly the right instinct — this rule generalizes it to scope. Fast-tracked approvals from this review: status-nudge block (PD-9), CSV export (PD-11, stretch), templates + sample project (PD-31), possibly portfolio status (PD-12, pending sizing).

**PD-40 — No feature freeze or pilot gate.** Adopt: **feature freeze end of week 5**; week 6 is hardening, a11y sweep, k6 run, restore drill, seed data, pilot comms. **Go/no-go checklist for end of week 6:** Playwright smoke green on the prod stack; backup restore drill executed (arch §6.2 — verify it happened, don't assume); the two pilot programs seeded (spec open question 4); kickoff deck with the honest-ledger framing (PD-16); rollback tag tested. Any red item slips the pilot by a week rather than shipping soft.

**PD-41 — Delivery risks are unmanaged (the arch register is technical-only).** Add a PD-owned delivery risk register: (1) **bus factor = 1** on the Product Engineer — mitigations: TL reviews all PRs, no unreviewed merge to main, runbook current; (2) **design-spec latency** — the "specs ≥3 working days before build slot" agreement has no tracking; make spec-readiness a visible checklist in the weekly checkpoint; (3) **part-time TL review latency** blocking the PE — agree a <24h PR review SLA or pre-delegated merge rights on low-risk areas; (4) **pilot adoption risk** — owned by me with the Ops Director, per §7's kill criterion.

**PD-42 — Open questions: answered where they are mine.** (Q3) **"Cairn" is ratified** — stop tracking, use it everywhere including the repo. (Q1) Timeline drag-editing: resolved *now* by PD-2 (out of MVP), not "end of Sprint 2." (Q2) SMTP availability: I will chase IT for an answer by end of week 1 — the PD-9 nudge works without it, so it gates niceness, not function. (Q4) Pilot programs: I will bring two candidate programs (3–5 projects each) to the week-5 checkpoint.

---

## 5. Verdicts and change lists (respond point by point)

### 01-product-spec.md — **APPROVE-WITH-CHANGES** (author: Product Engineer)

1. §4.3 C1 + §4.4 D3: change task description, comments, and status-update body to **plain text + auto-linked URLs**; move rich text to Phase 1 (§5 table). [PD-8, PD-24]
2. §4.4 D2: staleness threshold **14d → 7d**, matching §7's flagship metric. [PD-10]
3. §4.4 D4: rewrite to the simplified timeline — read-only bars, fixed month zoom, today line, "No dates" tray with inline date editing; delete zoom presets and the drag-edit/fallback clause; move drag-editing to Phase 1 in §5. Delete open question 1. [PD-2, PD-23]
4. §4.2 B3: state the fixed column set; add (or explicitly decline) sort-by-priority; confirm filters/group-by remain Phase 1. [PD-3, PD-14a, PD-30]
5. §4.2 B5: add the "Your projects need a status update" owner-nudge block to My Tasks as an AC. [PD-9]
6. §4.4 D3: add "copy previous update" prefill AC. [PD-13]
7. §4.2 B2: add create-project templates ("Blank", "Simple ops checklist") + deletable sample project. [PD-31]
8. §5: add "CSV export of portfolio roll-up" as MVP-stretch (first claim on Sprint-3 slack). [PD-11]
9. Decide portfolio-level status (add with AC, or record as declined) after PE sizing. [PD-12]
10. §4.5 E2: state the MVP search mechanism as ILIKE/trigram on titles/names (no tsvector). [PD-28]
11. Confirm one URL scheme with TL + Designer and state it in C1. [PD-29]
12. §2.1: add one sentence making explicit that "lead" confers no permissions anywhere (heads off PD-26 recurring).
13. Status enum: confirm `on_hold` (no "Blocked") as the ratified vocabulary. [PD-17]

### 02-architecture.md — **APPROVE-WITH-CHANGES** (author: Tech Lead)

1. Revert document status to **Draft** pending spec v1.1 sign-off; sign-off order is spec → arch. [PD-38]
2. Replace `task_assignees` M:N with nullable `tasks.assignee_id` (or add an enforced single-assignee constraint and document why the join table survives). [PD-20]
3. Add to ERD: `invites` (token, email, expiry, revoked) and password-reset tokens; `projects.owner_id`; `projects.default_view`; `tasks.priority`; `portfolios.description`. Remove (or mark dormant) `tasks.start_date`. Re-walk ERD against spec §3.1. [PD-27]
4. Remove the `private` project flag from RBAC behavior and the matrix; at most a dormant column with nothing reading it. [PD-25]
5. Remove `team_members.role` authz meaning; fix the RBAC matrix rows gating project edit/status posting to "owner/lead" — any member per spec §2.1. Correct the §2.2 example rule "status update requires portfolio membership" (status is project-level, any member). [PD-26]
6. Drop `search_vector`/GIN from MVP schema; MVP search = ILIKE/pg_trgm on titles/names; tsvector returns in Phase 1. [PD-28]
7. Confirm URL/ID ruling with the team (UUIDv7 stands; no slugs in MVP) and reflect the agreed web URL scheme. [PD-29]
8. Add build-order note: SSE scheduled after core flows in Sprint 3 with the pre-agreed cut to refetch-on-focus (formalizing risk #4's mitigation as a schedule rule). [PD-4]
9. Add notification retention (90d, spec F1) as a purge job note — currently unaddressed.
10. Status enum: no change (already matches spec) — noted for the record vs design. [PD-17]

### 03-design.md — **REWORK** (author: Designer)

The craft is high and much of this document survives verbatim (tokens, type, side-peek, empty states, a11y method). REWORK because the following list changes the product being designed, not its polish — resubmit v2 against spec v1.1:

1. Status vocabulary: replace **Blocked → On hold** everywhere (§2.3 table, chips, timeline bars, 4.d sort order); adopt the spec enum. [PD-17]
2. Remove the task-level **Status** field: 4.a column, 4.c field grid, `s` shortcut. [PD-18]
3. Priority enum → `none/low/medium/high` (no Urgent). [PD-19]
4. Notifications are **in MVP**: restore bell + unread badge (agree placement with PE; spec says sidebar) and design the inbox list (spec F1) — currently undesigned. [PD-21]
5. Delete the **Home** surface; My Tasks is the landing page; move the first-run checklist onto My Tasks. [PD-22]
6. My Tasks groups: **Overdue / Today / Upcoming / Later / No date**, Overdue first. [PD-15, PD-32]
7. Timeline (4.e): redraw to PD-2 — read-only bars, fixed month zoom, today line, tray with inline date editing; delete drag/resize/zoom/virtualization/keyboard-nudge specs.
8. List view (4.a): remove filter popover, group-by (except Section), column show/hide/resize/persistence. [PD-3, PD-30]
9. Command palette (§3.4) → plain Ctrl+K search per spec E2; delete verbs/actions. [PD-5]
10. Remove dark theme from MVP acceptance (keep token architecture; remove toggle + dual-theme CI gate). [PD-6]
11. Motion (§5): reduce to items 1, 3, 8 as JS; the rest CSS or cut; week-5 pairing is conditional on a green Sprint 3. [PD-7]
12. 4.d: portfolio status chip pends the PD-12 decision; inline chip edit opens the spec D3 composer (color+title+body) rather than a chip-only "status log". Add the **project** Overview tab (B2) hosting status history. [PD-33, PD-35b]
13. Remove: starred projects, NL date parsing, resizable/persistent drawer + column widths; restore the spec's "Recent projects" sidebar cluster. Keep: templates + sample project (now ratified into spec). [PD-31]
14. Align URL scheme to the PD-29 ruling (drop `PRJ-8fk2` slugs).
15. Keyboard map: reduce to Ctrl+K, Enter, Esc, x, ↑/↓, quick-add; drag alternative = "Move to…" menu, not Space-pickup DnD. [PD-3, PD-14b]

### Immediate next steps (this week)

1. Authors respond point-by-point to their change lists; disputes come to Friday's checkpoint with the decision log open.
2. PE sizes PD-12 (portfolio status); TL confirms PD-20/PD-27 schema changes before any migration is written.
3. `DECISIONS.md` + `DEFINITION-OF-DONE.md` created in-repo (I will draft both today). [PD-36, PD-38]
4. Spec v1.1 published; architecture and design v2 follow it; Sprint 1 builds only against signed docs.
