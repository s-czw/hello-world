package app.cairn.api.activity.web;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry in a task's merged activity stream: either a {@code comment} (author + body) or a
 * {@code system} activity event (actor + action + optional diff). Ordered chronologically by
 * {@code createdAt}. {@code id} is a string because comment ids are UUIDs and activity ids are bigserial.
 */
public record ActivityStreamEntry(
        String kind,
        String id,
        UUID actorId,
        String action,
        JsonNode diff,
        String body,
        boolean edited,
        OffsetDateTime editedAt,
        OffsetDateTime createdAt) {

    public static final String KIND_COMMENT = "comment";
    public static final String KIND_SYSTEM = "system";
}
