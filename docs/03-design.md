# Design Direction — Self-Hosted Program & Project Management Tool

**Author:** Designer · **Date:** 20 July 2026 · **Status:** v2 — reworked per PMO review (PD-1…PD-42); resubmitted against spec v1.1
**Scope:** MVP (3 × 2-week sprints) — core work management + portfolio layer
**Method:** design-system-first. Product type: data-dense enterprise daily-driver. Audience: 10–50 person general-ops team in an M365 org, mixed technical fluency. Dials: **density = high-ish · motion = low (3 JS moments, rest CSS) · accessibility ≥ WCAG 2.2 AA (non-negotiable)**.

Scope rule honored throughout (PD-39): every user-facing capability in this document traces to spec v1.1 Appendix A or to a PD-ratified decision-log entry. Nothing here is "decided" unilaterally; §8 lists disposition of every review finding.

---

## 1. Design principles

This is a tool people live in 6+ hours a day. It competes with Asana's polish on a 6-week budget, so we win on *speed-to-read* and *calm*, not on decoration.

1. **Speed-to-read beats visual delight.** Every list row, card, and status chip must be scannable in a single fixation: task name first, metadata second, decoration never. Ink hierarchy does the work; color is reserved for status and priority semantics.
2. **Keyboard-covered, mouse-complete.** The core loop (find, open, complete, quick-add, navigate) has a keyboard path — the *small* map in §3.3, not a chord vocabulary. The mouse path always exists too — this is a general-ops team, not vim users.
3. **Calm density.** High information density with generous *reading* rhythm: tight vertical spacing in tables (36px rows), but real whitespace between structural regions. No more than 2 font sizes visible in any one list surface.
4. **The URL is the state.** Every view and open task is addressable and shareable; back button always works. (Filter/group serialization deleted with the filters themselves — Phase 1, per spec §5.)
5. **Status is a first-class citizen — on projects.** `On track / At risk / Off track / On hold` colors are consistent from the portfolio roll-up bar down to the project header chip. Learn the color once, read it everywhere. Status exists on **projects only**; tasks have completed + priority, nothing else.
6. **Motion explains, never entertains.** Three JS animations confirm the three state changes that matter (complete, peek open/close, save); everything else is a CSS transition or nothing. All motion disappears under `prefers-reduced-motion`.
7. **Empty states teach.** With a 4-person team and no onboarding staff, every empty surface is a mini-tutorial with one primary action.

### What we keep from Asana

- **The mental model:** Portfolio → Project → Section → Task → Subtask. It scored 5/5 on portfolio in our benchmark precisely because this hierarchy is legible; we adopt it wholesale.
- **Side-peek task detail** (list stays visible behind the open task) — Asana's single best interaction. Ratified in the decision log.
- **Multi-view per project** (Overview / List / Board share one data model, switch via tabs).
- **Celebration on completion** — one subtle checkmark animation, not Asana's flying unicorns.
- **Inline editing** in list view — click a cell, type, Enter — scoped to the five spec fields (name, assignee, due date, priority, complete).

### What we reject from Asana

- **Layered-concept sprawl.** MVP exposes exactly four nouns: Task, Project, Section, Portfolio. No goals, no workload, no forms in the UI at all — not grayed-out upsells, *absent*.
- **Upsell chrome:** no banners, no locked features — we own the tool.
- **Heavy top bar + double sidebar.** One 240px sidebar, one 48px topbar, everything else is content.
- **Overly airy default density.** Our default is one notch denser than Asana's comfortable rows.
- **Ambiguous save state.** All inline edits save on blur/Enter with an explicit micro-confirmation (subtle field flash), never a mystery.

---

## 2. Design system

### 2.1 Foundations approach

Tokens ship as CSS custom properties in a single `tokens.css`, semantic layer over a primitive palette. **MVP ships light theme only** (PD-6). The semantic-token architecture is built dark-ready — the dark column below is retained as a *reference mapping* so the post-pilot dark theme is a re-map, not a redesign — but dark values are **not validated, not toggleable, and not a CI gate in MVP**. CI validates light-theme token pairs only. No theme toggle anywhere in the UI.

### 2.2 Color — primitives

Neutral ramp is slightly cool (blue-gray) to flatter data surfaces. Accent is a confident indigo-blue — distinct from status colors so "brand blue" never collides with status semantics.

```
--gray-0:  #FFFFFF   --gray-500: #64748B
--gray-25: #FBFCFD   --gray-600: #475569
--gray-50: #F6F8FA   --gray-700: #334155
--gray-100:#EEF1F5   --gray-800: #1E293B
--gray-200:#E2E7EE   --gray-850: #16202E
--gray-300:#CBD4E0   --gray-900: #0F1722
--gray-400:#94A3B8   --gray-950: #0A0F18

--indigo-300:#A5B4FC  --indigo-500:#6366F1  --indigo-600:#4F46E5  --indigo-700:#4338CA
```

### 2.3 Color — semantic tokens

Light values are the MVP source of truth; dark values are the deferred reference mapping (see §2.1).

