# Program & Project Management Tool Benchmark — Research Notes

**Date:** 20 July 2026
**Buyer context:** 10–50 person team, mixed/general operations work (not primarily software development).
**Hard requirements:** (1) program/portfolio layer — multi-project roll-ups, cross-project Gantt & dependencies, portfolio dashboards; (2) deep Microsoft 365 / Teams integration.
**Deliverable:** interactive comparison page (`comparison.html` in this folder, also published as a Claude artifact).

## Method

Three parallel research passes (one per group of three tools) using vendor pricing/feature pages, vendor help-center documentation, Microsoft Learn/AppSource listings, G2/Capterra, and 2025–2026 analyst comparisons (Gartner MQ for Adaptive Project Management & Reporting, Forbes Advisor, PCMag, TechRadar). Several vendor pricing pages block automated access, so figures were cross-checked across at least two independent 2026-dated sources; the top three tools' prices (Asana Advanced $24.99, Smartsheet Business $19, Wrike Business $25) were re-verified in a separate pass. Items that could not be confirmed against a primary source are marked **unverified** throughout. Scores are 1–5 judgments against this team's specific context — not absolute product quality.

## Consolidated scorecard

Criteria weights (default): portfolio 25%, M365 25%, ops fit 15%, reporting 10%, ease 10%, pricing value 10%, admin 5%.

| Tool (portfolio tier) | Portfolio | M365 | Ops fit | Reporting | Ease | Value | Admin | **Weighted** |
|---|---|---|---|---|---|---|---|---|
| **Asana** (Advanced) | 5 | 5 | 4 | 4 | 4 | 3 | 4 | **4.40** |
| **Smartsheet** (Business) | 4 | 5 | 4 | 4 | 4 | 3.5 | 4 | **4.20** |
| **Wrike** (Business) | 5 | 4.5 | 4 | 4 | 3 | 3 | 3.5 | **4.15** |
| **MS Planner/Project** (Plan 3) | 4 | 5 | 3 | 3 | 4 | 3 | 4 | **3.90** |
| **Zoho Projects** (Enterprise) | 4 | 3 | 4 | 4 | 3 | 5 | 3 | **3.70** |
| **Monday.com** (Pro*) | 3 | 3 | 5 | 4 | 5 | 3 | 3 | **3.60** |
| **ClickUp** (Business) | 3 | 2 | 5 | 4 | 3 | 5 | 3 | **3.35** |
| **Jira** (Premium) | 4 | 3 | 3 | 3 | 2 | 4 | 3 | **3.25** |
| **Airtable** (Team*) | 3 | 2 | 5 | 4 | 3 | 3 | 4 | **3.20** |

\* Monday Pro and Airtable Team only approximate the portfolio requirement — Monday's true Portfolio solution is Enterprise-only (custom pricing) and Airtable's portfolio is a DIY build.

## Cost at 25 seats (portfolio-capable tier, annual billing)

| Tool | Tier | $/user/mo | 25-seat cost/mo | Notes |
|---|---|---|---|---|
| Zoho Projects | Enterprise | $9 | **$225** | Cheapest genuine portfolio layer |
| ClickUp | Business | $12 | **$300** | No official Power Automate/Power BI |
| Jira | Premium | $14.54 | **$364** | + Guard ~$4.20/user for SAML SSO |
| Smartsheet | Business | $19 | **$475** | + Control Center (custom) for full program governance |
| Monday.com | Pro (DIY roll-up) | $19 | **$475** | True portfolio = Enterprise, custom (~$24–30+, unverified) |
| Airtable | Team | $20 | **$500** | SSO needs Business at $45/user ($1,125/mo) |
| Asana | Advanced | $24.99 | **$625** | Everything needed in one self-serve tier |
| Wrike | Business | $25 | **$625** | Annual-only; SSO needs Pinnacle |
| MS Planner/Project | Plan 3 | $30 | **$750** | Mixed licensing (5× Plan 3 + 20× Plan 1) ≈ $350 |

## Recommendation

1. **Top pick — Asana Advanced ($24.99/user).** The only tool that satisfies both hard requirements out of the box on a published, self-serve tier: unlimited + nested Portfolios with cross-project Gantt, workload and Goals, plus the deepest third-party Microsoft stack (native Teams app, Outlook add-in, certified Power Automate connector, native Power BI connector). Caveats: priciest published portfolio tier; SAML SSO is Enterprise-gated; no native docs surface.
2. **Runner-up — Smartsheet Business ($19/member).** The only tool with official Microsoft-published Power Automate *and* Power BI connectors, plus Teams tabs with in-Teams approvals; reports roll up to 30,000 sheets into portfolio Gantt dashboards; unlimited free guest editors. Caveats: dependencies are single-sheet only (cross-sheet = manual cell links); full program governance (Control Center) is a custom-priced add-on; SSO Enterprise-only.
3. **Worth shortlisting:**
   - **Wrike Business** if structured PPM depth matters most (native program hierarchy, cross-project dependencies, critical path, baselines) and the team can absorb a steeper learning curve.
   - **Microsoft Planner Plan 3 (mixed licensing ≈ $350/mo)** if staying all-Microsoft outweighs feature depth — accepting no cross-plan dependencies and DIY Power BI portfolio reporting.
   - **Zoho Projects Enterprise ($225/mo)** as the value option with a genuine portfolio layer including real inter-project dependencies.
4. **Ruled out for this brief:** Monday.com (best ease/ops fit but portfolio gated behind custom Enterprise), ClickUp and Airtable (M365 integration too weak for a hard requirement), Jira (portfolio strong and cheap, but weakest ease-of-use for a general-ops team and no native SharePoint/Power BI).

**Suggested next step:** 2-week pilot of Asana Advanced and Smartsheet Business with one real program each (3–5 projects), evaluating portfolio roll-up quality, Teams/Outlook workflow fit, and reporting against Power BI.

---

*The three appendices below are the full research passes, preserved verbatim (including per-tool source URLs and unverified flags).*


# Appendix A — Research pass 1: Jira, Monday.com, Asana

# Agent A findings: Jira, Monday.com, Asana
Research date: 2026-07-20. Vendor pricing pages blocked to fetcher; figures cross-verified across multiple independent 2026 pricing guides, vendor support docs via search, and comparison sites. Unconfirmed items marked unverified.

## Tool: Atlassian Jira (incl. Premium "Plans"; Jira Work Management status)

