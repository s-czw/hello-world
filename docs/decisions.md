# Decision log (ADR-lite)

The single source of truth for project decisions (delivery plan §4). Format: number, date, decision, context, owner, docs affected, status. Anything "decided" outside this file is not decided.

| # | Date | Decision | Context | Owner | Docs | Status |
|---|---|---|---|---|---|---|
| D-001 | 2026-07-20 | Build our own tool instead of buying | Benchmark of 9 tools: portfolio layer is universally the premium upsell; Asana Advanced = $625/mo at 25 seats | Owner | pm-tool-benchmark/ | Ratified |
| D-002 | 2026-07-20 | Stack: TypeScript end-to-end — Next.js, NestJS, PostgreSQL 16, Redis 7, pnpm workspaces | One language, small team maintainability | Owner + Tech Lead | 02 | Ratified |
| D-003 | 2026-07-20 | Hosting: Docker Compose on self-controlled VM; Caddy for proxy/TLS | Simplest to self-manage; host-agnostic | Owner + Tech Lead | 02 | Ratified |
| D-004 | 2026-07-20 | Timeline: 6-week MVP (3×2-week sprints) incl. core work mgmt AND portfolio layer; 4-week pilot | Aggressive by choice; scope cut accordingly (PD-1) | Owner | 01, 04 | Ratified |
| D-005 | 2026-07-20 | Working name: **Cairn** | PD-42; repo/monorepo name `cairn` | Project Director | all | Ratified |
| D-006 | 2026-07-20 | Task detail = side-peek drawer (not modal); fixed 520px | Asana's best interaction; keeps list context | Designer | 01 C1, 03 §4.c | Ratified |
| D-007 | 2026-07-20 | Ctrl+K is plain typeahead search, NOT a command palette | PD-5; creation verbs → Phase-1 backlog | Project Director | 01 E2, 03 §3.4 | Ratified (palette reversed) |
| D-008 | 2026-07-20 | IDs/URLs: UUIDv7 everywhere; `/tasks/:id` + `?task=` overlay; no vanity slugs in MVP | PD-29; one scheme across all docs | Tech Lead | 01 C1, 02 §4.1, 03 §3.2 | Ratified |
| D-009 | 2026-07-20 | Project status enum: `on_track / at_risk / off_track / on_hold` (no "Blocked"); status exists on projects only, not tasks | PD-17, PD-18 | Product Engineer | 01, 02, 03 | Ratified |
| D-010 | 2026-07-20 | Two org roles only (admin/member); team "lead" is a display label with zero authz meaning | PD-26 | Product Engineer | 01 §2.1, 02 §6.4 | Ratified |
| D-011 | 2026-07-20 | Plain text + auto-linked URLs everywhere (descriptions, comments, status bodies); rich text = Phase 2 (spec "Phase 1") | PD-8/PD-24 | Product Engineer | 01, 02, 03 | Ratified |
| D-012 | 2026-07-20 | Portfolio timeline MVP = read-only bars, fixed month zoom, today line, editable "no dates" tray; drag/zoom → later | PD-2/PD-23; fallback floor = sorted date-bar table (risk R2) | Project Director | 01 D4, 03 §4.e, 04 R2 | Ratified |
| D-013 | 2026-07-20 | SSE realtime is the LAST Sprint-3 item; pre-agreed cut to optimistic update + refetch-on-focus, no meeting required | PD-4; arch §4.4 | Tech Lead | 02, 04 | Ratified |
| D-014 | 2026-07-20 | D5 portfolio-level status: in MVP only if ≤1 PE-day (same composer retargeted); auto-drops otherwise | PD-12/PD-39 | Product Engineer | 01 D5, 03 §4.d, 04 §3.3 | Ratified (conditional) |
| D-015 | 2026-07-20 | MVP ships light theme only; token architecture stays dark-ready | PD-6 | Designer | 03 §2.1 | Ratified |
| D-016 | 2026-07-20 | Scope-change rule: nothing enters MVP without a PD-signed entry here AND an equal-effort cut (one-in-one-out); feature freeze Fri 21 Aug | PD-39/PD-40 | Project Director | 04 §4 | Ratified |
| D-017 | 2026-07-20 | Full PMO review PD-1…PD-42 dispositions | See review-pmo.md and each doc's disposition appendix | Project Director | all | Retro-logged |

Open items being tracked: SMTP availability on the VM (IT, due end of week 1) · the two pilot programs (PD, due week-5 checkpoint).