| Token | Light (MVP) | Dark (deferred ref) | Use |
|---|---|---|---|
| `--surface-page` | `#F6F8FA` | `#0A0F18` | App background |
| `--surface-raised` | `#FFFFFF` | `#16202E` | Cards, panels, table body |
| `--surface-overlay` | `#FFFFFF` | `#1E293B` | Modals, drawers, popovers |
| `--surface-sunken` | `#EEF1F5` | `#0F1722` | Board column wells, input wells |
| `--surface-hover` | `#EEF1F5` | `#243247` | Row/card hover |
| `--surface-selected` | `#EEF2FF` | `#26304D` | Selected row, active nav item |
| `--border-default` | `#E2E7EE` | `#28354A` | Hairlines, table rules |
| `--border-strong` | `#CBD4E0` | `#3A4A63` | Inputs, focused containers |
| `--ink-primary` | `#0F1722` | `#F1F5FA` | Titles, task names |
| `--ink-secondary` | `#475569` | `#A9B7C9` | Metadata, labels |
| `--ink-tertiary` | `#94A3B8` | `#6B7A90` | Placeholders, timestamps |
| `--ink-inverse` | `#FFFFFF` | `#0F1722` | Text on accent/status fills |
| `--accent` | `#4F46E5` | `#818CF8` | Primary buttons, links, focus, active states |
| `--accent-hover` | `#4338CA` | `#A5B4FC` | |
| `--accent-subtle` | `#EEF2FF` | `#26304D` | Selected fills, accent chips |
| `--focus-ring` | `#4F46E5` | `#93A6FF` | 2px outline, 2px offset, everywhere |

**Status colors** — project-level health, the ratified spec enum: **`on_track / at_risk / off_track / on_hold`** (PD-17; "Blocked" is gone from the vocabulary everywhere — chips, bars, sort orders). Each has fill (chip background), ink (text/icon on subtle), and bold (solid bar) variants. Ink-on-subtle pairs are ≥ 4.5:1 in light theme (CI-checked); bold fills carry `--ink-inverse` at ≥ 4.5:1.

| Status | Subtle bg (L/D-ref) | Ink (L/D-ref) | Bold (L/D-ref) |
|---|---|---|---|
| On track | `#E7F6EC` / `#12291C` | `#116932` / `#6EE7A0` | `#188A42` / `#2FA968` |
| At risk | `#FDF3DC` / `#2E2410` | `#8A5A00` / `#F5C64B` | `#D97706` / `#E8A33D` |
| Off track | `#FDECEC` / `#331518` | `#B42328` / `#F58E8E` | `#DC2F34` / `#E25C5C` |
| On hold | `#F3EDFB` / `#261B33` | `#6D3AB8` / `#C9A8F5` | `#7C3AED` / `#9D6FE8` |
| No status | `#EEF1F5` / `#243247` | `#475569` / `#A9B7C9` | `#94A3B8` / `#64748B` |

On hold is purple, not red — off-track (red) means "trajectory is bad," on hold (purple) means "deliberately paused." **Status-picker helper copy (PD-14d):** the On hold option carries a one-line hint — *"Paused on purpose — the project stays visible. To hide a finished project, archive it."* Never rely on color alone: every status chip carries its text label; ▲ (at risk) and ⏸ (on hold) icons accompany color in compact contexts (timeline bars).

**Priority** — task-level, spec enum **`none / low / medium / high`** (PD-19; no "Urgent"). Rendered as a small flag icon + label: High `#D97706`, Medium `#4F46E5`, Low `#94A3B8`, None = no flag rendered. Always icon + label, never a bare colored dot. **Effort note (PD-14a):** until spec v1.1 resolves sort-by-priority, priority is set-and-display only — we spend no further design effort on it (no chips beyond the flag, no keyboard shortcut, no filter affordances).

Avatar identity palette (deterministic hash of user id, 8 hues, all ≥ 4.5:1 with white initials): `#4F46E5 #0E7490 #188A42 #B45309 #BE185D #7C3AED #B42328 #334155`. **Initials only in MVP** (PD-34) — no image avatars, no upload flow.

### 2.4 Typography

**Self-hostable only — WOFF2 files vendored into the repo, `@font-face`, no CDN.**

- **UI + everything:** **Inter** (SIL OFL) — variable font; `font-feature-settings: "cv05","tnum"` on numeric columns (tabular numbers for dates/counts — critical for scan speed).
- **Monospace (IDs, keyboard keycaps):** **JetBrains Mono** (SIL OFL).

No display face. One family keeps the font payload ≤ ~120KB.

Type scale (rem, base 16px; line-heights tuned for density):

| Token | Size/LH | Weight | Use |
|---|---|---|---|
| `--text-2xl` | 24/32 | 600 | Page titles (project name header) |
| `--text-xl` | 20/28 | 600 | Panel titles, modal titles |
| `--text-lg` | 16/24 | 600 | Section headers, card titles in portfolio |
| `--text-md` | 14/20 | 400/500 | **Default UI + body** — task names (500), inputs, buttons |
| `--text-sm` | 13/18 | 400 | Table metadata, chips, sidebar items |
| `--text-xs` | 11/16 | 500 | Overlines, column headers (uppercase, +0.04em tracking), timestamps |

### 2.5 Spacing, radius, elevation

