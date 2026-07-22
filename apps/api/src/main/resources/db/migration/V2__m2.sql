-- Cairn M2 schema (portfolio layer + project schedule fields + status updates).
-- EXPAND-ONLY and backward-compatible with V1 (never edit V1): all new columns are nullable and all
-- new tables are additive, so a V1 api keeps working against a V2 database during a rolling deploy.
-- Still single-org (D-028): every new tenant table carries organization_id so the org-filter seam
-- applies uniformly and RLS can drop in as a second wall at commercialization (not built in M2).
-- IDs are UUIDv7 minted in app code (no DB default). Timestamps are timestamptz (UTC).
-- Indexed string columns are varchar(n) (jOOQ's H2-based DDL simulation can't index text/CLOB).

-- ---------------------------------------------------------------------------
-- Project schedule + denormalized current status (D2/D3)
-- start/end are plain schedule dates; current_status + status_updated_at are denormalized from the
-- latest project_status_updates row (written in the same tx as the status update) so the roll-up can
-- read a project's status without a per-project subquery.
-- ---------------------------------------------------------------------------

alter table projects add column start_date date;
alter table projects add column end_date date;
alter table projects add column current_status text;
alter table projects add column status_updated_at timestamptz;

-- [jooq ignore start] (check constraint — Postgres enforces it; jOOQ's H2 DDL simulation skips it)
alter table projects
    add constraint projects_current_status_chk
    check (current_status in ('on_track', 'at_risk', 'off_track', 'on_hold'));
-- [jooq ignore stop]

-- ---------------------------------------------------------------------------
-- Portfolios (D1) — a cross-team grouping of projects; the roll-up reads these.
-- ---------------------------------------------------------------------------

create table portfolios (
    id              uuid         primary key,
    organization_id uuid         not null references organizations (id),
    owner_id        uuid         references users (id),
    name            text         not null,
    description     text,
    color           text,
    sort_key        varchar(255) not null,
    created_at      timestamptz  not null default now()
);

create index portfolios_org_idx on portfolios (organization_id);

create table portfolio_projects (
    organization_id uuid         not null references organizations (id),
    portfolio_id    uuid         not null references portfolios (id),
    project_id      uuid         not null references projects (id),
    sort_key        varchar(255) not null,
    added_at        timestamptz  not null default now(),
    constraint portfolio_projects_pk primary key (portfolio_id, project_id)
);

create index portfolio_projects_project_idx on portfolio_projects (project_id);
create index portfolio_projects_order_idx on portfolio_projects (portfolio_id, sort_key);

-- ---------------------------------------------------------------------------
-- Project status updates (D3) — append-only history, newest first. A POST also denormalizes
-- projects.current_status + status_updated_at in the same transaction.
-- ---------------------------------------------------------------------------

create table project_status_updates (
    id              uuid         primary key,
    organization_id uuid         not null references organizations (id),
    project_id      uuid         not null references projects (id),
    author_id       uuid         references users (id),
    status          text         not null check (status in ('on_track', 'at_risk', 'off_track', 'on_hold')),
    title           text,
    body            text,
    created_at      timestamptz  not null default now()
);

create index project_status_updates_project_created_idx
    on project_status_updates (project_id, created_at desc);
