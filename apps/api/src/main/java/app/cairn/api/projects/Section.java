package app.cairn.api.projects;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A section row (a named group of tasks within a project), ordered by {@code sortKey}. */
public record Section(UUID id, UUID projectId, String name, String sortKey, OffsetDateTime createdAt) {}