- **Spacing scale (4px base):** `4, 8, 12, 16, 20, 24, 32, 40, 48, 64` as `--space-1…-16`. Component-internal padding 8/12; between-region gaps 16/24; page gutters 24 (≥1280px: 32).
- **Key density constants:** table row height **36px**; board card min-height 68px; sidebar item 32px; input height 32px (default) / 40px (modal forms); topbar 48px.
- **Radius:** `--radius-sm: 4px` (chips, inputs), `--radius-md: 6px` (buttons, cards, menu items), `--radius-lg: 10px` (modals, drawers, popovers), `--radius-full` (avatars, badges).
- **Elevation (light theme):**
  - `--shadow-1` hover/cards: `0 1px 2px rgb(15 23 34 / 0.06), 0 1px 3px rgb(15 23 34 / 0.08)`
  - `--shadow-2` popovers/dropdowns: `0 4px 12px rgb(15 23 34 / 0.10), 0 1px 3px rgb(15 23 34 / 0.06)`
  - `--shadow-3` modals/drawers/dragged cards: `0 12px 32px rgb(15 23 34 / 0.16), 0 2px 8px rgb(15 23 34 / 0.08)`

### 2.6 Component inventory (MVP)

Each ships as a spec'd, tokenized React component in Sprint 1–2. States required for every interactive component: default / hover / active / focus-visible / disabled / (loading where async).

| Component | Variants & key spec |
|---|---|
| **Button** | primary (accent fill) / secondary (border) / ghost / danger. Heights 32 (default), 28 (compact, table-inline), 40 (dialog CTAs). Icon-only requires `aria-label` + tooltip. Loading = spinner replaces label, width locked. |
| **Input / Textarea** | 32px; label above at `--text-sm` 500; error state = off-track border + message below with icon; inline-edit variant is borderless until hover/focus. |
| **Select / Combobox** | Popover listbox (`--shadow-2`), type-ahead filter, keyboard navigable; used for assignee, priority, section, project status. Assignee variant shows avatar+name rows. Status variant carries the On-hold helper line (§2.3). |
| **Date picker** | Input + calendar popover; typed dates in plain formats (`aug 12`, `12/08`). **No natural-language parsing** ("tomorrow", "fri") — cut per PD-31. Range mode for project start–end. |
| **Avatar** | 20/24/32px, **initials on identity hue only** (PD-34); stack variant (max 3 + "+N"). |
| **Chip** | status (subtle bg + ink + label), priority (icon+label), count. 20px tall, `--radius-sm`, `--text-sm`. Removable variant has 24px hit-area ✕. |
| **Modal** | Center, 480/640px, `--shadow-3`, backdrop `rgb(10 15 24 / 0.55)`; focus-trapped; Esc + backdrop click close (dirty-state confirm). Used only for create-project, confirm-delete. |
| **Drawer (side peek)** | Right-anchored, **fixed 520px** (no resize, no width persistence — PD-31), full height under topbar; the task-detail surface. Soft focus-trap (list behind remains clickable); Esc closes. |
| **Toast** | Bottom-left stack, max 3, 5s auto-dismiss (pause on hover/focus), action slot ("Undo"), `role="status"` aria-live polite. Error toasts persist until dismissed. |
| **Table (list view)** | 36px rows, sticky header + sticky first column (name), hairline rules, hover/selected surfaces; inline-edit targets on the five spec fields; row drag-handle on hover. **Fixed column set — no resize, no show/hide, no per-user persistence** (PD-3). |
| **Kanban card** | `--surface-raised`, `--radius-md`, `--shadow-1` on hover; contents: task name (2-line clamp, 500), meta row: due-date chip (off-track ink if overdue), priority flag, avatar, subtask count. Drag state: `--shadow-3` + slight tilt (CSS). |
| **Timeline bar** | 24px rounded-`--radius-sm` horizontal bar in the project's status bold color; **read-only** (no drag/resize handles — PD-2); label inside if ≥ 96px wide, else beside; hover tooltip = name, dates, status. |
| **Status composer** | The spec D3 ritual surface (see §4.d): color picker (4 statuses + helper copy) · prefilled title ("Status update — {date}") · plain-text body · **"Copy previous update"** button that prefills the body from the last update (PD-13) · post button. Opens from the project Overview tab and from the portfolio table chip. |
| **Empty state** | Icon (single-weight line style), 1-line headline, 1-line body, one primary button; centered in content region. |
| **Skeleton** | Token-gray shimmer blocks matching real layout geometry. |
| **Search overlay (Ctrl+K)** | See §3.4 — plain typeahead search per spec E2, not a command palette. |
| **Tooltip** | 300ms hover delay, `--text-sm`, dark fill; shortcuts shown as JetBrains Mono keycaps. |

Deliberately **not** in MVP: rich-text editor (descriptions, comments, and status bodies are **plain text + auto-linkified URLs** — now the ratified three-document position, PD-8/PD-24), charts library, calendar view, workload heatmap, command-palette verbs, theme toggle.

---

## 3. Information architecture & navigation

### 3.1 App frame

- **Topbar (48px):** product mark (left) · global search trigger (center-left, shows `Ctrl+K` keycap) · "+ New" split button · avatar menu (sign out, settings). *(No theme toggle — PD-6. No bell here — it lives in the sidebar per spec E1, PD-21.)*
- **Sidebar (240px, collapsible to 56px icon rail; collapse state persisted):**
  1. **My Tasks** — top slot; this is the post-login landing page (PD-22 — the "Home" surface is deleted; see §4.g).
  2. **Notifications** — bell + unread count badge (**in MVP**, spec F1; PD-21). Opens the inbox (§4.h).
  3. **Portfolios** — flat list (MVP: no nesting in the UI).
  4. **Projects** — **"Recent" cluster** (last 5 visited, per spec E1 — restored per PD-31) then all projects A–Z, each with an 8px project-color dot and a subtle status dot. *(No starring — cut, PD-31.)*
  5. Footer: collapse toggle.
