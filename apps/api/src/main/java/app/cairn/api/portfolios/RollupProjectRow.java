package app.cairn.api.portfolios;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of the portfolio roll-up: a member project with its owner, schedule, denormalized status, and
 * aggregated task counts. Produced entirely by the single roll-up aggregate query (no N+1).
 */
public record RollupProjectRow(
        UUID projectId,
        String name,
        String color,
        UUID ownerId,
        String ownerName,
        LocalDate startDate,
        LocalDate endDate,
        String currentStatus,
        OffsetDateTime statusUpdatedAt,
        int tasksTotal,
        int tasksComplete) {}
