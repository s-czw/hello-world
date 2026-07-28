package app.cairn.api.comments;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.comments.event.CommentEvents;
import app.cairn.api.core.error.ForbiddenException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.tasks.Task;
import app.cairn.api.tasks.TaskService;
import app.cairn.api.tasks.event.TaskEvents;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comments on tasks (C3). Plain text only (D-011). Creating a comment parses @mentions, resolves them to
 * real org members, and publishes {@link CommentEvents.CommentCreated} (the notify phase fans out from it).
 * Editing sets {@code edited_at}. Authors edit/delete their own comments; admins may also delete (moderation).
 */
@Service
public class CommentService {

    private static final int SNIPPET_MAX = 140;

    private final CommentRepository comments;
    private final TaskService tasks;
    private final MembershipService memberships;
    private final ApplicationEventPublisher events;

    public CommentService(
            CommentRepository comments,
            TaskService tasks,
            MembershipService memberships,
            ApplicationEventPublisher events) {
        this.comments = comments;
        this.tasks = tasks;
        this.memberships = memberships;
        this.events = events;
    }

    /** A task's comments, oldest first, cursor-paginated. Asserts the task is visible in this org (404). */
    public List<Comment> list(UUID taskId, UUID afterId, int limit) {
        tasks.get(taskId);
        return comments.pageByTask(taskId, afterId, limit);
    }

    /** All of a task's comments (used by the merged activity stream). */
    public List<Comment> listAll(UUID taskId) {
        return comments.listByTask(taskId);
    }

    /** A single comment in this org (404-for-inaccessible). Used when attaching a file to a comment. */
    public Comment get(UUID commentId) {
        return comments.findById(commentId).orElseThrow(() -> NotFoundException.of("Comment"));
    }

    @Transactional
    public Comment create(AuthPrincipal caller, UUID taskId, String body) {
        Task task = tasks.get(taskId); // 404 if the task is not in this org
        String trimmed = body.trim();
        UUID id = comments.insert(taskId, caller.userId(), trimmed);
        Comment created = comments.findById(id).orElseThrow();
        Set<UUID> mentioned = resolveMentions(MentionParser.parse(trimmed));
        events.publishEvent(new CommentEvents.CommentCreated(
                id, taskId, task.projectId(), caller.userId(), snippet(trimmed), mentioned));
        return created;
    }

    @Transactional
    public Comment update(AuthPrincipal caller, UUID commentId, String body) {
        Comment c = comments.findById(commentId).orElseThrow(() -> NotFoundException.of("Comment"));
        if (!caller.userId().equals(c.authorId())) {
            throw new ForbiddenException("You can only edit your own comment");
        }
        comments.updateBody(commentId, body.trim(), OffsetDateTime.now());
        return comments.findById(commentId).orElseThrow();
    }

    @Transactional
    public void delete(AuthPrincipal caller, UUID commentId) {
        Comment c = comments.findById(commentId).orElseThrow(() -> NotFoundException.of("Comment"));
        boolean allowed = caller.isAdmin() || caller.userId().equals(c.authorId());
        if (!allowed) {
            throw new ForbiddenException("You can only delete your own comment");
        }
        comments.delete(commentId);
    }

    /** Drop a task's comments when the task is being deleted (sync, same tx). */
    @EventListener
    void onTaskDeleting(TaskEvents.TaskDeleting e) {
        comments.deleteByTask(e.taskId());
    }

    /** Keep only mention candidates that resolve to a real member of this org. */
    private Set<UUID> resolveMentions(MentionParser.Mentions mentions) {
        Set<UUID> resolved = new LinkedHashSet<>();
        for (UUID id : mentions.userIds()) {
            memberships.findByUserId(id).ifPresent(a -> resolved.add(a.userId()));
        }
        for (String email : mentions.emails()) {
            memberships.findByEmail(email).ifPresent(a -> resolved.add(a.userId()));
        }
        return resolved;
    }

    private static String snippet(String body) {
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SNIPPET_MAX ? oneLine : oneLine.substring(0, SNIPPET_MAX) + "…";
    }
}