- **Content region:** page header (breadcrumb: Portfolio › Project · title · status chip · view tabs) + view body.

### 3.2 URL scheme

Per the PD-29 ruling: **UUIDv7 ids in API and URLs, no vanity slugs** (`PRJ-8fk2` is deleted). Path structure below; task overlay via `?task=`.

```
/my-tasks                        → post-login landing page
/projects                        → project directory
/projects/:projectId             → redirects to default view
/projects/:projectId/overview    → project Overview tab (status history — §4.f)
/projects/:projectId/list
/projects/:projectId/board
/projects/:projectId/list?task=:taskId   → list + side peek open
/portfolios
/portfolios/:portfolioId         → overview (roll-up)
/portfolios/:portfolioId/timeline
/tasks/:taskId                   → canonical share link; task full-width
                                   with "open in project" affordance
/notifications
/settings/…
```

No filter/sort/group query params in MVP (filters are Phase 1 — PD-30). `?task=` is the only overlay param.

### 3.3 Keyboard map (MVP — deliberately small)

Per PD-3/PD-14b, the chord map is cut. The complete MVP set:

`Ctrl+K` search · `↑/↓` move row selection · `Enter` open selected task (peek) · `Esc` close peek/modal · `x` toggle complete on selected · quick-add (ghost row `Enter`-to-commit-and-continue).

Single-key shortcuts are suppressed while any input has focus (WCAG 2.1.4). Cut from MVP: `g`-chords, `a/d/s` field shortcuts, `?` sheet, Space-pickup drag. Drag-and-drop's non-pointer path is the "Move to…" menu (§6.4), not a keyboard-DnD state machine.

### 3.4 Global search (Ctrl+K) — spec E2, exactly

Plain typeahead search over **task titles, project names, portfolio names** (prefix + substring, ILIKE/trigram server-side per the PD-28 ruling — no full-text in MVP). Centered overlay, 560px: one input, one results list grouped Tasks / Projects / Portfolios, arrow-keys + Enter to navigate to the result. **No creation verbs, no actions, no command palette** — reversed per PD-5 and logged in `DECISIONS.md`; palette verbs go to the Phase-1 backlog.

### 3.5 Empty states (write once, reuse)

| Surface | Headline / action |
|---|---|
| No projects yet | "Create your first project" → primary button opens create modal (templates: **Blank**, **Simple ops checklist** — ratified into spec B2, PD-31). |
| Project, no tasks | Ghost first row in list ("Type a task name…") already focused — the empty state *is* the input. Board shows one ghost card per column. |
| No portfolios | "Portfolios roll projects up into one status view" + "New portfolio" button. |
| Portfolio, no projects | "Add projects to this portfolio" → inline project picker. |
| My Tasks empty | "You're clear. Tasks assigned to you land here." (no CTA — calm). |
| Notifications empty | "Assignments, comments, and status changes land here." |
| Search, no results | "No matches for '…'" (no clear-filters button — there are no filters). |

---

## 4. Key screen specs

### 4.a Project — List view (`/projects/:id/list`)

**Layout.** Page header (breadcrumb, title inline-editable, project status chip → opens status composer, member avatar stack, view tabs **Overview | List | Board**). Toolbar row: sort menu (**Due date / Assignee** only, per spec B3; sort-by-priority pends the spec decision, PD-14a) · "+ Add task". **No filter popover, no group-by control** (PD-3, PD-30) — grouping is by Section, always, as structure not as an option. Below: the Table grouped by collapsible Section headers (name, count badge, "+" add-in-section, ⋯ menu: rename/delete/move).

**Columns (fixed set):** ✓ complete-circle · Name (sticky, min 320px, subtask count + expand caret) · Assignee · Due date · Priority · Comments count. *(Task-level "Status" column deleted — status is project-only, PD-18. No column resize/show-hide/persistence — PD-3.)*

**Inline edit** — the five spec fields only. Single click on a cell enters edit mode in place (text → borderless input; assignee/priority/due → popover pickers anchored to the cell). `Enter`/blur commits (optimistic, rollback + error toast on failure), `Esc` reverts, `Tab` commits and moves on. Name cell: second click edits; first click on the row (outside interactive cells) opens the side peek.

**Add task.** Bottom of every section: permanent ghost row "Add task…". `Enter` commits and opens a fresh ghost row beneath (rapid entry).

**Row states:** hover (surface-hover + drag handle + quick-complete), selected (keyboard cursor: `--surface-selected` + 2px accent left rail), completed (name struck, ink-tertiary; completed tasks collapse into "Completed (n)" toggle), overdue (due-date text in off-track ink).

**Reorder:** pointer drag via handle within/between sections. Non-drag path: row ⋯ menu → **"Move to section / position…"** (satisfies WCAG 2.5.7 — PD-3; no Space-pickup keyboard DnD).

### 4.b Project — Board view (`/projects/:id/board`)

