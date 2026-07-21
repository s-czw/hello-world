# Cairn — Development Plan

**Cairn** (working name, ratified) is a self-hosted, Asana-inspired program & project management tool for a 10–50 person mixed-operations team in a Microsoft 365 organization. It exists because every benchmarked commercial tool gates the portfolio/program layer behind a premium tier (see [`../pm-tool-benchmark/research-notes.md`](../pm-tool-benchmark/research-notes.md)); in Cairn the portfolio is a day-1 primitive on infrastructure we own.

**Locked decisions:** TypeScript end-to-end (Next.js + NestJS + PostgreSQL + Valkey, Traefik for TLS) · permissive-license-only runtime stack (commercialization-safe, CI-gated) · **multi-tenant SaaS architecture from day 1** (RLS tenant isolation, S3 storage, per-org quotas, scale-tier roadmap T0→T3 — D-022) · Docker Compose self-hosted at tier T0 · multi-provider auth with per-provider enable/disable — local email/password + Lark sign-in (week 7), Entra ID later · MVP in 6 weeks (3 × 2-week sprints, 20 Jul – 28 Aug 2026) including core work management and the portfolio layer, with the D-022 scope cuts (bar timeline → schedule table; D5, search, templates, stretch ladder deferred to Phase 2) · 4-week pilot from 31 Aug.

## Documents

| Doc | Author (role) | What it covers |
|---|---|---|
| [01-product-spec.md](01-product-spec.md) | Product Engineer | Vision & principles, personas, domain model, MVP feature spec (user stories + acceptance criteria), out-of-MVP cuts, differentiation vs Asana, pilot success criteria incl. kill/pivot rule |
| [02-architecture.md](02-architecture.md) | Tech Lead | Monorepo & module architecture, full ERD, API design, multi-provider auth (local + Lark toggleable, Entra-ready), SSE realtime with pre-agreed cut, storage, non-functionals, Docker Compose deployment, testing strategy, technical risks, licensing & commercialization audit |
| [03-design.md](03-design.md) | Designer | Design principles, token system (light theme MVP, dark-ready), component inventory, IA & URL scheme, key screen specs, anime.js v4 motion spec (3 JS moments), WCAG 2.2 AA commitments, sprint-by-sprint design deliverables |
| [04-delivery-plan.md](04-delivery-plan.md) | Project Director | Plan on a page, RACI, sprint plans with demo checkpoints and exit criteria, ceremonies & governance, top-10 risk register with triggers/fallbacks, post-MVP roadmap gates, UAT & rollback (assumes a **human team**) |
| [05-claude-build-plan.md](05-claude-build-plan.md) | — | **How to actually build Cairn with Claude Code** (execution model D-025): you + Claude, milestone build sequence with paste-ready prompts, the review loop (adversarial isolation drills), Claude-specific risks, and what stays your job. The practical companion to 04. |
| [review-pmo.md](review-pmo.md) | Project Director | The adversarial PMO review (findings PD-1…PD-42) that all documents above were revised against — kept for traceability |
| [decisions.md](decisions.md) | All (PD owns) | ADR-lite decision log — the single source of truth for "what did we decide" |

## How these documents were produced

Drafted by a four-role virtual team (Product Engineer, Tech Lead, Project Director, Designer) in four passes: parallel drafting → adversarial PMO review (42 numbered findings) → per-document revision with full feedback disposition (applied/rebutted, zero rebuttals) → delivery planning. Each document ends with its feedback-disposition appendix.

The repo root **`CLAUDE.md`** is the always-loaded operating manual for build sessions: the locked stack, the non-negotiable invariants, and the Definition of Done, distilled from the docs above.

## Reading order

New to the project: README → 04 (plan on a page) → 01 → 02 → 03. **Building it with Claude Code: `CLAUDE.md` → `05-claude-build-plan.md`, then start at Milestone 0.** 01 Appendix A is the frozen MVP scope.
