package app.cairn.api.notifications;

/**
 * The six in-app notification trigger types (F1). Kept as string constants (stored in the {@code type}
 * column) so the client can branch on them; the human-readable verb lives in each notification's payload.
 */
public final class NotificationType {

    private NotificationType() {}

    /** (1) A task was assigned to the recipient by someone else. */
    public static final String TASK_ASSIGNED = "task_assigned";

    /** (2) The recipient was @mentioned in a comment. */
    public static final String COMMENT_MENTION = "comment_mention";

    /** (3) Someone commented on a task the recipient is assigned to or created. */
    public static final String COMMENT_ADDED = "comment_added";

    /** (4) A status update was posted on a project the recipient owns, or a portfolio they own. */
    public static final String STATUS_UPDATE = "status_update";

    /** (5) The due date changed on a task assigned to the recipient. */
    public static final String TASK_DUE_CHANGED = "task_due_changed";

    /** (6) A task assigned to the recipient was completed by someone else. */
    public static final String TASK_COMPLETED = "task_completed";

    /** Resource-type tag for task-scoped notifications. */
    public static final String RESOURCE_TASK = "task";

    /** Resource-type tag for project-scoped notifications. */
    public static final String RESOURCE_PROJECT = "project";
}