**Layout.** Same header/toolbar. Horizontally scrolling columns (one per Section): 280px well (`--surface-sunken`, `--radius-lg`), header (name, count, "+", ⋯), card stack, "Add task" ghost card, `+ Add section` column at end.

**Card** = Kanban card (§2.6). Click opens side peek. Quick-complete circle on card hover.

**Drag-and-drop (pointer).** Lift after 4px movement threshold (click ≠ drag); dragged card gets `--shadow-3` + slight tilt via CSS class; origin shows a dashed ghost; target column tints; drop settles with a short CSS transition (PD-7 — no JS spring). Cross-column drop updates section (optimistic). Auto-scroll near viewport edges. Non-drag path: card ⋯ menu → "Move to section / position…". Screen-reader announcement on move via `aria-live` ("Moved 'Order signage' to Doing, position 2 of 5").

**Column overflow:** columns virtualize past ~50 cards; count badge always exact.

### 4.c Task detail — side peek drawer (ratified in decision log)

Task work is contextual — triage a list, open, edit, close, move on. A modal severs context; the peek keeps `↑/↓` retargeting alive. Full-page view exists only at `/tasks/:id` for shared links.

**Layout (fixed 520px right drawer):**
1. **Header row:** complete-toggle ("Mark complete" → filled) · copy-link · full-page icon · ⋯ (delete, move to project) · ✕.
2. **Title:** `--text-xl` borderless textarea, auto-grow.
3. **Field grid (2-col):** Assignee (avatar select) · Due date · Priority · Project › Section (combobox) — same pickers as inline list edit (one component, two anchors). *(No task Status field — PD-18.)*
4. **Description:** plain-text auto-grow area, URLs auto-linkified (PD-8), "Add a description…" placeholder.
5. **Subtasks:** compact checklist rows (complete-circle, name, assignee-mini, due-mini), "+ Add subtask"; row click swaps the peek to that subtask (breadcrumb back to parent).
6. **Attachments:** chip list + drop target (whole drawer accepts drag-over, dashed overlay).
7. **Activity & comments:** system events collapsed ("Show 12 updates"); comment composer pinned at bottom (plain text + auto-linked URLs, @mention typeahead, attach button; `Ctrl+Enter` sends).

**Behavior:** opening sets `?task=`; `Esc`/✕ closes (unsent comment → confirm); `↑/↓` while list has cursor swaps peek content in place (content crossfade 120ms). All edits optimistic with per-field rollback.

### 4.d Portfolio overview (`/portfolios/:id`)

**Layout.**
1. **Header:** portfolio name (inline edit) · tabs **Overview | Timeline** · "+ Add projects". **Portfolio-level status chip: D5, ratified with an auto-drop rule (PD-12, spec v1.1)** — the same status composer pointed at the portfolio, PE-sized at ≤1 day; if implementation exceeds the 1-day box it auto-drops per PD-39 and this chip is removed.
2. **Roll-up strip (four stat tiles, 96px):** Projects (n) · **Status bar** — single stacked bar of member-project statuses in bold status colors, each segment labeled with count (not color-only) · Tasks complete (x/y, thin progress bar) · Due range (earliest start → latest end).
3. **Project table:** rows = member projects. Columns: Name (color dot + link) · Status chip · Progress (thin bar + %) · Start · End (inline-editable dates) · Owner (avatar) · Task count · ⋯ (remove from portfolio). Sortable; default sort = status severity (**off track, at risk, on hold, on track, no status** — PD-17 order) so trouble floats to the top. **Staleness:** a status older than **7 days** (aligned to the flagship metric, PD-10 — set in spec v1.1) shows the grey stale treatment on the chip.
4. **Status editing (PD-33, PD-26):** the chip is an *entry point* — clicking it (any member, not just "leads") opens the **spec D3 status composer** (§2.6) prefilled with the clicked color: color + prefilled title + plain-text body + "Copy previous update" (PD-13). Posted updates land in the project's status history on its Overview tab (§4.f). No parallel chip-only "status log."
5. **CSV export (MVP-stretch, PD-11):** an "Export CSV" ghost button on the table toolbar, reusing the roll-up query — first claim on Sprint-3 slack, per spec §5.

**States:** loading = skeleton tiles + rows; empty per §3.5; a project in >1 portfolio is allowed (chip shows "also in…" tooltip).

### 4.e Portfolio timeline (`/portfolios/:id/timeline`) — simplified per PD-2

**Read-only bars at a single fixed Month zoom.** No zoom presets, no zoom animation, no bar drag/resize, no keyboard nudge/resize, no weekend shading, no virtualization. Drag-editing is Phase-1 backlog (decision made now, not end of Sprint 2).

**Layout.** Left rail (240px, sticky): project name + status dot per row, aligned 1:1 with bars. Main canvas: month-granularity time axis (sticky header, month boundaries as hairlines), horizontal scroll over a **bounded window of −6 / +18 months** — plain overflow scroll, no virtualization. Each project = one read-only Timeline bar (§2.6) spanning start→end, colored by status bold variant.

- **Today line:** 2px accent vertical rule, full height, "Today" pill at top; "Jump to today" button when today is off-screen.
- **Bars:** hover tooltip (name, dates, status); click → opens that project (Ctrl+click new tab). Focusable, with full `aria-label` including the status word.
- **"No dates" tray:** undated projects collect in a shelf under the chart **with inline start/end date editing directly in the tray** — that is how dates get fixed in MVP (PD-2), not by dragging bars.
- **MVP cut (unchanged):** no dependencies, no milestones, no baselines.

