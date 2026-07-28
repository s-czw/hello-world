package app.cairn.api.tasks.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain events published by {@code TaskService} within the request transaction. Two audiences consume
 * them: the {@code activity} module writes an {@code activity_log} row for each (this phase), and the
 * {@code notifications} module fans out in-app notifications (next phase). Publishing them now keeps the
 * notify phase additive — it only registers listeners. Events are synchronous (same tx, same request), so
 * the request-scoped {@code OrgContext} is live for any listener that touches the DB via the org seam.
 */
public final class TaskEvents {

    private TaskEvents() {}

    /** A task (or subtask) was created. */
    public record TaskCreated(UUID taskId, UUID projectId, UUID actorId, UUID assigneeId, String title, boolean subtask) {}

    /** A task's completion flag changed. {@code completed} is the new value; the notify phase only reacts to true. */
    public record TaskCompletionChanged(
            UUID taskId, UUID projectId, UUID actorId, UUID assigneeId, String title, boolean completed) {}

    /** A task's assignee changed (either id may be null). */
    public record TaskAssigneeChanged(
            UUID taskId, UUID projectId, UUID actorId, UUID oldAssigneeId, UUID newAssigneeId, String title) {}

    /** A task's due date changed (either date may be null). */
    public record TaskDueChanged(
            UUID taskId, UUID projectId, UUID actorId, UUID assigneeId, LocalDate oldDue, LocalDate newDue, String title) {}

    /** A task moved to a different section (either section may be null). */
    public record TaskSectionChanged(
            UUID taskId, UUID projectId, UUID actorId, UUID oldSectionId, UUID newSectionId, String title) {}

    /**
     * A task is about to be deleted. Published <em>before</em> the task row is removed so listeners can
     * clean up their FK-referencing rows (comments, attachments + files, activity) in the same tx.
     */
    public record TaskDeleting(UUID taskId) {}
}
