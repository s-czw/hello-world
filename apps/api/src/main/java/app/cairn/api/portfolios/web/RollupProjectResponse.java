package app.cairn.api.portfolios.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One member project's roll-up row as returned to clients. */
public record RollupProjectResponse(
        UUID projectId,
        String name,
        String color,
        UUID ownerId,
        String ownerName,
        LocalDate startDate,
        LocalDate endDate,
        String currentStatus,
        OffsetDateTime statusUpdatedAt,
        boolean stale,
        int tasksTotal,
        int tasksComplete) {}