**Status note — Jira Work Management (JWM):** JWM no longer exists as a separate product. Announced at Team '24, Atlassian merged Jira Software and Jira Work Management into a single product called simply "Jira." New JWM subscriptions ended 1 May 2024; existing subscribers migrated through early 2025. Merged Jira keeps two project types — "Software" and "Business" — so business teams get JWM's list/calendar/board views, business templates, and forms inside standard Jira at no separate SKU.

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 4 | Plans (Advanced Roadmaps) on Premium: cross-project Gantt, cross-project dependency mapping, capacity planning, what-if scenario sandboxing. Docked: portfolio dashboards are gadget-based and dated; goals/strategy roll-up sits in separate Atlassian Home/Focus surfaces rather than in Plans. |
| m365 | 3 | Solid official Teams app and Outlook add-in plus Microsoft-listed Power Automate connector, but no native SharePoint or Power BI connector — both require Power Automate flows, DIY API work, or paid marketplace apps. Notifications one-directional (Jira→Teams). |
| ops_fit | 3 | Post-merge Jira ships business templates (marketing, HR, finance, ops), forms, list/calendar views — far friendlier to non-dev teams than before — but underlying model (issues, workflow schemes, permission schemes) remains engineered for software teams; heavy for general ops. |
| reporting | 3 | Strong built-in agile/project reports and configurable dashboards (default 20 gadgets/dashboard), but dashboards look dated, cross-project business reporting clunky, Atlassian Analytics is Enterprise-only; BI needs third-party connectors. |
| ease | 2 | Lowest ease-of-use of the three (Capterra ease 4.1 vs monday 4.5); admin/config learning curve real; Plans takes training. |
| pricing_value | 4 | Cheapest path to true portfolio: Premium ≈ $14.54/user/mo annual → ~$364/mo for 25 users (~60% of Asana's portfolio-tier cost). Docked: SAML SSO costs extra (Guard), adoption effort eats savings. |
| admin | 3 | Granular permissioning, audit logs, sandbox on Premium; but SAML SSO/SCIM is paid add-on (Atlassian Guard ~$4.20/user/mo); no free guest role in Jira — external collaborators consume paid licenses (free "customer" accounts only in JSM). |

### Pricing (Jira Cloud, per user/month)
| Tier | Annual | Monthly | Notes |
|---|---|---|---|
| Free | $0 | $0 | Up to 10 users, 2 GB, boards, backlog, single-project Timeline; no Plans |
| Standard | $7.91 | $8.15 | 250 GB storage; 1,700 automation runs/site/mo |
| Premium | $14.54 | $15.25 | **Unlocks Plans (portfolio)**; unlimited storage, sandbox, 1,000 automation runs/user/mo, AI, 24/7 support, 99.9% SLA |
| Enterprise | Custom (annual only, ~800+ users) | — | Multi-site, Atlassian Analytics, Guard Standard included |

- Portfolio tier: **Premium**. No hard seat minimum; rates step down at ~100/250/500 users.
- **25 users on Premium ≈ $363.50/mo (~$4,362/yr annual).** Standard: ~$197.75/mo.
- Hidden cost: SAML SSO needs Atlassian Guard ~$4.20/user/mo (Guard Premium ~$8.18) → +~$105/mo for 25 users.

### M365/Teams specifics
- Teams (official app, free): project tabs; create/search/update/assign/comment work items; channel notifications; actionable link cards; Rovo AI drafts work items from Teams messages. Limitations: one-directional sync (Jira→Teams); issue creation from chat manual.
- Outlook (official AppSource add-in): create issues from email; view/act on issues from inbox.
- SharePoint/OneDrive: **no native connector** — Power Automate flows or paid Marketplace apps.
- Power Automate: Microsoft-listed Jira connector exists (issue create/update triggers/actions).
- Power BI: **no native connector** — REST API / third-party (CData, Marketplace).

### Portfolio specifics
- Name: **Plans** (ex Advanced Roadmaps / Portfolio for Jira). Premium/Enterprise only.
- Combine issue sources from unlimited projects/boards/filters into one cross-project roadmap; hierarchy above epics (initiatives/themes); cross-project dependency visualization; capacity planning; auto-scheduling; what-if sandbox; shareable views.
- Limits: ~5,000 issues/plan default (unverified); dashboards 20 gadgets default; goals/OKR roll-up not in Plans (Atlassian Home/Focus, Enterprise-positioned).

### Ratings
- G2: 4.3/5, ~7,500+ reviews (2026). Capterra: 4.4/5, 15,381 reviews.

### Sources (accessed 2026-07-20)
- atlassian.com/software/jira/pricing (blocked; cross-checked) · tech.co/project-management-software/jira-pricing · automationatlas.io/answers/jira-pricing-explained-2026 · costbench.com/software/project-management/jira · workmanagementhub.com/jira-pricing-2026
- community.atlassian.com/forums/Jira-articles/Jira-Work-Management-is-now-part-of-Jira/ba-p/2683620 · automation-consultants.com/the-future-of-jira-work-management
- atlassian.com/software/jira/guides/advanced-roadmaps/overview · support.atlassian.com/jira-software-cloud/docs/plan-and-view-cross-project-work-with-advanced-roadmaps
- support.atlassian.com/jira-software-cloud/docs/integrate-jira-cloud-with-microsoft-teams · marketplace.microsoft.com/en-us/product/office/wa200000056 · learn.microsoft.com/en-us/connectors/jira
- atlassian.com/software/guard/pricing · getpulsesignal.com/pricing/atlassian-guard
- capterra.com/compare/19319-147657/JIRA-vs-monday-com · g2.com/products/jira/reviews · atlassian.com/software/jira/templates

## Tool: Monday.com (monday work management)

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 3 | Pro gives credible DIY roll-up (dashboards ≤20 boards, Gantt widget, dependency columns), but the real **Portfolio solution — portfolio boards, cross-project dependencies, resource planner, portfolio health/risk AI — is Enterprise-only** (custom pricing). |
| m365 | 3 | Native Teams app (tabs + bot + notifications), good 2-way Outlook calendar sync and email-to-item, OneDrive/Azure AD — but **no native Power BI connector and no official Power Automate connector** (both third-party); SAML SSO Enterprise-only. |
| ops_fit | 5 | Built precisely for mixed/general ops: fully custom boards/workflows, 200+ non-dev templates, WorkForms, monday Docs, no-code automations. |
| reporting | 4 | Attractive widget dashboards; caveat: tier-gating (boards/dashboard: 5 Standard / 20 Pro / 50 Enterprise), advanced analytics in Enterprise. |
| ease | 5 | Best-in-class adoption — Capterra ease 4.5, repeated top marks for UI; non-technical teams self-serve quickly. |
| pricing_value | 3 | Pro $19/seat fair ($475/mo for 25), but hard portfolio requirement → Enterprise custom (~$24–30+/seat reported, often 50+ seats), plus seat-bucket rounding. |
| admin | 3 | Unlimited free viewers all paid tiers; unlimited guests on Pro; but SAML SSO, audit log, advanced permissions, HIPAA in Enterprise. |

### Pricing (per seat/month)
| Tier | Annual | Monthly | Notes |
|---|---|---|---|
| Free | $0 | $0 | Max 2 seats, 3 boards, 500 MB; no guests |
| Basic | $9 | $12 | Unlimited viewers; no timeline/Gantt, no integrations/automations |
| Standard | $12 | $14 | Timeline & Gantt, calendar, guests (3 free, then 4 guests = 1 seat), 250+250 automation/integration actions/mo, dashboards ≤5 boards |
| Pro | $19 | $24 | Private boards, formulas, time-tracking, dependency automations, Gantt milestones + critical path, 25k+25k actions/mo, unlimited guests, dashboards ≤20 boards |
| Enterprise | Custom | — | **Unlocks Portfolio solution**, dashboards ≤50 boards, 250k actions, SAML SSO, audit, advanced permissions, HIPAA. Street ~$24–30+/seat, often 50+ seats expected (unverified) |

- Portfolio tier: **Enterprise** (Pro approximates via ≤20-board dashboards; standalone Pro projects cannot join a portfolio per monday support docs, June 2026).
- Minimum 3 seats; seat buckets (3/5/10/15/20/25…) — 6-person team pays for 10 seats. One 2026 source said Pro $30/seat; majority say $19 annual/$24 monthly.
- **25 users on Pro = $475/mo ($5,700/yr).** Enterprise custom: ~$600–750+/mo estimated (unverified).

### M365/Teams specifics
- Teams (native app + Copilot 365 app): embed boards as full tabs (views/filters/automations inside Teams); bot notifications and item creation; link unfurling; automation recipes → Teams channels.
- Outlook: create items from emails; **two-way Outlook Calendar sync**; iCalendar invites. Limitation: M365 Business on Exchange Online only (no personal/on-prem).
- SharePoint/OneDrive: OneDrive file attach/embed (reaches SharePoint doc libraries via file picker); no dedicated SharePoint list/site connector (unverified beyond file-level).
- Power BI: **no native connector** — third-party paid apps (Alpha Serve/Tempo, CData, Windsor.ai).
- Power Automate: **no official certified connector**; webhooks/API or middleware (Make, Zapier).
- Azure AD/Entra: SSO + SCIM on Enterprise.

### Portfolio specifics
- Name: **Portfolio solution** (Enterprise; full release 2024).
- Portfolio boards connecting project boards; Portfolio Health Snapshot (On track/At risk/Off track); Portfolio Risk Insights (AI daily risk list); All Projects Dashboard (≤200 project boards); cross-project dependencies; Resource planner; portfolio-level automations.
- Limits: Enterprise-exclusive; only Enterprise project boards connect; dashboards ≤50 boards, ~20,000-item cap across connected boards; Pro limited to DIY roll-ups.

### Ratings
- G2: 4.7/5 (~15,000–15,257 reviews product page). Capterra: 4.6/5, 6,040 reviews.

### Sources (accessed 2026-07-20)
- monday.com/work-management/pricing (blocked; cross-checked) · support.monday.com articles: 115005320209 (plans), 13337066797202 (portfolio), 23921675672466 (All Projects Dashboard), 360002187819 (dashboards), 360015643840 (Gantt), 360017556179 (action limits), 360000305419 (guests), 360010359819 (Teams), 360011895179 + 4404712396562 (Outlook)
- ir.monday.com portfolio full-release press release · marketplace.microsoft.com/en-us/product/web-apps/mondaycom.mondaydotcom
- alphaservesp.com/products/mondaycom/power-bi · tempo.io/products/power-bi-connector-for-monday
- costbench.com/software/project-management/monday · get-alfred.ai/blog/monday-pricing · kickconsulting.com.au monday pro-vs-enterprise · buyersprint.com/2026/04/03/mondaycom-pricing-2026
- g2.com/products/monday-com/reviews · capterra.com/compare/19319-147657/JIRA-vs-monday-com

## Tool: Asana

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 5 | Portfolios (Advanced tier): most complete mid-market portfolio layer — unlimited portfolios with status/timeline/workload roll-ups, portfolio-level Gantt, nested portfolios for program structure, native Goals tied to work — no enterprise upsell. |
| m365 | 5 | Deepest official Microsoft stack: well-regarded native Teams app (tasks from messages, unfurling, meetings), Outlook add-in, OneDrive/SharePoint file attach, certified Power Automate connector, **native Power BI connector** — only tool of the three with one (Advanced+). |
| ops_fit | 4 | Strong general ops — branching forms, rules/automation, non-dev templates, approvals, proofing, workload — but no native docs surface; opinionated task model suits some ops workflows less than monday. |
| reporting | 4 | Universal Reporting dashboards across projects/portfolios/goals; portfolio dashboards built in; slightly fewer widgets than monday; heavy BI leans on Power BI connector. |
| ease | 4 | Clean consumer-grade UX, fast onboarding; layered concepts (tasks/sections/projects/portfolios/goals) take orientation. |
| pricing_value | 3 | Advanced $24.99/user is priciest portfolio tier ($624.75/mo for 25, ~1.7× Jira Premium); seat buckets of 5 above 5 users; Enterprise-gated SAML. But everything needed is in one tier. |
| admin | 4 | **Free unlimited guests on all paid plans** (best external-access model); solid admin console; SAML SSO/SCIM and advanced governance need Enterprise. |

### Pricing (per user/month)
| Tier | Annual | Monthly | Notes |
|---|---|---|---|
| Personal (free) | $0 | $0 | Up to 10 teammates; list/board/calendar only; no Timeline/Gantt, no dashboards |
| Starter | $10.99 | $13.49 | Timeline & Gantt, forms, 250 automation runs/mo, limited dashboards; **no Portfolios/Goals/workload** |
| Advanced | $24.99 | $30.49 | **Unlocks Portfolios (unlimited) + Goals**, portfolio workload, universal reporting, branching forms, approvals, proofing, time tracking, 25k automation runs/mo, Power BI/Tableau/Salesforce connectors |
| Enterprise | Custom | — | SAML SSO, SCIM, admin data controls |
| Enterprise+ | Custom | — | Compliance pack (audit log API, data residency, eDiscovery) |

- Portfolio tier: **Advanced**. Minimum 2 seats. Buckets: +1 from 2–5; +5 up to 30; +10 to 100; +25 to 500 (25 users = exact bucket).
- **25 users on Advanced = $624.75/mo ($7,497/yr annual).**

### M365/Teams specifics
- Teams (native app): convert Teams messages into tasks; create/assign/edit tasks in Teams; pin projects and portfolios as tabs; rich link unfurling; tasks in Teams Meetings; channel notifications; search Asana from Teams.
- Outlook: official add-in (emails → tasks with context/attachments); Office 365 connector posts task activity to Outlook groups.
- SharePoint/OneDrive: attach files from SharePoint doc libraries via OneDrive integration; file-level only, no list/site sync.
- Power Automate: **official Microsoft-listed Asana connector**.
- Power BI: **native Asana connector** (tasks by project/team/portfolio). Requires Asana Advanced/Enterprise(+) + own Power BI license. Azure AD SSO/SCIM (Enterprise).

### Portfolio specifics
- Name: **Portfolios** + **Goals**, Advanced+.
- Unlimited projects per portfolio with real-time status/progress/priority/custom-field roll-ups; portfolio timeline/Gantt; **nested portfolios** (programs → portfolios); Portfolio Workload (cross-project capacity by person); portfolio dashboards; Goals linked to work; task-level dependencies across projects.
- Limits: gated to Advanced+; item caps per portfolio unverified; cross-project dependency visualization is portfolio-timeline-based, not a dedicated dependency workspace like Jira Plans.

### Ratings
- G2: 4.4/5, 10,000+ reviews (Q1 2026). Capterra: 4.5/5, ~13,000+ reviews.

### Sources (accessed 2026-07-20)
- asana.com/pricing (blocked; cross-checked) · get-alfred.ai/blog/asana-pricing · cirface.com/blog/asana-pricing-explained · gend.co/blog/asana-pricing · agiled.app/blog/asana-pricing (seat buckets)
- asana.com/plan/advanced · help.asana.com/s/article/portfolios-overview
- asana.com/microsoft · asana.com/apps/microsoft · help.asana.com/s/article/microsoft-teams-and-asana-integration
- learn.microsoft.com/en-us/connectors/asana · asana.com/apps/powerbi · help.asana.com/s/article/power-bi-integration
- help.asana.com/s/article/guests-faq · g2.com/products/asana/reviews · capterra.com/p/184581/Asana-PM/reviews

## Cross-cutting notes (Agent A)
- Portfolio-capable tier cost at 25 users/mo (annual): Jira Premium $363.50 < Monday Enterprise custom (~$600–750 est., unverified; Pro $475 = DIY roll-up only) < Asana Advanced $624.75.
- Only Asana meets both hard requirements (full portfolio + native Power BI/Power Automate/Teams/Outlook) on a self-serve published tier. Jira meets portfolio cheapest but weakest on M365 depth and ops ergonomics. Monday best ops fit and easiest adoption, but true portfolio locked behind custom-priced Enterprise.
- SSO caveat all three: SAML SSO extra-cost for Jira (Guard ~$4.20/user/mo), Enterprise-gated for Monday and Asana.

# Appendix B — Research pass 2: ClickUp, Wrike, Smartsheet

# Agent B findings: ClickUp, Wrike, Smartsheet
Research date: 2026-07-20. Vendor pricing pages blocked to fetcher; facts from search excerpts of official vendor pages, help-center docs, Microsoft docs/marketplace, and 2026-dated third-party trackers, cross-checked. Single-sourced/conflicting items flagged unverified.

## Tool: ClickUp

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 3 | Portfolios are dashboard "portfolio cards" rolling up Folders/Lists, plus Goals and an "Everything" Gantt spanning projects — but no true program object, no project-level dependency management; roll-ups assembled by hand. |
| m365 | 2 | Weakest here: Teams app does channel notifications, task creation from messages, link unfurling, but cannot embed ClickUp views as Teams tabs (5+ year open request); no official Power Automate or Power BI connector (third-party/API only). |
| ops_fit | 5 | Purpose-built general work management: custom statuses/fields/workflows, Form view, Docs, Whiteboards, Chat, large non-dev template library, high automation quotas — most "all-in-one" for mixed ops. |
| reporting | 4 | Unlimited Dashboards with 50+ card types on Business; strong team-level reporting but no BI-grade analytics, no official Power BI path; heavy dashboards can be slow. |
| ease | 3 | Steep initial learning curve (feature overload, deep configurability); modern UI helps once configured. |
| pricing_value | 5 | Portfolio-relevant tier (Business) $12/user/mo annual — ~half Smartsheet, ~quarter Wrike — almost all features included, no seat minimums; 25 users ≈ $300/mo. |
| admin | 3 | Generous guest seats (Business: 10 + 5/member), 2FA; but SAML/Microsoft/Okta SSO, audit logs, custom roles Enterprise-only (Business gets Google SSO; custom roles at semi-hidden Business Plus). |

### Pricing
| Plan | Annual | Monthly | Notes |
|---|---|---|---|
| Free Forever | $0 | $0 | Unlimited members/tasks; 100 MB; 100-use caps on Gantt/Timeline/Goals/custom fields; 5 Spaces |
| Unlimited | $7 | $10 | Unlimited Gantt/dashboards/custom fields/storage/integrations; 5 guests + 2/member |
| Business | $12 | $19 | Unlimited Timeline/Workload, Goal Folders, advanced automations, Google SSO, unlimited dashboards; 10 guests + 5/member |
| Business Plus | $19 | $29 | Semi-hidden tier: custom role permissions, priority support |
| Enterprise | Custom | — | SAML/Microsoft/Okta SSO, audit logs, HIPAA, ~250k automations/mo, data residency |
| AI add-ons | Brain +$9/user/mo; "Everything AI" ~+$28/user/mo | | Optional |

- Portfolio tier: effectively **Business ($12)** (unlimited Timeline, Goal Folders, Workload, unlimited dashboards). No seat minimums published.
- **25 users on Business (annual) = $300/mo (~$3,600/yr).**

### M365/Teams specifics
- Teams (native app, free): pushes ClickUp activity into a Teams channel; create tasks from Teams messages; link unfurling. Limitations: channel-only notifications (no DMs); **no pinning ClickUp views as Teams tabs** (1,000+ vote request, 5+ years open); no in-Teams task editing.
- Outlook: add-in — tasks from emails, attach emails to tasks; ClickUp can search Outlook mail (Universal Search/Brain).
- SharePoint/OneDrive: search-level integration + file attachment; no deep co-management.
- Power Automate / Power BI: **no official connector for either** (Power BI request 850+ upvotes since 2019); workarounds: API via HTTP, Zapier/Make, paid third-party (Vidi Corp connector on MS Marketplace). Biggest miss vs M365 requirement.

### Portfolio specifics
- Features: "Portfolios" (portfolio cards in Dashboards), Goals + Goal Folders, Everything view / multi-List Gantt.
- Portfolio cards roll up progress across Folders/Lists; Goals aggregate tasks with % progress; Gantt spans Lists/Folders/Spaces with task dependencies/milestones; live updates.
- Limits: no first-class program entity (portfolio is a dashboard construct); dependencies task-level only; no project-level health/RAG object OOB; no portfolio resource/financial governance.

### Ratings
- G2: 4.7/5, ~13,000 reviews (seller page 12,815; aggregators 13,151). Capterra: 4.6/5, ~6,073 (94% 4+).

### Sources (accessed 2026-07-20)
- clickup.com/pricing (blocked; excerpts) · help.clickup.com articles 6303244318999 (pricing/roles), 6305932146455 (Teams), 6312200675991 (portfolio cards), 6305027039767 (SAML), 25324213764119 (audit logs)
- clickup.com/integrations/microsoft-teams · clickup.com/features/portfolios
- feedback.clickup.com/integrations/p/microsoft-teams-integration-20 · clickup.canny.io/integrations/p/microsoft-power-automate
- marketplace.microsoft.com vidicorpltd clickup-to-power-bi-connector
- g2.com/products/clickup/reviews · capterra.com/p/158833/ClickUp/reviews
- Cross-checks: eesel.ai, upsys-consulting.com, cloudwards.net, quackback.io clickup-pricing (2026-dated)

## Tool: Wrike

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 5 | Only one of the three with native nested Space > Folder > Project hierarchy where folders act as programs/portfolios: project-level statuses/roll-ups, cross-project Gantt with 4 dependency types + critical path + baselines, portfolio dashboards core product. |
| m365 | 4.5 | Deep official Microsoft partnership: Teams app with real channel tabs (view AND edit tasks in Teams), actionable bot notifications, Outlook add-in + Actionable Messages, OneDrive/SharePoint attach, official Power BI connector bundled in Power BI Desktop. Docked: Gantt in Teams tabs view-only; no native Power Automate connector (Wrike Integrate add-on covers). |
| ops_fit | 4 | Dynamic request forms, custom item types, blueprints, approvals, proofing, custom workflows suit PMO/marketing/ops; weaker as "everything hub" (no docs/whiteboards/chat); forms require Business. |
| reporting | 4 | Real-time dashboards, progress/performance reports, resource/utilization on Business; deepest analytics (Wrike Analyze BI) at Pinnacle/add-on. |
| ease | 3 | Most common complaint: steep learning curve, dense interface for non-technical users; powerful once learned. |
| pricing_value | 3 | Portfolio depth needs Business ~$25/user/mo annual (25 users ≈ $625/mo — priciest here), seat bands of 5/10/25, annual-only, SSO not included at that tier. Genuine PPM for the money. |
| admin | 3.5 | Free Collaborator role (greater of 20 or 15% of paid seats), granular access roles/user groups; but SAML SSO only on legacy Enterprise/Pinnacle/Apex, not Business. |

### Pricing (list prices valid for purchases on/after 2026-01-21 per trackers)
| Plan | Annual | Notes |
|---|---|---|
| Free | $0 | 200 active tasks, 2 GB/account; user cap conflicting (historically unlimited; some 2026 sources say 5 — unverified) |
| Team | $10 | Cap 2–15 users (some say 2–25 — unverified); Gantt, shareable dashboards, 50 automation actions/user/mo, AI Essentials; no request forms |
| Business | ~$25 (legacy list $24.80) | 5–200 users; custom fields/workflows, request forms, blueprints, reports, time tracking, resource & capacity mgmt, 200 actions/user/mo, 5 GB/user |
| Pinnacle | Custom (~$40–55/user/mo unverified) | SSO, locked Spaces, Wrike Analyze, budgeting/job roles |
| Apex | Custom (~$60–80 unverified) | New top tier bundling Integrate, Sync, Whiteboard, Datahub |

- Standalone "Enterprise" plan end-of-sale for new customers (grandfathered); new enterprise buyers → Pinnacle/Apex.
- Seat bands: ≤30 in groups of 5; 30–100 groups of 10; >100 groups of 25. Business 5-seat minimum.
- Portfolio tier: **Business (~$25)**; advanced portfolio analytics/budgeting → Pinnacle.
- **25 users on Business (annual) = $625/mo (~$7,500/yr).**

### M365/Teams specifics
- Teams (native app, all account types): add folders/projects/Spaces as **channel tabs** — create/edit tasks, comment, share, view List or Gantt inside Teams; bot DMs on assignment/@mentions; **Actionable Notifications** (change status, due dates, reply within Teams); create tasks from conversations. Limitations: Gantt tab view-only; bot install per user.
- Outlook: add-in (tasks from emails, view/update from inbox); **Outlook Actionable Messages** (change due dates/status/comment inside Wrike email notifications, instant sync).
- SharePoint/OneDrive: attach SharePoint files/folders and OneDrive files.
- Power BI: **official Wrike Connector bundled with Power BI Desktop** (since May 2023).
- Power Automate: no native certified connector; via Wrike Integrate add-on (Workato) or third parties.
- Also: MS Project/Excel import-export; Entra ID SAML SSO + SCIM (Pinnacle/Apex/legacy Enterprise only).

### Portfolio specifics
- No single "Portfolio" object; the layer is native hierarchy **Spaces > Folders (programs/portfolios) > Projects > Tasks** + portfolio dashboards + cross-project Gantt + (top tier) Wrike Analyze.
- Open any folder/Space in Gantt to see all child projects on one timeline; 4 dependency types incl. **cross-project dependencies**, critical path, baselines, drag-and-drop rescheduling with auto-adjustment; project-level custom fields/status roll-ups; real-time portfolio dashboards; resource/capacity planning (Business+).
- Limits: portfolio budgeting/financials + Analyze need Pinnacle; modest automation quotas; Free/Team can't do meaningful portfolio work.

### Ratings
- G2: 4.2/5, ~4,520 reviews. Capterra: 4.4/5, ~2,963. (Aggregator verified 2026-05-02.)

### Sources (accessed 2026-07-20)
- wrike.com/price + comparison-table (blocked; excerpts) · help.wrike.com articles: 209605669 (plan types), 4402261856151 (Business), 27537116133143 (Team vs Business), 4402266276247 (Pinnacle), 115001825869 (Teams), 4408759036439 (actionable notifications), 115004562709 (bot), 14918588863895 (Power BI), 360046791773 (SAML/Entra), 209603989 (licenses/Collaborator), 209604229 (Gantt dependencies)
- wrike.com/partners/microsoft · wrike.com/apps/email-integration/wrike-for-outlook · wrike.com/use-cases/project-portfolio-management
- g2.com/products/wrike/reviews · capterra.com/p/76113/Wrike/reviews
- Cross-checks: thedigitalprojectmanager.com wrike-pricing, checkthat.ai, costbench.com, agiled.app (2026-dated)

## Tool: Smartsheet

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 4 | Reports aggregate rows/sheet-summaries from up to 30,000 sheets, render as cross-project Gantt on dashboards; Control Center arguably strongest program-governance engine in this set (blueprints, automated provisioning, program roll-ups) — but Control Center is custom-priced add-on; cross-sheet dependencies only via manual cell-linking. |
| m365 | 5 | Deepest M365 stack of the three: official Microsoft-published Power Automate connector, official Power BI connector, Teams app with tabs for sheets/reports/dashboards + bot DMs + in-Teams approvals, Outlook add-in creating/editing rows from email; formal Microsoft partner. |
| ops_fit | 4 | Spreadsheet-native paradigm fits ops; forms, robust automations, proofing, document builder, unlimited free collaborators; weaker native docs/chat/whiteboards; advanced setups need formula/cell-link skills. |
| reporting | 4 | Row + sheet-summary reports roll portfolios into live dashboards with charts/Gantt widgets; official Power BI connector for BI-grade analysis; widget variety less than ClickUp. |
| ease | 4 | Gentlest adoption for Excel-fluent ops staff — familiar grid, clean UI, easy dashboards; complexity rises with cross-sheet formulas, cell links, Control Center. |
| pricing_value | 3.5 | Business $19/user/mo annual with unlimited free Guests/Contributors good value (~$475/mo for 25), but true program layer (Control Center) and SSO/governance behind custom quotes — full-requirement TCO opaque. |
| admin | 4 | Unlimited free Guest/Contributor seats with external-collaborator governance and strong Admin Center; SAML SSO, domain validation Enterprise-only; provisional-member auto-convert-to-paid mechanic needs admin oversight. |

### Pricing (new "user subscription model" since June 2024)
| Plan | Annual | Monthly | Notes |
|---|---|---|---|
| Free plan | Not offered to new customers per 2026 trackers (30-day trial); legacy status unverified | — | Unlimited free Guests/Contributors on paid plans instead |
| Pro | $9 | $12 | **1–10 members max**; unlimited sheets, Gantt/board/calendar; external collaborators view-only; limited automations |
| Business | $19 | $24 | **3-member minimum**, no cap; unlimited automations, conditional-logic forms, workload tracking, unlimited **free guest editors**, 1 TB, Teams/Slack connectors |
| Enterprise | Custom (~$30–50/user/mo unverified) | — | SAML SSO, domain control, audit logs, governance, AI, WorkApps |
| AWM (Advanced Work Management) | Custom | — | Enterprise + Control Center, Dynamic View, Data Shuttle, DataMesh, Bridge, premium connectors, Calendar & Pivot apps |
| Control Center (standalone add-on) | Custom quote; on Business & Enterprise | — | Program/portfolio engine; typically sold with onboarding/consulting |

- Seat model: only Members pay; Guests (edit/comment) and Contributors (view) free/unlimited; Provisional Members 90 days free then auto-convert to paid unless downgraded — budget watch-item.
- Portfolio tier: **Business ($19)** gives report/dashboard-based portfolio layer; **full program layer (Control Center) = custom add-on or AWM tier**.
- **25 members on Business (annual) = $475/mo (~$5,700/yr)** + custom Control Center if needed.

### M365/Teams specifics
- Teams (native app): sheets, reports, **dashboards as channel tabs** (all plans); bot delivers personal notifications, reminders, **approval/update requests as DMs** — approvals actionable in Teams; bot lookups; workflow alerts to channels. Limitations: automated notifications/approvals/bot need **Business+**; approvals DM-only; app is "read-notify-approve" (no free-form row edit in Teams; edits via update-request forms); channel notifications need admin consent; EU-region reduced capability.
- Outlook: add-in — create/edit rows from inbox, attach emails to rows.
- SharePoint/OneDrive: attach OneDrive files; O365 sign-in; Data Shuttle (AWM/add-on) automates file data flows. Live SharePoint web-part embed: unverified.
- Power Automate: **official Smartsheet connector published by Microsoft** (learn.microsoft.com/connectors/smartsheet) — only tool of the three with this.
- Power BI: **official Smartsheet connector** in Power BI Desktop/service.

### Portfolio specifics
- Features: **Reports** (row + sheet-summary), **Dashboards**, and true program layer **Control Center** (+ Portfolio WorkApps, Resource Management add-ons).
- Row reports consolidate tasks from ≤**30,000 source sheets**, viewable as Gantt/calendar, pinned to dashboards; sheet-summary reports aggregate project metadata (status, health, % complete) into portfolio grid/chart; Control Center adds blueprint-based project creation, automated provisioning/archiving, program roll-up reporting, change management at scale.
- Limits: **dependencies work within a single sheet only** (cross-sheet = manual cell links); portfolio without Control Center is reporting-based; Control Center custom-priced with consulting; portfolio resource mgmt separate add-on.

### Ratings
- G2: 4.4/5 (seller page 23,245 across products; product-specific count unverifiable). Capterra: 4.5/5, ~3,453.

### Sources (accessed 2026-07-20)
- smartsheet.com/pricing (blocked; excerpts) · help.smartsheet.com articles: 2483245 (user subscription model), 520100 (user types), 522214 (reports/30k sheets), sheet-summary-reports, 2476201 + 2479071 + 2482425 (Teams), 2476661 (SAML), 2483128 (external governance)
- smartsheet.com/marketplace/premium-apps/control-center · datasheet-control-center · platform/portfolio-management · datasheet-microsoft-365-together · marketplace/apps/microsoft-flow
- learn.microsoft.com/en-us/connectors/smartsheet · learn.microsoft.com/en-us/power-bi/connect-data/service-connect-to-smartsheet
- g2.com/products/smartsheet/reviews · capterra.com/p/79104/Smartsheet/reviews
- Cross-checks: spendhound.com, tech.co, zylo.com, workmanagementhub.com (2025–2026-dated)

## Cross-tool summary (Agent B)
| Criterion | ClickUp | Wrike | Smartsheet |
|---|---|---|---|
| portfolio | 3 | 5 | 4 |
| m365 | 2 | 4.5 | 5 |
| ops_fit | 5 | 4 | 4 |
| reporting | 4 | 4 | 4 |
| ease | 3 | 3 | 4 |
| pricing_value | 5 | 3 | 3.5 |
| admin | 3 | 3.5 | 4 |
| 25-seat cost, portfolio tier (annual) | $300/mo (Business) | $625/mo (Business) | $475/mo (Business) + custom Control Center |

Unverified flags: Wrike Team cap (15 vs 25), Wrike free-plan user count, Pinnacle/Apex and Smartsheet Enterprise/AWM/Control Center pricing (custom), Smartsheet legacy free plan, Smartsheet product-specific G2 count, ClickUp Business automation quota (5,000–10,000/mo cited).

# Appendix C — Research pass 3: MS Planner/Project, Airtable, Zoho Projects + cross-tool benchmarks

# Agent C findings: Microsoft Planner/Project, Airtable, Zoho Projects + cross-tool benchmarks
Research date: 2026-07-20. Several official pages 403-blocked via proxy; figures captured via search-index snippets of official pages, cross-checked against 2+ secondary sources. Unconfirmed items marked unverified.

## Tool: Microsoft Planner / Microsoft Project (the new unified Planner)

### Naming/plan structure (clarified)
- "Microsoft Planner" is now the single brand for To Do + classic Planner + Project for the web, delivered as the Planner app in Teams (GA 2024) and new Planner for web (planner.cloud.microsoft).
- **Project for the web retired August 2025** (with Project/Roadmap Teams apps); former projects are now "premium plans" in Planner. **Project Online retires 2026-09-30.** Project desktop continues separately.
- License renames: Project Plan 1 → **Planner Plan 1** (Apr 2024); Project Plan 3/5 → **Planner and Project Plan 3/5** (Sep 2024). Basic "Planner in Microsoft 365" included with M365 at no extra cost.

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 4 | Native **Portfolios** (Plan 3/5) rolls multiple premium plans into one view with cross-plan **Roadmap** timeline; Plan 5 adds portfolio optimization/demand management. Docked: premium plans only; **no cross-plan dependencies**. |
| m365 | 5 | It IS M365: first-class Teams app, Entra ID, M365 Groups/SharePoint, native Power Automate connector, Power BI via Dataverse. Unmatched depth. |
| ops_fit | 3 | Good templates, custom fields, goals; fine for general task/project work. But no in-app forms (needs MS Forms), no in-app automation rules (needs Power Automate), docs elsewhere (SharePoint/Loop). |
| reporting | 3 | Per-plan charts/People/Goals views decent; **no built-in portfolio-level dashboarding** — portfolio reporting requires Power BI against Dataverse (IT work). |
| ease | 4 | Familiar Microsoft UI in Teams; basic Planner near zero-training. Premium features and license/naming maze cause confusion. |
| pricing_value | 3 | Portfolio capability costs $30/user/mo (Plan 3) — 3× Zoho Enterprise. Mitigated by mixed licensing (only portfolio creators need Plan 3) and $0 basic Planner. |
| admin | 4 | Entra ID SSO, M365 governance, DLP/compliance inherited. Guest access OK for basic plans; premium-plan guest sharing clunky (whole-Team add; no individual plan sharing). |

### Pricing (annual, per user/month)
| Tier | Price | Notes |
|---|---|---|
| Planner in Microsoft 365 (basic) | $0 (in M365) | Board/Grid/Schedule/Chart; no Gantt, no dependencies |
| Planner Plan 1 | $10 | Premium plans: Timeline (Gantt), dependencies, sub-tasks, custom fields, milestones, Goals/People views. **Cannot create portfolios** (read-only of shared) |
| **Planner and Project Plan 3** | $30 | **← unlocks Portfolios** (create/edit), baselines, critical path, lead/lag dependencies, Assignments view, Project desktop, Project Online |
| Planner and Project Plan 5 | $55 | Portfolio selection/optimization, demand management, enterprise resource capacity |
| M365 Copilot add-on | $30 | Optional AI |

- De facto free tier = basic Planner in M365. No minimum seats; mix-and-match per user.
- Editing premium plans needs Plan 1+; unlicensed users read-only; guests can edit without license (per MS Q&A).
- **25 users all-Plan 3 = $750/mo ($9,000/yr). Realistic mixed: 5× Plan 3 + 20× Plan 1 = $350/mo.**

### M365/Teams specifics
- Teams: native pinnable app; plans as channel tabs; activity-feed notifications.
- Outlook: To Do/flagged-email in "My Day"; plan schedules → Outlook calendar (iCal, basic plans); group notifications via Outlook groups.
- SharePoint/OneDrive: each basic plan backed by M365 Group + SharePoint site; Planner web part embeds plans in SharePoint pages; attachments in SharePoint/OneDrive.
- Power Automate: native Planner connector — **basic plans only**; premium-plan automation via **Dataverse connector**.
- Power BI: premium plans in Dataverse → Dataverse connector (msdyn_project/msdyn_projecttask); legacy Planner connector sees basic plans only. No turnkey portfolio report pack.
- Limitations: split brain basic vs premium (different storage, APIs, licensing); portfolio reporting DIY; Project desktop separate.

### Portfolio specifics
- Name: **Portfolios** (rolled out through 2025; replaces retired Roadmap app).
- Groups related **premium** plans; consolidated task list + progress; **Roadmap view** (each plan a row on shared timeline); status tracking; shareable (Plan 1/basic read-only).
- Limits: premium plans only; **no cross-plan task dependencies** (MS Q&A confirms); no cross-plan resource view; no ADO/Project Online projects; no built-in portfolio dashboards (Power BI required); max plans/portfolio unpublished (unverified).

### Ratings
- G2 (MS Planner): 4.2/5 (count unverified). Capterra: 4.3/5, ~276–293 reviews (ease 4.5, value 4.5).

### Sources (accessed 2026-07-20)
- microsoft.com/en-us/microsoft-365/planner/microsoft-planner-plans-and-pricing (fetched directly)
- techcommunity.microsoft.com plannerblog: portfolios feature (4342145), transitioning/retiring Project for the web (4410149)
- schneider.im microsoft-project-plan-3-and-5-name-changes · wellingtone.com microsoft-planner-premium-licensing-plans-pricing-2026
- support.microsoft.com planner manage-multiple-plans-with-portfolios · learn.microsoft.com/answers 5428468 (cross-plan deps) · learn.microsoft.com/planner/licensing
- capterra.com/p/227201/Microsoft-Planner · g2.com/products/microsoft-planner/reviews

## Tool: Airtable

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 3 | No named portfolio feature — you build one: Projects↔Tasks linked tables, rollups, Gantt with drag-and-drop dependencies + auto-cascade, Interface Designer dashboards. Powerful but DIY; hard across multiple bases. |
| m365 | 2 | Weakest of the three: **no native Teams tab app** (embed via generic Website tab); Teams/Outlook = automation actions; Power Automate connector (Independent Publisher) **[DEPRECATED]** per MS Learn; no native Power BI connector. |
| ops_fit | 5 | Best-in-class mixed ops: custom tables/fields, forms, automations (incl. AI), interfaces/portals, hundreds of templates — a flexible ops platform. |
| reporting | 4 | Interface Designer dashboards strong and end-user buildable; no cross-workspace BI layer; heavy reporting → external BI over API. |
| ease | 3 | Intuitive UI, but someone must architect the portfolio system (schema, links, rollups, interfaces) — real adoption effort. |
| pricing_value | 3 | Team $20 fair; governance/SSO at Business **$45/user** — priciest realistic option at 25–50 seats. |
| admin | 4 | Business: SAML SSO + admin panel; Enterprise Scale: SCIM, audit logs, DLP, EKM, HyperDB, 99.9% SLA. Read-only collaborators/form submitters free. |

### Pricing (annual, per seat/month)
| Tier | Annual | Monthly | Limits/features |
|---|---|---|---|
| Free | $0 | — | 5 editors, 1,000 records/base, 100 runs/mo, 1 GB; **no Gantt/Timeline** |
| **Team** | $20 | $24 | 50k records/base, 25k runs/mo, 20 GB; **Gantt + Timeline, dependencies** — minimum tier for DIY portfolio |
| Business | $45 | $54 | 125k records, 100k runs, 100 GB; SAML SSO, admin panel, two-way sync, premium sync |
| Enterprise Scale | Custom | — | 500k+ records, 500k runs, 1 TB; SCIM, audit, DLP, EKM, Enterprise Hub |

- Every editor on any base = paid seat; read-only/form fillers free. No published minimum.
- Portfolio tier: **Team** technically; **Business** realistic for M365 org (SSO + governance).
- **25 users: Team $500/mo ($6,000/yr); Business $1,125/mo ($13,500/yr).**

### M365/Teams specifics
- Teams: no first-party Teams tab app in AppSource (MS Q&A confirms; workaround = Website tab embed). Official = OAuth **"Send Microsoft Teams message" automation action** (needs Entra admin consent). Cloud Exchange only.
- Outlook: automation actions (send email, create/update Calendar events); **Airtable Sync: Outlook Calendar** (one-way; sync gated to paid; multi-source Business+; no on-prem Exchange).
- SharePoint/OneDrive: OneDrive file picker for attachments only; **no SharePoint integration**.
- Power Automate/Power BI: connector deprecated per MS Learn; via REST API/HTTP, Zapier/Make/n8n. No native Power BI connector.
- Net: integration is automation-plumbing, not embedded experience — biggest gap vs requirement #2.

### Portfolio specifics
- No named feature; pattern: Projects↔Tasks linked tables, rollup/formula fields, **Gantt view** (milestones, dependencies with auto-cascade, critical path), Timeline view, **Interface Designer** portfolio dashboards. Marketed for enterprise portfolio use.
- Limits: best in single base (cross-base needs synced tables, Business+); record caps bound very large portfolios; no program hierarchy, goals module, or resource-capacity engine — all hand-modeled.

### Ratings
- G2: 4.6/5, ~3,276 reviews. Capterra: 4.6/5, ~2,232 reviews.

### Sources (accessed 2026-07-20)
- airtable.com/pricing + support.airtable.com/docs/airtable-plans (403; snippets, cross-checked tinycommand.com, saasworthy.com, thedigitalprojectmanager.com)
- support.airtable.com/docs: gantt-view-milestones-dependencies-and-critical-paths, send-ms-teams-message-action, ms-teams-admin-approval, outlook-automation-actions, airtable-sync-integration-outlook-calendar
- learn.microsoft.com/en-us/connectors/airtable (deprecated) · learn.microsoft.com/answers/4394662 (no Teams app)
- airtable.com/solutions/project-management · g2.com/products/airtable/reviews · capterra.com/p/146652/Airtable

## Tool: Zoho Projects

### Scores
| Criterion | Score | Justification |
|---|---|---|
| portfolio | 4 | Enterprise tier: **Portfolio Dashboard**, **Global Gantt** across projects, and real **inter-project task dependencies** (unique among these three), plus critical path/baselines and project grouping. Lacks demand management/portfolio optimization. |
| m365 | 3 | Best third-party M365 story here: real **Teams tab app** (full web app incl. Gantt tab) + notification bot, Outlook add-in with action cards, calendar sync, M365 user import, Entra SAML SSO, even a **Microsoft 365 Copilot app**. But no native Power Automate or Power BI connectors (Zoho Flow/Analytics instead), no SharePoint docs. |
| ops_fit | 4 | Blueprints (no-code workflow automation + approvals), custom fields/layouts/statuses, issue tracking, timesheets, budgets, task rollup — solid structured ops; less freeform than Airtable; templates skew classic-PM. |
| reporting | 4 | Portfolio dashboards, resource utilization, planned-vs-actual, timesheet/budget reports; deeper BI via Zoho Analytics (separate product). Not Power BI-native. |
| ease | 3 | Capability-for-price praised, but busy UI, 2–3 week onboarding; mobile weaker for Gantt/reporting. |
| pricing_value | 5 | Outlier: full portfolio at **$9/user/mo** — ~1/3 of Planner Plan 3, ~1/5 of Airtable Business. Free plan for pilots. |
| admin | 3 | SAML SSO with Entra (Zoho org accounts), roles/profiles, client-user model; read-only users capped (~10 Premium/Enterprise, 100 Ultimate per sources); governance lighter than Microsoft/Airtable Enterprise. |

### Pricing (annual, per user/month)
| Tier | Annual | Monthly | Features/limits |
|---|---|---|---|
| Free | $0 | — | Up to 5 users, 3 projects, 5 GB (older 3-user/2-project limits in some sources; Zoho page says free for 5) |
| Premium | $4 | $5 | Up to 50 users; unlimited projects, Gantt, Blueprint automation, resource utilization, budgeting, 100 GB |
| **Enterprise** | $9 | $10 | Unlimited users; **← unlocks portfolio**: Portfolio Dashboard, Global Gantt, **inter-project dependencies**, critical path, baselines, custom fields/roles/profiles, 120 GB |
| Ultimate | $14 | $15 | +15 GB/user (min 150 GB/org), ~100 read-only users, custom modules, higher limits |

- No published seat minimums. Also included in Zoho One (~$37–45/user/mo bundle).
- **25 users on Enterprise = $225/mo ($2,700/yr).**

### M365/Teams specifics
- Teams: official app (AppSource) — **Projects tab** in channels/chats with near-full web app: task lists/details + dedicated **Gantt** and **Issues** tabs; **bot** notifications (tasks/milestones/issues/forums).
- Outlook/M365: add-in — create/manage tasks from mailbox via action cards; sync tasks/events to Outlook Calendar; import M365 users; Microsoft sign-in; Entra SAML SSO (Zoho suite).
- Copilot: **Zoho Projects app for Microsoft 365 Copilot** — NL search/summarize/act on tasks/milestones/reports in M365.
- OneDrive/SharePoint: attach from OneDrive (+ Google Drive/Dropbox/Box/WorkDrive); **no SharePoint doc-library integration**.
- Power Automate/Power BI: **no certified Zoho Projects connector** (Zoho ships Sign/Mail/Calendar only; automation via Zoho Flow or webhooks/API); BI via Zoho Analytics or API.

### Portfolio specifics
- Names: **Portfolio Dashboard** + **Global Gantt Chart** + **Inter-project Dependencies** (Enterprise); **project groups** (program-style).
- Cross-project health/status dashboards; one Gantt spanning all projects with dependencies linking tasks **across projects** (neither Planner nor Airtable does natively); critical path/baseline per project; cross-project resource utilization.
- Limits: no demand-management/portfolio-optimization (scoring, scenario modeling); dashboards less freeform than Airtable Interfaces; deep BI needs Zoho Analytics.

### Ratings
- G2: 4.3/5 (~300–470 reviews; unverified exact). Capterra: 4.5/5 (~400–830; unverified exact). PCMag **Editors' Choice** for PM (per snippets).

### Sources (accessed 2026-07-20)
- zoho.com/projects/zohoprojects-pricing.html + pricing-comparison.html (403; snippets, cross-checked tech.co, costbench.com, comparedge.com)
- zoho.com/projects/integrations/microsoft-teams.html · help.zoho.com Teams + microsoft-365 articles · marketplace.zoho.com ms-outlook-for-zoho-projects · appsource.microsoft.com zohocorp.zohoprojects
- help.zoho.com accounts SAML access-zoho-via-entra-id
- g2.com/products/zoho-projects/pricing · capterra.com/p/169455/Zoho-Projects

## Cross-tool benchmarks (2025–2026 signals; accessed 2026-07-20; gaps unverified)

**Gartner MQ — Adaptive Project Management & Reporting (2024/2025):**
- monday.com — Leader 4 consecutive years (2022–2025); 2025: furthest Completeness of Vision, highest Ability to Execute (monday IR).
- Asana — Leader (2024 MQ). Atlassian (Jira) — Leader in 2025 per roundup (bdq.cloud). Smartsheet — evaluated; Leader plausible but unverified. Wrike/ClickUp/Airtable positions unverified.

**Forbes Advisor "10 Best PM Software" (Feb 2026; 26 platforms, 61 metrics):**
- monday.com 4.3 stars "best for new teams"; ClickUp called best for most SMBs (affordable); Coda 4.1, Notion 3.8. Airtable/Zoho/Planner placements unverified.

**PCMag (latest per snippets):**
- Zoho Projects — Editors' Choice ("intuitive design and available customizations"); Asana — Editors' Choice and Best of the Year 2020 + 2025; Airtable and monday.com appear in picks. Others unverified.

**TechRadar (current):**
- monday.com at/near top; Asana (tracking/deadlines) and ClickUp ("one app to replace them all") headline picks. Exact rankings others unverified.

**G2 2026 + Grid:**
- Top by G2 data: Jira, Asana, ClickUp, monday.com. Labels: Asana = task mgmt & collaboration; monday = customizable workflows; ClickUp = all-in-one; **Smartsheet = large-scale project & portfolio planning**; Wrike top set (marketing/prof-services). Airtable 4.6 G2 avg; Planner 4.2; Zoho 4.3.
- SelectSoftwareReviews 2026 scoring: Smartsheet 96 (project tracking) vs Airtable 90, Zoho Projects 87–89 (top customization/value; cheapest top-10 at $4/user), Asana 58, Teamwork 56.

**One-liners:** Jira = G2 volume leader, dev-centric outlier for general-ops buyer; monday.com = most-awarded overall; Asana = perennial Leader/Editors' Choice for collaboration-heavy teams; ClickUp = value/feature-density SMB pick; Wrike = structured creative/prof-services workflows; Smartsheet = analyst pick for large-scale portfolio planning; Planner = never tops general lists but default M365-native choice; Airtable = highest user satisfaction as flexible ops database, not turnkey PPM; Zoho Projects = value/Editors'-Choice budget pick with real portfolio features.

## Bottom-line fit (Agent C tools, 25 seats)
| | Portfolio layer | M365 depth | Portfolio-tier cost/mo (25 users) |
|---|---|---|---|
| Planner/Project Plan 3 | Native but premium-plans-only, no cross-plan deps | Unmatched (native) | $750 (or ~$350 mixed Plan 3/Plan 1) |
| Airtable (Team/Business) | DIY build, no named feature | Weakest (no Teams app; deprecated PA connector) | $500 Team / $1,125 Business |
| Zoho Projects Enterprise | Real portfolio dashboard + global Gantt + cross-project deps | Good third-party (Teams tab, Outlook add-in, Copilot app; no PA/Power BI) | $225 |
