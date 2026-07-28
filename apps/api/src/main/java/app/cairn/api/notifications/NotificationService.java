package app.cairn.api.notifications;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.comments.event.CommentEvents;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.projects.Project;
import app.cairn.api.projects.ProjectService;
import app.cairn.api.projects.event.ProjectStatusUpdateEvents;
import app.cairn.api.portfolios.PortfolioService;
import app.cairn.api.tasks.Task;
import app.cairn.api.tasks.TaskService;
import app.cairn.api.tasks.event.TaskEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * In-app notification fan-out (F1). Listeners consume the same domain events the {@code activity} module
 * records and create one notification per recipient. Fan-out is <strong>in-process</strong> (no worker
 * container, no queue): listeners run on {@link TransactionPhase#AFTER_COMMIT}, i.e. synchronously after
 * the triggering transaction commits but still inside the HTTP request, so the request-scoped
 * {@code OrgContext} is live and every write goes through the org-filter seam. Running after commit means
 * a notification is created only for a change that actually persisted, and a fan-out failure can never
 * roll back the user's action. ({@code @Async} was avoided deliberately: an async thread has no
 * request scope, so it could not honour the seam.)
 *
 * <p><strong>Never self-notify</strong> — a recipient equal to the actor is always skipped.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final TaskService tasks;
    private final ProjectService projects;
    private final PortfolioService portfolios;
    private final MembershipService memberships;
    private final ObjectMapper mapper;

    public NotificationService(
            NotificationRepository notifications,
            TaskService tasks,
            ProjectService projects,
            PortfolioService portfolios,
            MembershipService memberships,
            ObjectMapper mapper) {
        this.notifications = notifications;
        this.tasks = tasks;
        this.projects = projects;
        this.portfolios = portfolios;
        this.memberships = memberships;
        this.mapper = mapper;
    }

    // --- read API (controller) ----------------------------------------------

    /** A recipient's notifications, newest first, keyset-paginated. */
    public List<Notification> list(UUID recipientId, UUID afterId, int limit) {
        return notifications.pageByRecipient(recipientId, afterId, limit);
    }

    public int unreadCount(UUID recipientId) {
        return notifications.unreadCount(recipientId);
    }

    /** Mark a single notification read. 404 if it does not exist or is not the caller's. */
    public void markRead(AuthPrincipal caller, UUID id) {
        if (!notifications.markRead(id, caller.userId())) {
            throw NotFoundException.of("Notification");
        }
    }

    public int markAllRead(UUID recipientId) {
        return notifications.markAllRead(recipientId);
    }

    // --- fan-out listeners (F1) ----------------------------------------------

    /** (1) A task created already assigned to someone else notifies that assignee. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskCreated(TaskEvents.TaskCreated e) {
        notifyAssignment(e.assigneeId(), e.actorId(), e.taskId(), e.projectId(), e.title());
    }

    /** (1) Reassigning a task notifies the new assignee. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAssigneeChanged(TaskEvents.TaskAssigneeChanged e) {
        notifyAssignment(e.newAssigneeId(), e.actorId(), e.taskId(), e.projectId(), e.title());
    }

    /** (5) A due-date change on a task notifies its assignee. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onDueChanged(TaskEvents.TaskDueChanged e) {
        if (isOther(e.assigneeId(), e.actorId())) {
            create(
                    e.assigneeId(),
                    e.actorId(),
                    NotificationType.TASK_DUE_CHANGED,
                    NotificationType.RESOURCE_TASK,
                    e.taskId(),
                    payload(e.actorId(), "changed the due date of", e.title(), dueSnippet(e.newDue()), e.projectId()));
        }
    }

    /** (6) A task completed by someone other than its assignee notifies the assignee. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCompletion(TaskEvents.TaskCompletionChanged e) {
        if (e.completed() && isOther(e.assigneeId(), e.actorId())) {
            create(
                    e.assigneeId(),
                    e.actorId(),
                    NotificationType.TASK_COMPLETED,
                    NotificationType.RESOURCE_TASK,
                    e.taskId(),
                    payload(e.actorId(), "completed your task", e.title(), null, e.projectId()));
        }
    }

    /**
     * (2) @mentions and (3) a comment on a task the recipient is assigned to or created. A user who is
     * both @mentioned and the assignee/creator gets a single notification (the @mention), never two.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onComment(CommentEvents.CommentCreated e) {
        Task task = safeTask(e.taskId());
        String title = task == null ? null : task.title();
        Set<UUID> notified = new LinkedHashSet<>();

        // (2) mentions
        for (UUID mentioned : e.mentionedUserIds()) {
            if (isOther(mentioned, e.authorId()) && notified.add(mentioned)) {
                create(
                        mentioned,
                        e.authorId(),
                        NotificationType.COMMENT_MENTION,
                        NotificationType.RESOURCE_TASK,
                        e.taskId(),
                        payload(e.authorId(), "mentioned you in a comment", title, e.snippet(), e.projectId()));
            }
        }

        // (3) commented on your task (assignee or creator), excluding anyone already @mentioned
        if (task != null) {
            for (UUID recipient : new UUID[] {task.assigneeId(), task.createdBy()}) {
                if (isOther(recipient, e.authorId()) && notified.add(recipient)) {
                    create(
                            recipient,
                            e.authorId(),
                            NotificationType.COMMENT_ADDED,
                            NotificationType.RESOURCE_TASK,
                            e.taskId(),
                            payload(e.authorId(), "commented on your task", title, e.snippet(), e.projectId()));
                }
            }
        }
    }

    /** (4) A status update notifies the project owner and every containing portfolio's owner. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStatusUpdate(ProjectStatusUpdateEvents.StatusUpdatePosted e) {
        Project project = safeProject(e.projectId());
        String projectName = project == null ? null : project.name();
        Set<UUID> recipients = new LinkedHashSet<>();
        if (project != null && project.ownerId() != null) {
            recipients.add(project.ownerId());
        }
        recipients.addAll(portfolios.ownerIdsOfPortfoliosContaining(e.projectId()));

        for (UUID recipient : recipients) {
            if (isOther(recipient, e.actorId())) {
                ObjectNode node = payloadNode(e.actorId(), "posted a status update", projectName, e.snippet(), null);
                node.put("status", e.status());
                create(
                        recipient,
                        e.actorId(),
                        NotificationType.STATUS_UPDATE,
                        NotificationType.RESOURCE_PROJECT,
                        e.projectId(),
                        node.toString());
            }
        }
    }

    // --- helpers -------------------------------------------------------------

    private void notifyAssignment(UUID assigneeId, UUID actorId, UUID taskId, UUID projectId, String title) {
        if (isOther(assigneeId, actorId)) {
            create(
                    assigneeId,
                    actorId,
                    NotificationType.TASK_ASSIGNED,
                    NotificationType.RESOURCE_TASK,
                    taskId,
                    payload(actorId, "assigned you a task", title, null, projectId));
        }
    }

    /** Insert one notification, swallowing any failure so fan-out never disrupts the request. */
    private void create(
            UUID recipientId, UUID actorId, String type, String resourceType, UUID resourceId, String payloadJson) {
        try {
            notifications.insert(recipientId, type, actorId, payloadJson, resourceType, resourceId);
        } catch (RuntimeException ex) {
            log.warn("notification fan-out failed (type={}, recipient={})", type, recipientId, ex);
        }
    }

    /** True when {@code recipient} is a real user distinct from the actor (the never-self-notify rule). */
    private static boolean isOther(UUID recipient, UUID actor) {
        return recipient != null && !recipient.equals(actor);
    }

    private String actorName(UUID actorId) {
        if (actorId == null) {
            return "Someone";
        }
        return memberships.findByUserId(actorId).map(a -> a.name()).orElse("Someone");
    }

    private String payload(UUID actorId, String verb, String objectTitle, String snippet, UUID projectId) {
        return payloadNode(actorId, verb, objectTitle, snippet, projectId).toString();
    }

    /** Build the self-contained render payload (actor name, verb, object title, snippet, project id). */
    private ObjectNode payloadNode(UUID actorId, String verb, String objectTitle, String snippet, UUID projectId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("actorName", actorName(actorId));
        node.put("verb", verb);
        if (objectTitle != null) {
            node.put("objectTitle", objectTitle);
        }
        if (snippet != null && !snippet.isBlank()) {
            node.put("snippet", snippet);
        }
        if (projectId != null) {
            node.put("projectId", projectId.toString());
        }
        return node;
    }

    private static String dueSnippet(LocalDate newDue) {
        return newDue == null ? "Due date cleared" : "Due " + newDue;
    }

    private Task safeTask(UUID taskId) {
        try {
            return tasks.get(taskId);
        } catch (NotFoundException ex) {
            return null;
        }
    }

    private Project safeProject(UUID projectId) {
        try {
            return projects.get(projectId);
        } catch (NotFoundException ex) {
            return null;
        }
    }
}
