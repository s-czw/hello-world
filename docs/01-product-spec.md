# Product Specification — Cairn

**Document:** 01-product-spec.md
**Author:** Product Engineer
**Date:** 20 July 2026
**Status:** v1.1 — PMO review (PD-1…PD-42) applied; ready for sign-off. Architecture and design v2 follow this doc.
**Audience:** Tech Lead (architecture spec follows this doc), Designer (UI/UX spec follows this doc), Project Director (delivery plan follows this doc)

---

## 1. Vision & product principles

### 1.1 Why we are building this

We evaluated 9 commercial PM tools (see `pm-tool-benchmark/research-notes.md`). Asana Advanced won the benchmark (weighted 4.40/5) because it is the only tool that ships a complete portfolio layer — unlimited portfolios with status/timeline roll-ups, portfolio Gantt, nested portfolios — on a self-serve tier, plus the deepest M365 integration. But it costs **$24.99/user/mo ($625/mo at 25 seats, ~$7.5k/yr)**, portfolio features are gated to that tier, SAML SSO is gated further to Enterprise, and our data lives on someone else's cloud.

Every tool in the benchmark had the same shape of problem: **the portfolio/program layer is the upsell**. Jira gates Plans behind Premium; Monday gates its Portfolio solution behind custom-priced Enterprise; Smartsheet's Control Center is a custom-priced consulting engagement; MS Planner needs $30/user Plan 3 licenses just to *create* a portfolio. The one feature this team declared a hard requirement is, industry-wide, the feature vendors monetize hardest.

So we build our own: an Asana-inspired work management tool where **the portfolio layer is a core primitive, not a premium tier**, running on a VM we control, with our data in a Postgres database we can query, back up, and export at will.

### 1.2 What "Asana-inspired but ours" means

We copy Asana's **semantics**, not its surface area:

- **Copy:** the layered mental model (task → section → project → portfolio), the crispness of "every task has one assignee and one due date," list + board as two views of the same data, project status updates (on track / at risk / off track) that roll up into portfolios, the portfolio timeline where each project is a bar.
- **Do not copy:** the other ~90% of Asana — goals, workload, forms, rules, proofing, approvals, AI. Those are post-MVP or never. Asana took 15 years; we have 6 weeks. We win by shipping the 20% our ops team actually uses, un-gated.
- **Do differently:** see §6 — no seat-gating, data ownership, no per-seat pricing pressure, M365-native roadmap (we integrate with the Microsoft stack we already pay for rather than treating it as an enterprise upsell).

### 1.3 Product principles

1. **The portfolio is not a paywall.** Every capability ships to every user. Program roll-up visibility is the reason this product exists; it is available to a brand-new member on day one.
2. **One task, one assignee, one due date.** We keep Asana's opinionated task model. Ambiguity about who owns a thing is the #1 failure mode of ops teams; the tool refuses to create it. (Collaborators can follow a task; only one person is *responsible*.)
3. **Status is a habit, not a report.** A project status update takes < 60 seconds to post and is the single source of truth that rolls up to the portfolio. The tool optimizes for the weekly cadence: prompt, prefill, one-click color.
4. **Boring, fast, obvious.** General-ops users, not power users. Every screen answerable in one glance; no configuration required before first use; list view keyboard-friendly; page loads < 1s on our own VM.
5. **Own the data, own the runtime.** Everything runs in Docker Compose on our VM. No feature may depend on a third-party SaaS to function. Exportable data (JSON/CSV) is a feature, not an afterthought.

### 1.4 Working name

Three candidates:

| Codename | Rationale |
|---|---|
| **Cairn** | A cairn is a stack of stones that marks the route — individual stones (tasks/projects) stacked into a visible marker (portfolio) that keeps everyone on the same path. Short, ownable, evokes the roll-up. |
| **Loom** | Weaves many threads (tasks, projects) into one visible fabric (the program view). Nice metaphor, but collides with the well-known Loom video product. |
| **Waypoint** | Navigation metaphor for milestones and program tracking. Clear but generic; weak as a distinctive internal identity. |

**Decision: "Cairn" — RATIFIED by the Project Director (PD-42, 20 Jul 2026).** No longer provisional; used everywhere including the repo. Repo/monorepo name: `cairn`.

---

## 2. Users & roles

Target org: 10–50 people, mixed/general operations (not software development), Microsoft 365 shop. Roles are **org-level** in MVP (no per-project permission matrix — see Out of MVP).

### 2.1 Role model (MVP)

| Role | Who | Can do (MVP) |
|---|---|---|
| **Admin** | IT owner / ops director (1–3 people) | Everything below + invite/deactivate users, assign roles, rename org, delete any object |
| **Member** | Everyone else | Create/edit projects, portfolios, teams, tasks; comment; upload attachments; complete anything they can see |

