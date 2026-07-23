package app.cairn.api.comments;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A plain-text comment on a task (D-011). {@code editedAt} is non-null once the comment was edited. */
public record Comment(
        UUID id,
        UUID taskId,
        UUID authorId,
        String body,
        OffsetDateTime createdAt,
        OffsetDateTime editedAt) {}
