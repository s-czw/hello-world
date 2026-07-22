package app.cairn.api.tasks;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A partial task update. Each {@code *Set} flag says whether the caller supplied that field, so an
 * explicit {@code null} clears a nullable column (unassign, clear due date) while an omitted field is
 * left untouched. Built by {@link TaskService} from the web DTO; mapped to jOOQ in {@link TaskRepository}.
 */
public record TaskUpdate(
        boolean titleSet, String title,
        boolean descriptionSet, String description,
        boolean assigneeSet, UUID assigneeId,
        boolean dueSet, LocalDate dueDate,
        boolean prioritySet, String priority,
        boolean completedSet, Boolean completed,
        boolean completedAtSet, OffsetDateTime completedAt) {}
