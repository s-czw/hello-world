package app.cairn.api.projects;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A project row. Every project belongs to exactly one team (spec §3). M2 adds schedule dates and a
 * denormalized current status (kept in step with the latest {@code project_status_updates} row).
 */
public record Project(
        UUID id,
        UUID teamId,
        UUID ownerId,
        String name,
        String description,
        String color,
        String defaultView,
        boolean archived,
        LocalDate startDate,
        LocalDate endDate,
        String currentStatus,
        OffsetDateTime statusUpdatedAt,
        OffsetDateTime createdAt) {}