### 4.f Project Overview tab (`/projects/:id/overview`) — added per PD-35b

The third view tab from spec B2, previously missing from this doc. Hosts:

1. **Status block:** current status chip + "Update status" button → the D3 composer (§2.6); beneath it, the **status history** — reverse-chronological list of posted updates (color, title, body, author avatar, timestamp). This is where the §7 flagship metric becomes visible to the team.
2. **About:** project description (plain text + auto-linked URLs), owner, start–end dates, member avatar stack.
3. **Quick stats:** tasks complete x/y with progress bar, overdue count.

### 4.g My Tasks (`/my-tasks`) — the landing page

Post-login landing surface (PD-22; Home is deleted).

1. **Owner nudge block (PD-9 — top of page, project owners only):** "Your projects — 2 need a status update" with the stale projects listed as chips; each click opens that project's status composer directly. Computed on read; hidden when zero.
2. **Task groups, in this order (PD-15/PD-32):** **Overdue** (first — off-track ink header, this is the point of the view) · **Today** · **Upcoming** (next 7 days) · **Later** · **No date**. Each row: complete-circle, name, project pill, due date; click opens side peek; `↑/↓`/`Enter`/`x` work here exactly as in list view.
3. **First-run checklist card (moved here from the deleted Home — PD-22):**
   - Admin first sign-in: "Set up your workspace" — Create a project (opens create modal with the two templates) · Invite your team (copies invite link) · Create a portfolio (enabled after ≥1 project). Persists until complete or dismissed; state per-workspace.
   - Invited member first sign-in: "You've joined ⟨workspace⟩" + assigned tasks + "Press `Ctrl+K` to go anywhere" keycap hint.

### 4.h Notifications inbox (`/notifications`) — restored to MVP per PD-21

Spec F1, in MVP. Entry: sidebar bell + unread count badge (placement = sidebar, per spec E1; topbar placement declined and logged).

**Layout.** Simple reverse-chronological list, grouped Today / Earlier. Each row: actor avatar · one-line event sentence ("Sara assigned you 'Order signage'", "Status changed to At risk on Fit-out Phase 2") · relative timestamp · unread dot. Row click deep-links (task rows open the peek in project context; status rows open the project Overview tab) and marks read. "Mark all read" in the header. The six spec F1 event types, nothing more. Unread badge caps at "9+". Empty state per §3.5.

### 4.i Onboarding / first-run

No tour framework, no coach-marks SDK — three cheap moments:

1. **First-run checklist** — on My Tasks, per §4.g.3.
2. **Just-in-time hints** (one-shot tooltips, each shows exactly once, **stored in localStorage** — no schema, PD-31): first list view → "Click any cell to edit"; first board view → "Drag cards between sections — or use the ⋯ menu"; first task complete → the completion animation is the reward. Never more than one visible.
3. **Seed content:** the "Simple ops checklist" template creates a 3-section, 8-task sample project marked `Sample` (deletable in one click) so list/board/peek are demonstrable in the pilot. (Templates + sample ratified into spec B2 — PD-31.)

---

## 5. Motion spec — reduced per PD-7

**Three JS moments** (anime.js v4), everything else CSS transitions or nothing.

**Global rules.** Named imports only (`import { animate, createTimeline, svg, utils } from 'animejs'`); all animation code inside a `createScope()` per view, reverted on unmount. Durations: micro 120–200ms, transitions 200–320ms. Nothing blocks input; all motion interruptible. One motion per event.

**Reduced-motion rule (non-negotiable).** A single `motionOK()` gate wraps every call: under `prefers-reduced-motion: reduce`, durations collapse to 0 via `utils.set()` (state still applied instantly). Opacity-only crossfades ≤ 120ms are the sole residual. One module — the fallback can never be forgotten per-feature.

| # | Moment | Trigger | Spec |
|---|---|---|---|
| 1 | **Task complete** | Click/`x` on complete circle | `createTimeline()`: circle fill `scale [0.6→1]` 180ms `outBack` → SVG check draw (`svg.createDrawable`) 220ms `outQuad` offset `-=120` → row/card fade to completed style 200ms. If list hides completed: row collapse after 600ms dwell. Reused at 1.25× for first-run checklist steps. |
| 2 | **Drawer (side peek) open/close** | Row click/`Enter`; `Esc`/✕ | Open: `x ['100%','0%']` 260ms `outExpo` + content fade 160ms offset 80ms. Close: 200ms `inQuad`. Peek-swap (↑/↓): crossfade 120ms only. |
| 3 | **Inline edit commit flash** | Successful inline save | Cell background pulse `--accent-subtle → transparent`, 400ms — the "it saved" confirmation from principle 1. Failure: 2× horizontal shake 240ms + error styling. |

**CSS-only (no JS):** board card drop settle (short transform transition), drag lift (class swap), toast enter/exit (transform+opacity transition), skeleton shimmer (keyframes) + content swap-in (opacity transition), hover states (≤120ms), modals (fade+2%-scale, 160ms), sidebar collapse (150ms width).

**Cut entirely:** timeline zoom choreography (no zoom exists — PD-2), staggered content swap-in, toast stack choreography, spring physics.

