package app.cairn.api.projects.web;

import app.cairn.api.projects.Project;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A project as returned to clients. */
public record ProjectResponse(
        UUID id,
        UUID teamId,
        UUID ownerId,
        String name,
        String description,
        String color,
        String defaultView,
        boolean archived,
        OffsetDateTime createdAt) {

    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.id(), p.teamId(), p.ownerId(), p.name(), p.description(), p.color(),
                p.defaultView(), p.archived(), p.createdAt());
    }
}
