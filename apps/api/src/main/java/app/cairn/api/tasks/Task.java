package app.cairn.api.tasks;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A task row. Single nullable assignee (D-009); no task-level status (D-009); plain-text fields (D-011). */
public record Task(
        UUID id,
        UUID projectId,
        UUID sectionId,
        UUID assigneeId,
        String title,
        String description,
        String priority,
        LocalDate dueDate,
        boolean completed,
        OffsetDateTime completedAt,
        UUID createdBy,
        String sortKey,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