**Scheduling (PD-7):** the Designer+PE motion-tuning pairing in week 5 happens **only if Sprint 3 opens green**; otherwise that week is polish burn-down and pilot prep, and motion ships as-spec'd or CSS-only.

Not animated, on purpose: page navigation — instant view swap; speed *is* the brand.

---

## 6. Accessibility — WCAG 2.2 AA commitments

Priority order: accessibility > interaction > performance > style; these are acceptance criteria (per the Definition of Done, PD-36: axe scan + manual keyboard pass on new surfaces, scoped per story). The Designer runs the manual pass at each sprint review.

1. **Contrast:** all ink-on-surface pairs in §2.3 ≥ 4.5:1 (text) and ≥ 3:1 (UI components/graphics, incl. focus ring vs adjacent colors, status bold fills vs page, timeline bars vs canvas). **Light theme validated with a scripted token-pair check in CI** (dark theme check deferred with the theme — PD-6); a failing pair fails the build.
2. **Focus (2.4.7, 2.4.11, 2.4.13):** universal `:focus-visible` ring — 2px `--focus-ring`, 2px offset. Sticky headers never obscure a focused row (scroll-margin). Focus order follows visual order; drawer open moves focus to drawer title, close returns it to the originating row; modals trap focus.
3. **Target size (2.5.8):** every pointer target ≥ 24×24px — complete-circles, chip ✕, drag handles, ⋯ menus.
4. **Dragging alternative (2.5.7):** every drag operation has a non-drag path — the **⋯ menu "Move to section/position…"** for list rows and board cards, "Move left/right" for columns (PD-3 ruling: menu path, not Space-pickup keyboard DnD). Timeline bars are read-only, so no alternative is needed there; dates are edited in the tray/table.
5. **Keyboard completeness (2.1.1, 2.1.2):** every action in the reduced §3.3 map verified per sprint; no keyboard traps (Esc always works); single-key shortcuts suppressed while inputs have focus (2.1.4).
6. **Live regions (4.1.3):** one polite `aria-live` region for toasts/save confirmations/move announcements; assertive only for failed saves. Optimistic updates announce on commit, with a follow-up on rollback.
7. **Semantics:** list view = table semantics with roving row focus (row-level `↑/↓` + `Enter`; full ARIA-grid cell navigation is not in MVP — PD-3); board = labeled regions per column (`aria-label="Doing, 5 tasks"`); status/priority conveyed as text (chips carry labels; timeline bars have full `aria-label` incl. status word); icons decorative unless sole content.
8. **Reflow & zoom (1.4.4, 1.4.10):** usable at 200% zoom; table scrolls horizontally within its own container; drawer becomes full-width sheet < 720px viewport.
9. **Motion (2.3.3):** §5 reduced-motion gate; no flashing content.
10. **Forms & errors (3.3.1–3.3.3):** every input labeled (visible or `aria-label` for inline edits), errors identified in text adjacent to the field, error toasts persist and are reachable.

---

## 7. Design deliverables plan — 6 weeks

Working agreement with the PE: **specs land ≥ 3 working days before the build slot** (tracked as a visible spec-readiness checklist in the weekly PD checkpoint — PD-41); design review of built UI happens *in the running app* twice a week (Tue/Thu, 30 min); findings filed as `design-qa` issues with severity (blocker / before-pilot / polish). Scope discipline: anything not in spec Appendix A goes through `DECISIONS.md` with a one-in-one-out cut (PD-38/39).

### Sprint 1 (weeks 1–2) — Foundations + first surfaces
- **Day 1–2:** `tokens.css` (§2.2–2.5, light theme, machine-readable) + vendored fonts → PE unblocked immediately.
- **Day 3–5:** Component specs batch 1 (button, input, select, avatar, chip, table row, toast, skeleton) — anatomy, states, tokens, a11y notes; delivered as a static HTML/CSS reference page in-repo (living spec, no Figma dependency).
- **Week 2:** Screen specs: app frame + sidebar, **list view** (4.a), **side peek** (4.c), **My Tasks incl. nudge block** (4.g). Empty-state copy pack v1. First Tue/Thu in-app reviews on list view.
- Exit: PE has everything for list + peek + My Tasks; tokens frozen (changes after this are versioned via the decision log, not silent).

### Sprint 2 (weeks 3–4) — Board + portfolio surfaces
- Component specs batch 2 (kanban card, modal, date picker, timeline bar, **status composer**, search overlay).
- Screen specs: **board view** (4.b) incl. drag interaction + "Move to…" menu path; **portfolio overview** (4.d) incl. composer flow; **portfolio timeline** (4.e — read-only month view, bounded window); **project Overview tab** (4.f); **notifications inbox** (4.h).
- Mid-sprint: design QA pass 1 on Sprint-1 surfaces (light-theme contrast script + manual keyboard audit).
- Exit: all MVP screens spec'd; board + portfolio in build; a11y findings triaged.