That's it — **two roles in MVP**. "Program lead" and "project lead" are *personas*, not permission levels; in a ≤50-person internal tool, restricting who can create a portfolio adds friction without adding safety. **To be unambiguous (PD-26 guard): "lead" and "owner" confer no permissions anywhere in the system — any member can edit any project, post any status update, and manage any portfolio; there is no per-team or per-project role column with authz meaning.** Per-project permissions, guest role, and read-only viewers are post-MVP (§5).

### 2.2 Personas and their day-1 needs

**P1 — Amira, Operations Director (Admin persona).**
Owns the tool decision and the VM budget. Day 1 needs: create the org, invite the team by email, see that people actually signed up, and open one portfolio that shows every active workstream with a red/yellow/green status without asking anyone anything.
*Success = she stops maintaining the weekly status PowerPoint.*

**P2 — Daniel, Program Lead (Member persona, heavy portfolio user).**
Runs 2 programs of 4–6 projects each (e.g. "Office relocation" spanning fit-out, IT, comms, HR). Day 1 needs: create a portfolio, pull existing projects into it, see them as bars on a timeline, and read each project's latest status update from one screen. Needs to nudge project leads whose status is stale.
*Success = his Monday program review runs off the portfolio screen.*

**P3 — Sofia, Project Lead (Member persona, heavy project user).**
Runs individual projects (an event, a procurement cycle, an onboarding batch). Day 1 needs: create a project from scratch in < 2 minutes, structure it with sections, assign tasks with due dates, drag cards across a board in a stand-up, and post a weekly status update in under a minute.
*Success = she posts a status weekly without being chased.*

**P4 — Omar, Team Member (Member persona, task consumer).**
Contributes to 3–5 projects; lives in "what do I owe and when." Day 1 needs: a **My Tasks** view of everything assigned to him sorted by due date, the ability to complete/comment/attach from the task, and an in-app notification when he's assigned something or @mentioned.
*Success = nothing assigned to him is discovered late.*

---

## 3. Domain model (product language)

The containment hierarchy, top to bottom:

```
Organization
 └── Team (grouping of people + projects; e.g. "Facilities", "HR Ops")
      └── Project (a bounded piece of work with a start/end)
           └── Section (named ordered grouping inside a project; = board column)
                └── Task (the atomic unit of work)
                     └── Subtask (child task; one level deep in MVP)

Portfolio ──groups──> Projects (cross-team; a project can be in many portfolios)
```

### 3.1 Objects, defined

