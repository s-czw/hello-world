package app.cairn.api.projects.web;

import app.cairn.api.projects.ProjectStatusUpdate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A project status update as returned to clients. */
public record StatusUpdateResponse(
        UUID id,
        UUID projectId,
        UUID authorId,
        String status,
        String title,
        String body,
        OffsetDateTime createdAt) {

    public static StatusUpdateResponse from(ProjectStatusUpdate u) {
        return new StatusUpdateResponse(
                u.id(), u.projectId(), u.authorId(), u.status(), u.title(), u.body(), u.createdAt());
    }
}
