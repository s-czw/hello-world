# Cairn — Development Plan

**Cairn** (working name, ratified) is a self-hosted, Asana-inspired program & project management tool for a 10–50 person mixed-operations team in a Microsoft 365 organization. It exists because every benchmarked commercial tool gates the portfolio/program layer behind a premium tier (see [`../pm-tool-benchmark/research-notes.md`](../pm-tool-benchmark/research-notes.md)); in Cairn the portfolio is a day-1 primitive on infrastructure we own.

**Current baseline (D-028 — internal-first, single-org):** build for our own team first; commercialize (multi-tenant SaaS) only if the pilot proves value — a **gated future phase**, not day-1 work. Team = **you + Claude Code** (`docs/05` is the delivery plan of record). Target = a **usable minimum viable product in ~2 weeks** (M1), then the portfolio layer (M2), then collaboration/polish (M3), then an internal pilot; **dates flexible, milestone-gated by owner review**.

**Locked stack decisions:** **Java 21 / Spring Boot API + Next.js/React web** (D-026), PostgreSQL 16 + jOOQ/Flyway, Valkey 8, Traefik TLS; web↔api contract is OpenAPI-generated · permissive-license-only stack, CI-gated on npm + Maven (JVM runtime is Temurin OpenJDK, GPLv2 + Classpath Exception — documented exception) · **modular monolith** (D-027) · **MVP is single-org and lean** — one API replica, in-process jobs, direct DB pool, local-disk attachments, no realtime channel; multi-tenancy/RLS/PgBouncer/worker-split/S3/metrics/SSO are all **deferred to the commercialization phase** (arch §10, D-028).

## Documents

| Doc | Author (role) | What it covers |
|---|---|---|
| [01-product-spec.md](01-product-spec.md) | Product Engineer | Vision & principles, personas, domain model, MVP feature spec (user stories + acceptance criteria), out-of-MVP cuts, differentiation vs Asana, pilot success criteria incl. kill/pivot rule |
| [02-architecture.md](02-architecture.md) | Tech Lead | Monorepo & module architecture, full ERD, API design, multi-provider auth (local + Lark toggleable, Entra-ready), SSE realtime with pre-agreed cut, storage, non-functionals, Docker Compose deployment, testing strategy, technical risks, licensing & commercialization audit |
| [03-design.md](03-design.md) | Designer | Design principles, token system (light theme MVP, dark-ready), component inventory, IA & URL scheme, key screen specs, anime.js v4 motion spec (3 JS moments), WCAG 2.2 AA commitments, sprint-by-sprint design deliverables |
| [05-claude-build-plan.md](05-claude-build-plan.md) | — | **THE PLAN OF RECORD (D-025/D-028).** How Cairn is actually built: you + Claude, single-org, milestone sequence (M1 ≈ 2-week MVP → M2 portfolio → M3 polish → pilot) with paste-ready prompts, the review loop, owner-bottleneck risks, and what stays your job. |
| [04-delivery-plan.md](04-delivery-plan.md) | Project Director | **Superseded for team/scope/schedule** (see banner). Still current: Definition of Done, governance/scope-change rules, UAT script, rollback plan, pilot adoption thinking. |
| [review-pmo.md](review-pmo.md) | Project Director | The adversarial PMO review (findings PD-1…PD-42) that all documents above were revised against — kept for traceability |
| [decisions.md](decisions.md) | All (PD owns) | ADR-lite decision log — the single source of truth for "what did we decide" |

## How these documents were produced

Drafted by a four-role virtual team (Product Engineer, Tech Lead, Project Director, Designer) in four passes: parallel drafting → adversarial PMO review (42 numbered findings) → per-document revision with full feedback disposition (applied/rebutted, zero rebuttals) → delivery planning. Each document ends with its feedback-disposition appendix.

The repo root **`CLAUDE.md`** is the always-loaded operating manual for build sessions: the locked stack, the non-negotiable invariants, and the Definition of Done, distilled from the docs above.

## Reading order

New to the project: README → `05` (the plan of record) → 01 → 02 → 03. **Building it: `CLAUDE.md` → `05`, then start at M1 (the ~2-week single-org MVP).** 01 Appendix A is the MVP scope; `decisions.md` D-028 is the current baseline.