| Object | Definition & key fields (MVP) |
|---|---|
| **Organization** | Single tenant per deployment. Name, logo (later). One org per install in MVP — no multi-tenancy. |
| **User** | Email, name, password hash, role (admin/member), active flag. Avatar = initials in MVP. |
| **Team** | Named group with members; owns projects. Fields: name, description, members. Every project belongs to exactly one team. A default "General" team is created with the org so day-1 users never face an empty prerequisite. |
| **Project** | Fields: name, description, team, **owner** (a user), **status** (see Status update), **start date, end date** (drive the portfolio timeline bar), color, archived flag, default view (list/board). Members of the org can see all projects in MVP (no private projects). |
| **Section** | Ordered, named grouping of tasks within a project. Renders as headed groups in list view and as **columns in board view** — one structure, two projections (Asana semantic). Every project gets a default "To do / In progress / Done"-style set on creation (template), fully editable. |
| **Task** | Fields: title, description (**plain text + auto-linked URLs** — rich text is Phase 1, per PD-8), project, section, **one assignee** (nullable), **due date** (nullable, date only — no times in MVP), completed flag + completed_at, order within section, created_by, timestamps. |
| **Subtask** | A task whose parent is a task. Same fields (assignee, due date, complete). **One level deep in MVP** (no sub-subtasks). Subtasks live under a parent, don't sit in sections, and show a progress count (3/5) on the parent. |
| **Comment** | Authored text on a task (**plain text + auto-linked URLs**, PD-8), chronological, with **@mention** (triggers notification). Edit/delete own comments. No threads in MVP. |
| **Attachment** | File uploaded to a task (or a comment). Stored on local volume (Tech Lead: pluggable storage interface, S3-compatible later). Max 25 MB/file in MVP. Images preview inline; everything else downloads. |
| **Portfolio** | Named collection of projects with an owner. Fields: name, description, owner, ordered list of member projects. A project can belong to multiple portfolios. **Not nested in MVP** (nesting is post-MVP; benchmark: Asana's nested portfolios are the program layer we defer). |
| **Status update** | Posted on a **project** (and, per D5, on a portfolio): color (**on track / at risk / off track / on hold** — this four-value enum is the ratified vocabulary across all docs, PD-17; there is no "Blocked"), title, body (**plain text + auto-linked URLs**, PD-8), author, timestamp. Latest update = the project's current status; full history retained. This is the atom that rolls up to portfolios. |
| **Notification** | In-app only. Types in §4.6. |

### 3.2 Fields: built-in vs custom

- **MVP = built-in fields only**: the fields listed above, plus task **priority (none/low/medium/high)** as a single fixed enum field, because boards and lists want one triage lever.
- **Custom fields (user-defined: text/number/select per project or org) are post-MVP Phase 1**, alongside dashboards/reporting — custom fields are only worth their complexity when reporting can pivot on them. Tech Lead note: model priority as if it were a custom field (field-registry pattern) so Phase 1 doesn't require a migration rewrite.

### 3.3 Deliberate model simplifications vs Asana (MVP)

- No multi-homing of tasks (a task lives in exactly one project). Asana's "task in many projects" is powerful but multiplies edge cases; revisit Phase 3.
- No task dependencies in MVP (portfolio timeline shows project bars, not a cross-project dependency Gantt — that's the single biggest scope cut; see §5).
- No milestones as a distinct type; use a task with priority-high + due date.
- No private projects/portfolios; everything org-visible. Privacy = Phase 4 with guest access.

---

## 4. MVP feature spec (6 weeks)

Conventions: stories are `As a <persona>, I want <thing>, so that <why>`. Acceptance criteria (AC) are testable. Anything not stated is not in MVP.

### 4.0 Cross-cutting acceptance criteria (apply to every feature)

- AC0.1: All pages usable at 1280px+ desktop; layout does not break at 1024px. (Mobile-responsive is best-effort, not a gate.)
- AC0.2: All mutations reflect in the UI without a manual page refresh (optimistic update or refetch).
- AC0.3: All destructive actions (delete project/portfolio/task with subtasks) require a confirm step.
- AC0.4: Every list of objects has a sensible empty state with a primary CTA (Designer owns copy/illustration).
- AC0.5: P95 server response < 500ms for reads at 50-user scale; task complete/create < 200ms perceived.

### 4.1 Module: Auth & org setup

**A1. First-run org creation.**
As an admin, I want the first account created on a fresh install to set up the organization, so that deployment → usable takes minutes.
- AC: On a fresh DB, visiting the app shows a "Create your organization" flow: org name + admin name/email/password. Completing it creates the org, a default "General" team, and logs the admin in as role=admin.
- AC: Once an org exists, this flow is unreachable; the login page shows instead.

**A2. Email/password auth.**
As any user, I want to sign in with email + password, so that access is controlled.
- AC: Passwords ≥ 10 chars, stored hashed (argon2/bcrypt — Tech Lead's call). Sessions persist across browser restarts ("stay signed in" default on, 30-day session).
- AC: Login rate-limited (5 failures → 15-min lockout per account+IP).
- AC: "Forgot password" = admin-performed reset in MVP (admin generates a reset link from the Members page). No SMTP dependency required for the MVP to function. If SMTP is configured (env vars), invite + reset emails send automatically.
- Note: Entra ID SSO is Phase 2 (locked roadmap). Tech Lead: keep an identity abstraction so adding OIDC doesn't touch the user model.

**A3. Invites.**
As an admin, I want to invite teammates by email, so that the team gets in without me creating passwords.
- AC: Admin enters one or more emails → invite records created with tokenized links (shown in-app for copy/paste; emailed if SMTP configured). Invitee opens link → sets name + password → lands in the org as member.
- AC: Pending invites listed with revoke. Expiry 14 days. Re-invite regenerates the token.

**A4. Member management.**
As an admin, I want to see and manage members, so that leavers lose access.
- AC: Members page lists all users (name, email, role, active, last seen). Admin can promote/demote admin↔member and deactivate/reactivate. Deactivated users can't log in; their historical tasks/comments remain attributed.
- AC: At least one active admin must always exist (guard rail).

### 4.2 Module: Teams, projects & views

**B1. Teams.**
As a member, I want projects grouped by team, so that the sidebar reflects how we're organized.
- AC: Any member can create a team (name, description) and add/remove members. Teams with their projects appear in the sidebar navigation.
- AC: Deleting a team requires it to have no projects (move or delete projects first).

**B2. Create project.**
As a project lead, I want to create a project in under 2 minutes, so that new work is captured immediately.
- AC: Create dialog: name (required), team (defaults to my most recent), owner (defaults to me), start/end dates (optional), color, starting view (list default / board), and a **template choice: "Blank" or "Simple ops checklist"** (pre-seeded sections + example tasks). [PD-31, ratified via decision log]
- AC: On create, project has 3 default sections ("To do", "In progress", "Done") — or the template's sections — and opens in its default view.
- AC: A **deletable sample project** ("Getting started with Cairn") is seeded with the org at first-run, demonstrating sections, tasks, a subtask, and a status update; deleting it is one confirm. [PD-31]
- AC: Project header shows name, color, owner, dates, current status chip, and view switcher tabs (List | Board | *Overview*). Overview tab = description + status update history + members active in the project.
- AC: Edit all project fields inline from header/overview. Archive project (hidden from sidebar/search-default, excluded from portfolio roll-up counts but retained in history; unarchive possible). Delete = admin or project owner, confirm required, cascades tasks.

**B3. List view.**
As a member, I want a fast list of tasks grouped by section, so that I can scan and edit work like a document.
- AC: **Fixed column set** (PD-3): complete-toggle, title, assignee avatar, due date, priority chip — no column resize, show/hide, or per-user width persistence in MVP; grouping is by Section only (no group-by assignee/due). Grouped under section headers, respecting manual order.
- AC: Inline: click title to open task detail (side panel — list stays visible behind it); click assignee/due/priority cells to edit in place; Enter at end of a section adds a new task row (title-only quick add).
- AC: Drag to reorder within a section and across sections. Collapse/expand sections. Add/rename/reorder/delete sections (delete requires empty or moves tasks to a chosen section).
- AC: Toggle "show completed" (default: hide completed older than 7 days).
- AC: Sort within the view by due date, assignee, **or priority** (temporary sort; manual order is the persisted default). Sort-by-priority added per PD-14a so the priority field earns its keep in MVP.
- AC (confirmation, PD-30): **filters, group-by (other than Section), and saved views remain Phase 1** — no filter popover in the MVP list toolbar.
- AC: Keyboard scope in MVP (PD-3/PD-14b): ↑/↓ row navigation, Enter (open/quick-add), `x` (complete), Esc, Ctrl/Cmd-K search. No full chord map. Drag alternative for accessibility = "Move to section/position…" in the row ⋯-menu.

**B4. Board view.**
As a project lead, I want the same project as a kanban board, so that stand-ups can walk columns.
- AC: Sections render as columns; tasks as cards (title, assignee avatar, due date, priority chip, subtask count 2/5). Same data as list — moving a card between columns *is* changing its section, and is instantly reflected in list view.
- AC: Drag cards within/between columns persists order/section. Quick-add card at column top/bottom. Add/rename/reorder columns = section ops.
- AC: Clicking a card opens the same task detail panel as list view.

**B5. My Tasks.**
As a member, I want one view of everything assigned to me across all projects, so that I never miss my own work.
- AC: "My Tasks" is the default landing page after login. Groups: **Overdue / Today / Upcoming (next 7 days) / Later / No date**, each sorted by due date. Shows project name on each row. Complete/edit inline; click opens task panel.
- AC: Count badge of overdue+today items shown in the sidebar.
- AC (PD-9, the status-nudge block): if I **own projects** whose latest status update is **> 7 days old (or absent)**, My Tasks shows a block at the top — "Your projects — N need a status update" — listing those projects, each opening the D3 composer in one click. Computed on read (one query against status updates); no cron, no email required. This is the MVP's pull mechanism for the flagship §7 metric.

### 4.3 Module: Tasks

**C1. Task CRUD & detail panel.**
As a member, I want to capture and edit a task with the essentials, so that work is unambiguous.
- AC: Task detail panel (slide-over) shows: title, complete toggle, assignee (single-select user picker with typeahead), due date (plain date picker, clearable — no natural-language parsing in MVP, PD-31), priority, project + section (changeable via picker → moves the task), description (**plain text with URLs auto-linked** — rich text is Phase 1, PD-8), subtasks, attachments, comments/activity. There is **no task-level status field** — status exists on projects only (PD-18).
- AC: Create task via: list quick-add, board quick-add, global "+ New task" (asks for project), and from a subtask promotion (see C2).
- AC: Complete/incomplete toggles from list row, board card, and panel; completing plays a subtle affordance (Designer: motion) and sets completed_at.
- AC: Delete task (creator, assignee, project owner, or admin) with confirm; deletes its subtasks after explicit warning.
- AC: Every task has a stable URL that opens the panel over the owning project — this is what notifications and search link to. **Ratified URL/ID scheme (PD-29, one scheme across all docs): UUIDv7 identifiers in the API; web routes `/tasks/:id` (canonical) plus a `?task=` overlay param when the panel opens over a project view; no vanity slugs in MVP.** Confirmed with Tech Lead + Designer via the decision log, week 1.

**C2. Subtasks.**
As a project lead, I want to break a task into subtasks with their own assignees/dates, so that shared deliverables have clear per-person ownership.
- AC: Add subtasks from the task panel (title quick-add). Each subtask has assignee, due date, complete toggle; opening one shows a slim panel with breadcrumb to the parent.
- AC: Parent shows progress (3/5). Completing the parent with open subtasks prompts a warning (allowed, not blocked). One level deep only — the "add subtask" affordance is absent on subtasks.
- AC: "Promote to task" moves a subtask into a section of the project as a full task.
- AC: Subtasks assigned to me appear in My Tasks like any task.

**C3. Comments & @mentions.**
As a member, I want to discuss work on the task itself, so that context isn't lost in Teams chats.
- AC: Chronological comments on the task panel; author avatar + relative timestamp; edit/delete own comments (edited marker shown).
- AC: `@` typeahead inserts a mention; mentioned user gets a notification. Task activity (created, completed, assignee changed, due date changed, status/section moved) is interleaved as system entries in the same stream, visually distinct from comments.

**C4. Attachments.**
As a member, I want files on the task, so that the deliverable and the discussion live together.
- AC: Upload via button and drag-drop onto the task panel; multiple files; ≤ 25 MB each (clear error over). Images (png/jpg/gif/webp) render as thumbnails with a lightbox; other types show name/size/icon and download.
- AC: Uploader or admin can delete an attachment. Attachments are also attachable to a comment (renders inline in the stream).
- AC: Files stored on a Docker volume path; served with auth (no unauthenticated URLs).

### 4.4 Module: Portfolio layer

*Benchmark grounding: this module replicates the Asana-Advanced semantics we're paying to avoid — "unlimited projects per portfolio with real-time status/progress roll-ups, portfolio timeline/Gantt" (research-notes.md, Asana Portfolio specifics) — minus workload, custom-field roll-ups, and nesting.*

**D1. Create portfolio & membership.**
As a program lead, I want to group projects into a portfolio, so that a program is visible as one thing.
- AC: Any member creates a portfolio (name, description; owner = creator). Add/remove projects via typeahead picker; a project can be in multiple portfolios; ordering within the portfolio is manual (drag).
- AC: Portfolios appear in a dedicated "Portfolios" sidebar section. Deleting a portfolio never touches its projects (confirm copy states this).

**D2. Portfolio list (roll-up) view.**
As a program lead, I want a live table of my portfolio's projects, so that Monday review needs no slide deck.
- AC: Table columns: project name (link), **current status chip** (color + title of latest update, with "time since" — e.g. "At risk · 3d ago"), owner avatar, start → end dates, **task progress** (n complete / total, thin progress bar), team. 
- AC: Projects with **no status update yet, or none in the last 7 days**, show a "No recent update" grey chip — staleness is visible, not hidden (Principle 3). Threshold is **7 days**, matching the §7 flagship metric (PD-10; a 14-day chip would plateau the metric at 14 days).
- AC: Header summary: count of projects by status color (e.g. 4 on track · 2 at risk · 1 off track · 1 no update).
- AC: Rows live-update when a new status is posted (on refetch; realtime push not required for MVP).

**D3. Project status updates (the roll-up atom).**
As a project lead, I want to post a status in under a minute, so that I'll actually do it weekly.
- AC: "Update status" button on the project header and on the portfolio row (the portfolio row's inline chip is an **entry point that opens this same composer**, prefilled with the chosen color — not a separate chip-only log, PD-33). Compose = pick color (**On track / At risk / Off track / On hold** — ratified enum, PD-17), title (prefilled "Status update — {date}"), body (**plain text + auto-linked URLs**, PD-8). Posting sets the project's current status everywhere instantly.
- AC (PD-13): a **"Copy previous update"** button prefills the body from the project's last status update, ready to edit — the sub-60-second weekly path.
- AC (PD-14d): the "On hold" option carries one line of helper copy in the picker ("Work is paused but the project stays visible — use Archive to remove finished projects") to prevent confusion with archiving.
- AC: Status history lives on the project Overview tab, newest first, with author + timestamp.
- AC: Members of the project's team following it get an in-app notification when a status is posted (§4.6). No automated reminders in MVP (that's rules/automation, Phase 3) — but staleness is surfaced per D2 and pulled via the My Tasks owner-nudge block (B5, PD-9).

**D4. Portfolio timeline view (simplified per PD-2 — decided now, not end of Sprint 2).**
As a program lead, I want the portfolio's projects as bars on a shared time axis, so that sequencing and overlap are obvious.
- AC: Horizontal timeline: one row per project, **read-only bar** spanning start → end date, colored by current status color, project name label, owner avatar on the row. **Single fixed month-granularity axis** (no zoom presets), month gridlines, "today" line, plain horizontal scroll over a bounded window (−6 to +18 months) — no virtualization.
- AC: Projects missing start or end date render in a "Not scheduled" tray below the chart with **inline date editing in the tray** (and dates remain editable on the project itself); setting dates moves the bar onto the timeline. This — not bar dragging — is how dates get fixed in MVP.
- AC: Explicitly **not** in MVP (moved to Phase 1 per PD-2/PD-23): bar drag-move/drag-resize, zoom presets and zoom animation, weekend shading, virtualization, keyboard nudge/resize.
- AC: Also explicitly **not** in MVP: task-level rows, cross-project dependency lines, milestones on the timeline, critical path. This is a portfolio-of-projects view, not a Gantt.

**D5. Portfolio-level status (added per PD-12; PE sizing: ≤1 day).**
As a program lead, I want to set an overall status on the portfolio itself, so that I can report the program upward the same way projects report to me.
- Sizing basis: this is **literally the D3 composer pointed at a portfolio** (same enum, same title/body/plain-text model, same history list) — one polymorphic target or a second FK, one chip on the portfolio header, no new components. If implementation exceeds 1 day, it drops per the one-in-one-out rule (PD-39) and the Designer removes the chip.
- AC: Portfolio header shows an overall status chip (manual, set via the D3 composer); history on the portfolio view; same 7-day staleness treatment as D2. Portfolio status does **not** auto-derive from member projects in MVP.

### 4.5 Module: Search & navigation

**E1. Global navigation.**
- AC: Persistent left sidebar: My Tasks (with badge), Notifications (with unread badge), Portfolios (list), Teams → their projects (collapsible), + New buttons. Recently visited projects float to a "Recent" cluster at top.
- AC: Top bar: global search, "+ New" (task/project/portfolio), user menu (profile: name, password change; admin sees Members & Invites).

**E2. Global search.**
As a member, I want to find any task or project by name fast, so that navigation never blocks me.
- AC: Search field (shortcut `/` or Ctrl/Cmd-K) with typeahead over **task titles, project names, portfolio names** (prefix + substring, case-insensitive). **MVP mechanism (PD-28): `ILIKE`/`pg_trgm` trigram matching on title/name columns — no `tsvector`/GIN full-text in the MVP schema; tsvector arrives in Phase 1 with descriptions/comments.** No external search engine. Results grouped by type, show project context for tasks, keyboard navigable, Enter opens. This is plain search, **not** a command palette — no creation verbs or actions (PD-5).
- AC: Archived projects and completed tasks excluded from default results; "include completed/archived" toggle on the full results page.
- AC: Full-text search of descriptions/comments is **out** (Phase 1, with reporting).

### 4.6 Module: Notifications (in-app only)

**F1. Notification inbox.**
As a member, I want an in-app inbox of things that concern me, so that I don't need email for the tool to work.
- AC: Events that notify: **(1)** task assigned to me, **(2)** @mentioned in a comment, **(3)** comment added on a task I'm assigned to or created, **(4)** status update posted on a project in a portfolio I own or a project I own, **(5)** due date changed on a task assigned to me, **(6)** task assigned to me completed by someone else.
- AC: Bell icon + unread count in sidebar; inbox lists notifications newest-first with actor, verb, object, snippet, relative time; click deep-links to the task/project (opens panel); mark read on click; "mark all read".
- AC: No self-notifications (my own actions never notify me). Notifications retained 90 days.
- AC: Email digests, browser push, and Teams delivery are **out** (Phase 2 with M365 integration).

---

## 5. Explicitly OUT of the MVP

Locked roadmap phases: **Phase 1** dashboards/reporting → **Phase 2** Entra ID SSO + Teams/Outlook → **Phase 3** forms & rules/automation → **Phase 4** goals & workload, guest access.

| Cut item | Lands in | Note |
|---|---|---|
| Dashboards / charts / universal reporting | Phase 1 | Portfolio table (D2) is the only "report" in MVP |
| Custom fields (user-defined) | Phase 1 | §3.2; schema designed for it now |
| Full-text search (descriptions/comments), filters/saved views | Phase 1 | MVP search = ILIKE/trigram on titles/names only |
| Rich text (descriptions, comments, status bodies) | Phase 1 | MVP = plain text + auto-linked URLs everywhere (PD-8) |
| Timeline drag-editing (bar move/resize), zoom presets | Phase 1 | MVP timeline = read-only bars, fixed month zoom (PD-2) |
| CSV/JSON export UI (general) | Phase 1 | **Exception — MVP stretch (PD-11): one endpoint, "Export portfolio roll-up table as CSV", reusing the D2 query; first claim on Sprint-3 slack (~0.5 day)** |
| Entra ID SSO (OIDC/SAML), SCIM deprovisioning | Phase 2 | MVP = email/password + invites only |
| Teams app/tabs/bot, Outlook add-in, email notifications, calendar sync | Phase 2 | Benchmark M365 specifics are the blueprint |
| Forms (intake), rules/automation, recurring tasks, status-update reminders | Phase 3 | Staleness chip (D2) is the MVP stand-in for reminders |
| Task dependencies & cross-project dependencies, milestones, critical path, baselines | Phase 3 | Biggest deliberate cut; portfolio timeline ≠ Gantt |
| Task multi-homing (task in many projects) | Phase 3 | One project per task in MVP |
| Goals/OKRs, workload/capacity views | Phase 4 | Asana Advanced parity items, explicitly deferred |
| Guest/external access, private projects & granular permissions, per-project roles | Phase 4 | MVP: 2 org roles, everything org-visible |
| Nested portfolios (programs of portfolios) | Phase 4 | Asana's program construct; flat portfolios in MVP |
| Calendar view, timeline view *within* a project | Phase 1–3 (as demanded) | List + board only in MVP |
| Mobile apps / full mobile web | Not scheduled | Desktop-first internal tool |
| Time tracking, budgets, proofing, approvals, docs/whiteboards/chat | Not scheduled | Other tools exist; we don't chase all-in-one (see ClickUp cautionary tale in benchmark) |
| Multi-org tenancy, i18n, public API & webhooks | Not scheduled (API likely Phase 2/3 alongside integrations) | Single org, English UI |

---

## 6. What we deliberately do differently from Asana

Grounded in the benchmark's Asana findings (pricing table, Portfolio specifics, admin notes):

1. **No seat-gating of the portfolio layer.** Asana gates Portfolios/Goals/workload behind Advanced ($24.99/user) and SAML behind Enterprise. In Cairn, portfolios are a day-1 primitive for every user (Principle 1). The benchmark's core finding — every vendor monetizes the program layer — is our reason to exist.
2. **Zero marginal cost per seat.** Asana at 25 seats = $625/mo with +5 seat-bucket jumps; growing 26 → 30 seats costs real money. Cairn's cost = one VM. Adding seat #26, or seasonal contractors, is free; no bucket math, no license true-ups, no procurement cycle.
3. **Data ownership & residency.** Our Postgres, our VM, our backups, our jurisdiction. Ad-hoc SQL against live data from day 1 — no waiting for Phase 1 reporting to answer a one-off question. Asana gates data residency/eDiscovery at Enterprise+.
4. **Unlimited guests, eventually free-er than Asana's.** Asana's unlimited free guests is its best admin feature (benchmark, admin 4/5). When guest access lands (Phase 4), ours is unlimited *and* un-gated by tier — because there are no tiers.
5. **M365 integration as roadmap core, not upsell.** Asana's M365 stack scored 5/5 and is the model for Phase 2 (Teams tabs + message→task, Outlook add-in, later Power Automate-style webhooks). Difference: our SSO (Entra ID) ships as part of Phase 2 for everyone, not at an Enterprise price point.
6. **Ruthlessly smaller surface.** No goals/forms/proofing/AI in the product until the team pulls for them. Asana's own docked point in the benchmark — "layered concepts take orientation" — is what we avoid: fewer concepts, faster orientation, ops-team-shaped.
7. **Status hygiene built into the UI.** Asana relies on habit/automation for status cadence; Cairn makes staleness visible by default (grey "no recent update" chip in every portfolio roll-up) so social pressure does the work before Phase 3 automation exists.

**What we accept losing vs Asana (honest ledger):** mobile apps, 200+ integrations, workload/capacity, proofing/approvals, AI features, vendor support/SLA, and their 15 years of edge-case polish. The Project Director should re-state this trade in the pilot kickoff so expectations are set.

---

## 7. Success criteria for the MVP pilot

Pilot: 4 weeks post-launch (weeks 7–10), whole team invited, ≥ 2 real programs run as portfolios (mirroring the benchmark's suggested pilot design: one real program of 3–5 projects each). Metrics measured from our own DB (self-hosting = free analytics); baseline week = week 7.

### Adoption (primary — did it become the system of record?)

| Metric | Target by end of pilot |
|---|---|
| Activated users (signed in ≥ 2 distinct days) | ≥ 90% of invitees |
| Weekly active users (any write action) | ≥ 70% of activated users, weeks 9–10 |
| Active projects (≥ 5 tasks & ≥ 2 contributors) | ≥ 10 |
| Live portfolios (≥ 3 projects each) | ≥ 2, each viewed ≥ weekly by its program lead |
| **Status-update cadence** (flagship metric) | ≥ 80% of portfolio-member projects have a status update ≤ 7 days old, weeks 9–10 |
| Old process retired | Weekly status deck/spreadsheet officially discontinued by Ops Director (binary) |

### Task-level hygiene (is the data trustworthy?)

| Metric | Target |
|---|---|
| Tasks created during pilot | ≥ 300 total |
| Tasks with an assignee | ≥ 85% |
| Tasks with a due date | ≥ 70% |
| Tasks completed in-tool during pilot | ≥ 40% of tasks due within the pilot window |
| Comments per active project per week | ≥ 3 (proxy: discussion moved onto tasks) |
| Notification inbox opened per WAU | ≥ 3×/week |

### Quality & operability gates (must-pass)

- Zero data-loss incidents; nightly `pg_dump` backup restored successfully at least once during pilot.
- P95 page load < 1.5s, P95 API < 500ms at pilot load; no Sev-1 open > 24h.
- Pilot exit survey: ≥ 70% of weekly-active users answer "keep and extend" (vs "return to buying Asana"); System Usability-style ease score ≥ 4/5 median.

**Kill/pivot criterion (pre-agreed):** if status-update cadence < 50% and WAU < 40% at end of pilot despite the Project Director's adoption push, we stop building and revisit the buy decision (Asana Advanced remains the benchmarked fallback at $625/mo). Owning the tool only pays if the team actually lives in it.

---

## Appendix A — MVP scope summary (one screen, for sprint planning)

**In (6 weeks):** first-run org setup (+ sample project) · email/password auth · invites · member admin · teams · projects (create with templates/edit/archive/overview) · sections · list view (inline edit on 5 fixed columns, drag, quick-add, section grouping only) · board view (drag, same data) · My Tasks (Overdue-first + status-nudge block) · tasks (assignee, due date, priority, plain-text description) · subtasks (1 level) · comments + @mentions + activity stream (plain text) · attachments (25 MB, local volume) · portfolios (create, add projects, manual order) · portfolio roll-up table with status chips + 7-day staleness + progress · project status updates (4 colors, copy-previous, history) · portfolio status (D5, ≤1 day or cut) · portfolio timeline (read-only project bars, fixed month zoom, today line, dates-editable tray) · global nav + search (ILIKE/trigram on titles/names) · in-app notifications (6 event types).

**Stretch (Sprint-3 slack, in priority order):** CSV export of portfolio roll-up (PD-11) · SSE realtime (built last, pre-agreed cut to refetch-on-focus per PD-4).

**Out:** everything in §5.

## Appendix B — Open questions (owner → resolve by)

1. ~~Timeline drag-editing in vs out~~ — **RESOLVED (PD-2): out of MVP; Phase 1 backlog. Deleted.**
2. Is SMTP available on the VM at launch (nicer invites/resets) or do we ship copy-paste links only? — **PD chasing IT, answer by end of week 1 (PD-42)**; gates niceness only — PD-9 nudge works without it.
3. ~~Ratify or rename "Cairn"~~ — **RESOLVED (PD-42): Cairn ratified.**
4. Pilot programs: which 2 real programs seed the portfolios? — Project Director brings two candidates (3–5 projects each) to the week-5 checkpoint (PD-42).

## Appendix C — Feedback disposition (PMO review, review-pmo.md)

Every PD finding relevant to this document, with disposition. **Applied: 24 · No change needed: 5 · Rebutted: 0.**

| PD | Disposition | Where / note |
|---|---|---|
| PD-1 | Applied | Capacity cuts absorbed via PD-2/3/8/etc. below; Appendix A now reflects the simplified scope, SSE + CSV as ordered stretch |
| PD-2 | Applied | D4 rewritten: read-only bars, fixed month zoom, today line, editable tray; drag/zoom → Phase 1 (§5); open question 1 deleted |
| PD-3 | Applied | B3: fixed 5-column set, Section grouping only, reduced keyboard scope, ⋯-menu drag alternative |
| PD-4 | Applied | SSE listed as last-priority stretch in Appendix A with the pre-agreed refetch-on-focus cut; AC0.2 already satisfiable without it |
| PD-5 | No change needed | E2 already specced plain search; sentence added confirming no command-palette verbs |
| PD-8 | Applied | C1, C3, D3, §3.1: plain text + auto-linked URLs everywhere; rich text moved to Phase 1 (§5) |
| PD-9 | Applied | B5: "Your projects need a status update" owner-nudge block AC (computed on read, 7-day threshold) |
| PD-10 | Applied | D2 staleness 14d → 7d, matching §7 flagship metric |
| PD-11 | Applied | §5 + Appendix A: portfolio roll-up CSV export as MVP stretch, first claim on Sprint-3 slack |
| PD-12 | Applied | New D5: portfolio-level status, PE-sized at ≤1 day (same D3 composer retargeted); auto-drops per PD-39 if it exceeds 1 day |
| PD-13 | Applied | D3: "Copy previous update" body-prefill AC |
| PD-14a | Applied | B3: sort-by-priority added (cheap) so priority is not dormant |
| PD-14b | Applied | B3: keyboard ceiling = Ctrl-K/Enter/Esc/x/↑↓/quick-add |
| PD-14c | No change needed | Subtask "promote to task" retained (C2) — small, accepted by PD |
| PD-14d | Applied | D3: "On hold" helper copy in status picker |
| PD-15/32 | No change needed | B5 grouping already Overdue-first with No date; design aligns to spec |
| PD-16 | No change needed | §6 honest ledger stands; kickoff framing is a PD action |
| PD-17 | Applied | §3.1 + D3: `on_track/at_risk/off_track/on_hold` confirmed as ratified enum; no "Blocked" |
| PD-18 | Applied | C1: explicit "no task-level status field" sentence |
| PD-19 | Applied (confirm) | §3.2 enum `none/low/medium/high` stands as ratified; no Urgent |
| PD-23 | Applied | Zoom presets deleted from D4 (superseded by PD-2 fixed month zoom) |
| PD-24 | Applied | Resolved in the plain-text direction per PD-8 |
| PD-26 | Applied | §2.1: explicit sentence that lead/owner confer no permissions anywhere |
| PD-28 | Applied | E2: ILIKE/pg_trgm on titles/names; no tsvector in MVP |
| PD-29 | Applied | C1: UUIDv7 + `/tasks/:id` + `?task=` overlay stated as the one scheme; no slugs |
| PD-30 | Applied | B3: explicit AC confirming filters/group-by/saved views remain Phase 1 |
| PD-31 | Applied | B2: templates ("Blank", "Simple ops checklist") + deletable sample project added; C1: plain date picker (no NL parsing) |
| PD-35a | Applied | Same as PD-10 |
| PD-35b | Applied (confirm) | B2 already specs the Overview tab in the view switcher hosting D3 history; Designer adds it in v2 |
| PD-38/39/42 | No change needed (doc-level) | Status header updated to v1.1; Cairn ratified in §1.4; scope additions above are the PD's fast-tracked approvals — all to be retro-logged in `DECISIONS.md` |
