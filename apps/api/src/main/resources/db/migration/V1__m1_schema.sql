-- Cairn M1 schema (single-org, D-028).
-- Every tenant-owned table carries organization_id so the org-filter seam applies uniformly
-- and RLS can drop in as a second wall at commercialization (not built in M1).
-- IDs are UUIDv7 minted in app code (no DB default). Timestamps are timestamptz (UTC).
-- No RLS in M1. Email uniqueness via a functional lower() unique index (no citext extension).

-- ---------------------------------------------------------------------------
-- Identity & org
-- ---------------------------------------------------------------------------

create table organizations (
    id          uuid        primary key,
    name        text        not null,
    created_at  timestamptz not null default now()
);

create table users (
    id            uuid          primary key,
    email         varchar(320)  not null,
    name          text          not null,
    password_hash text,
    created_at    timestamptz not null default now()
);

-- [jooq ignore start] (functional index — jOOQ's H2-based DDL simulation can't model it; Postgres runs it)
create unique index users_email_lower_uk on users (lower(email));
-- [jooq ignore stop]

create table memberships (
    id              uuid        primary key,
    organization_id uuid        not null references organizations (id),
    user_id         uuid        not null references users (id),
    role            text        not null default 'member' check (role in ('admin', 'member')),
    active          boolean     not null default true,
    created_at      timestamptz not null default now(),
    constraint memberships_org_user_uk unique (organization_id, user_id)
);

create index memberships_user_idx on memberships (user_id);

create table auth_sessions (
    id                 uuid        primary key,
    organization_id    uuid        not null references organizations (id),
    user_id            uuid        not null references users (id),
    refresh_token_hash varchar(255) not null,
    expires_at         timestamptz not null,
    created_at         timestamptz not null default now(),
    revoked_at         timestamptz
);

create unique index auth_sessions_token_uk on auth_sessions (refresh_token_hash);
create index auth_sessions_user_idx on auth_sessions (user_id);

create table invites (
    id              uuid        primary key,
    organization_id uuid        not null references organizations (id),
    email           varchar(320) not null,
    token_hash      varchar(255) not null,
    invited_by      uuid        not null references users (id),
    expires_at      timestamptz not null,
    revoked_at      timestamptz,
    accepted_at     timestamptz,
    created_at      timestamptz not null default now()
);

create unique index invites_token_uk on invites (token_hash);
create index invites_org_email_idx on invites (organization_id, email);
create index invites_invited_by_idx on invites (invited_by);

-- ---------------------------------------------------------------------------
-- Teams
-- ---------------------------------------------------------------------------

create table teams (
    id              uuid        primary key,
    organization_id uuid        not null references organizations (id),
    name            text        not null,
    description     text,
    created_at      timestamptz not null default now()
);

create index teams_org_idx on teams (organization_id);

create table team_members (
    id              uuid        primary key,
    organization_id uuid        not null references organizations (id),
    team_id         uuid        not null references teams (id),
    user_id         uuid        not null references users (id),
    created_at      timestamptz not null default now(),
    constraint team_members_team_user_uk unique (team_id, user_id)
);

create index team_members_team_idx on team_members (team_id);
create index team_members_user_idx on team_members (user_id);

-- ---------------------------------------------------------------------------
-- Projects & sections
-- ---------------------------------------------------------------------------

create table projects (
    id              uuid        primary key,
    organization_id uuid        not null references organizations (id),
    team_id         uuid        references teams (id),
    owner_id        uuid        references users (id),
    name            text        not null,
    description     text,
    color           text,
    default_view    text        not null default 'list' check (default_view in ('list', 'board')),
    archived        boolean     not null default false,
    created_at      timestamptz not null default now()
);

create index projects_org_idx on projects (organization_id);
create index projects_team_idx on projects (team_id);
create index projects_owner_idx on projects (owner_id);

create table sections (
    id              uuid        primary key,
    organization_id uuid        not null references organizations (id),
    project_id      uuid        not null references projects (id),
    name            text         not null,
    sort_key        varchar(255) not null,
    created_at      timestamptz  not null default now()
);

create index sections_project_sort_idx on sections (project_id, sort_key);

-- ---------------------------------------------------------------------------
-- Tasks
-- ---------------------------------------------------------------------------

create table tasks (
    id              uuid        primary key,
    organization_id uuid        not null references organizations (id),
    project_id      uuid        not null references projects (id),
    section_id      uuid        references sections (id),
    assignee_id     uuid        references users (id),
    title           text        not null,
    description     text,
    priority        text        not null default 'none' check (priority in ('none', 'low', 'medium', 'high')),
    due_date        date,
    completed       boolean     not null default false,
    completed_at    timestamptz,
    created_by      uuid        references users (id),
    sort_key        varchar(255) not null,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index tasks_project_section_sort_idx on tasks (project_id, section_id, sort_key);
create index tasks_project_completed_due_idx on tasks (project_id, completed, due_date);
create index tasks_assignee_completed_due_idx on tasks (assignee_id, completed, due_date);
