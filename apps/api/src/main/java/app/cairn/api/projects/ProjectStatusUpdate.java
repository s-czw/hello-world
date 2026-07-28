package app.cairn.api.projects;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry in a project's status-update history (D3). The most recent entry's {@code status} and
 * {@code createdAt} are denormalized onto the project ({@code current_status}/{@code status_updated_at})
 * when it is posted. Body is plain text (D-011).
 */
public record ProjectStatusUpdate(
        UUID id,
        UUID projectId,
        UUID authorId,
        String status,
        String title,
        String body,
        OffsetDateTime createdAt) {}
