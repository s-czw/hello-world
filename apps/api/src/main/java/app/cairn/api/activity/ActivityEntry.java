package app.cairn.api.activity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A system activity-log row (task created/completed/assignee/due/section changes). {@code diffJson} is
 * the raw jsonb text ({@code null} when the action needs no diff), parsed into an object at the edge.
 */
public record ActivityEntry(
        long id,
        UUID actorId,
        String action,
        String resourceType,
        UUID resourceId,
        String diffJson,
        OffsetDateTime createdAt) {}
