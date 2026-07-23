-- Cairn M3 schema (subtasks, comments, activity log, attachments, notifications).
-- EXPAND-ONLY and backward-compatible with V1+V2 (never edit V1/V2): every new column is nullable and
-- every new table is additive, so a V2 api keeps working against a V3 database during a rolling deploy.
-- Still single-org (D-028): every new tenant table carries organization_id so the org-filter seam
-- applies uniformly and RLS can drop in as a second wall at commercialization (not built in M3).
-- IDs are UUIDv7 minted in app code (no DB default), except activity_log which uses a bigserial (it is a
-- high-volume append-only stream, never addressed by id from clients). Timestamps are timestamptz (UTC).
-- Indexed string columns are varchar(n) (jOOQ's H2-based DDL simulation can't index text/CLOB).

-- ---------------------------------------------------------------------------
-- Subtasks (C2) — one level only.
-- A task may have a parent (parent_task_id) OR be a parent, never both: the "no subtask of a subtask"
-- rule is enforced in TaskService (409). subtask_sort_key orders siblings under one parent.
-- ---------------------------------------------------------------------------

alter table tasks add column parent_task_id uuid references tasks (id);
alter table tasks add column subtask_sort_key varchar(255);

create index tasks_parent_sort_idx on tasks (parent_task_id, subtask_sort_key);

-- ---------------------------------------------------------------------------
-- Comments (C3) — plain text (D-011); edited_at marks an edited comment.
-- ---------------------------------------------------------------------------

create table comments (
    id              uuid         primary key,
    organization_id uuid         not null references organizations (id),
    task_id         uuid         not null references tasks (id),
    author_id       uuid         references users (id),
    body            text         not null,
    created_at      timestamptz  not null default now(),
    edited_at       timestamptz
);

create index comments_task_created_idx on comments (task_id, created_at);

-- ---------------------------------------------------------------------------
-- Attachments (C4) — metadata row; bytes live on the configured StorageProvider (local disk in M3).
-- comment_id is nullable: an attachment hangs off a task directly, or off a comment on that task.
-- ---------------------------------------------------------------------------

create table attachments (
    id              uuid         primary key,
    organization_id uuid         not null references organizations (id),
    task_id         uuid         not null references tasks (id),
    comment_id      uuid         references comments (id) on delete set null,
    uploaded_by     uuid         references users (id),
    file_name       text         not null,
    storage_key     text         not null,
    content_type    text,
    size_bytes      bigint       not null,
    created_at      timestamptz  not null default now()
);

create index attachments_task_idx on attachments (task_id);

-- ---------------------------------------------------------------------------
-- Activity log (C3) — append-only system events (task created/completed/assignee/due/section changes),
-- written via domain events. Merged with comments at read time for the task activity stream.
-- ---------------------------------------------------------------------------

create table activity_log (
    id              bigserial    primary key,
    organization_id uuid         not null references organizations (id),
    actor_id        uuid         references users (id),
    action          text         not null,
    resource_type   varchar(64)  not null,
    resource_id     uuid         not null,
    diff            jsonb,
    created_at      timestamptz  not null default now()
);

create index activity_log_resource_idx on activity_log (resource_type, resource_id, created_at desc);

-- ---------------------------------------------------------------------------
-- Notifications (F1) — the table exists in M3; in-process fan-out + the read/list endpoints land in the
-- next phase. payload jsonb carries a self-contained render (actor name, verb, object title, snippet).
-- ---------------------------------------------------------------------------

create table notifications (
    id              uuid         primary key,
    organization_id uuid         not null references organizations (id),
    recipient_id    uuid         not null references users (id),
    type            text         not null,
    actor_id        uuid         references users (id),
    payload         jsonb,
    resource_type   text,
    resource_id     uuid,
    read            boolean      not null default false,
    created_at      timestamptz  not null default now()
);

create index notifications_recipient_idx on notifications (recipient_id, read, created_at desc);
create index notifications_created_idx on notifications (created_at);
