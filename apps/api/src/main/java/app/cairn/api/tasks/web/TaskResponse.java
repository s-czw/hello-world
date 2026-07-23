package app.cairn.api.tasks.web;

import app.cairn.api.tasks.Task;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A task as returned to clients. */
public record TaskResponse(
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
        UUID parentTaskId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.id(), t.projectId(), t.sectionId(), t.assigneeId(), t.title(), t.description(),
                t.priority(), t.dueDate(), t.completed(), t.completedAt(), t.createdBy(), t.sortKey(),
                t.parentTaskId(), t.createdAt(), t.updatedAt());
    }
}