### Sprint 3 (weeks 5–6) — Onboarding, polish, pilot prep
- **Week 5:** first-run/onboarding (4.i) + final copy pack; empty states implemented; **conditional** motion pairing (§5 — only if Sprint 3 opens green, else polish burn-down); CSV-export stretch if slack exists (PD-11).
- **Week 6 (feature freeze in effect, PD-40):** Design QA pass 2 — full-product sweep: light theme, 200% zoom, reduced-motion, keyboard-only end-to-end run (create-project → tasks → board → portfolio → status update → notification); polish burn-down of `design-qa` issues with the PD prioritizing against pilot readiness; pilot-feedback intake form + 30-min usability session script for the PD's pilot kickoff.
- Exit: MVP visually complete, AA checklist signed per DoD, onboarding live, demo 3 = pilot go/no-go input.

**Standing risks:** (1) even read-only timeline layout math (month axis, bounded window, today line) is the largest remaining design-engineering unknown — the floor below it is a sorted date-bar *table*, which still meets cross-project visibility; (2) Ctrl+K search depends on the ILIKE search endpoint landing in Sprint 2 — fallback is client-side project/portfolio jump only.

---

## 8. Feedback disposition — PMO review (PD-# relevant to this document)

| PD | Disposition |
|---|---|
| PD-2 | **Applied** — timeline redrawn: read-only bars, fixed Month zoom, today line, tray with inline date editing; drag/resize/zoom/virtualization/weekend shading/keyboard-nudge all deleted (§4.e, §2.6, §5). |
| PD-3 | **Applied** — fixed columns, Section grouping only; cut resize/show-hide/persistence, group-by, filter popover; keyboard reduced; 2.5.7 via "Move to…" menu (§4.a, §3.3, §6). |
| PD-5 | **Applied** — command palette replaced with plain Ctrl+K typeahead per spec E2; verbs to Phase-1 backlog (§3.4). |
| PD-6 | **Applied** — light theme only; toggle removed; dual-theme CI gate removed; token architecture kept dark-ready as reference (§2.1, §2.3, §3.1, §6.1). |
| PD-7 | **Applied** — motion reduced to task-complete, drawer open/close, commit flash as JS; rest CSS or cut; week-5 pairing conditional on green Sprint 3 (§5). |
| PD-8 / PD-24 | **Applied (design position stood; now ratified)** — plain text + auto-linkified URLs stated explicitly for descriptions, comments, status bodies (§2.6, §4.c). |
| PD-9 | **Applied** — owner status-nudge block designed at top of My Tasks, chips open the composer (§4.g.1). |
| PD-11 | **Applied** — CSV export button on portfolio table designed as MVP-stretch, first claim on Sprint-3 slack (§4.d.5, §7). |
| PD-12 | **Applied** — portfolio status chip removed from the header pending PE sizing + spec decision; noted as same-composer-if-ratified (§4.d.1). |
| PD-13 | **Applied** — "Copy previous update" button in the status composer (§2.6, §4.d.4). |
| PD-14 | **Applied** — (a) priority is set-and-display only, no extra spend, pending spec sort decision; (b) chord map cut; (d) On-hold helper copy in the status picker (§2.3, §3.3). |
| PD-15 / PD-32 | **Applied** — My Tasks groups: Overdue / Today / Upcoming / Later / No date, Overdue first (§4.g.2). |
| PD-17 | **Applied** — Blocked → On hold everywhere: color table, icons, sort order, timeline bars; spec enum adopted (§2.3, §4.d.3). |
| PD-18 | **Applied** — task-level Status field removed from list columns, peek field grid, and shortcuts (§4.a, §4.c, §3.3). |
| PD-19 | **Applied** — priority enum none/low/medium/high; "Urgent" deleted (§2.3). |
| PD-21 | **Applied** — notifications restored to MVP: sidebar bell + badge (spec's placement adopted; topbar declined, logged) and inbox designed (§3.1, §4.h). |
| PD-22 | **Applied** — Home deleted; My Tasks is the landing page; first-run checklist moved onto My Tasks (§3.1, §4.g). |
| PD-23 | **Applied** — superseded by PD-2; single fixed Month zoom (§4.e). |
| PD-26 | **Applied** — "editable by leads" removed; any member opens the composer from the portfolio chip (§4.d.4). |
| PD-29 | **Applied** — slugs (`PRJ-8fk2`) deleted; UUIDv7 ids; `/tasks/:id` + `?task=` overlay per ruling (§3.2). |
| PD-30 | **Applied** — filter popover, non-Section group-by, and filter query-param serialization removed (§1.4, §3.2, §4.a). |
| PD-31 | **Applied** — cut: starring, NL date parsing, resizable/persistent drawer + column widths; kept (now ratified): create-project templates + sample project; hints moved to localStorage; "Recent projects" sidebar cluster restored (§2.6, §3.1, §4.i). |
| PD-33 | **Applied** — inline chip is an entry point that opens the spec D3 composer (color+title+body); chip-only "status log" deleted; history lives on the project Overview tab (§4.d.4, §4.f). |
| PD-34 | **Applied** — avatars are initials-only; "image when set" removed (§2.3, §2.6). |
| PD-35b | **Applied** — project Overview tab added, hosting status history (§4.f, view tabs in §4.a). |
| PD-36 / PD-37 / PD-38 / PD-39 / PD-40 / PD-41 | **Applied (as they bind this doc)** — DoD a11y items referenced in §6; decisions routed to `DECISIONS.md` (side-peek ratified, palette reversed, notification placement); one-in-one-out honored; week-6 freeze reflected in §7; spec-readiness tracking added to the working agreement. |

**Rebuttals: none.** No review finding conflicted with a locked decision.
