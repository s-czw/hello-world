package app.cairn.api.projects;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A project row. Every project belongs to exactly one team (spec §3). */
public record Project(
        UUID id,
        UUID teamId,
        UUID ownerId,
        String name,
        String description,
        String color,
        String defaultView,
        boolean archived,
        OffsetDateTime createdAt) {}
