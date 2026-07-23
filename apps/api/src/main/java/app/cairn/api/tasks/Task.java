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
        UUID parentTaskId,
        String subtaskSortKey,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /** True when this task is a subtask (has a parent). One level only (D-009-adjacent, C2). */
    public boolean isSubtask() {
        return parentTaskId != null;
    }
}
