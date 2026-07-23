package app.cairn.api.comments.event;

import java.util.Set;
import java.util.UUID;

/**
 * Domain events published by {@code CommentService}. The {@code notifications} module (next phase)
 * consumes {@link CommentCreated} to fan out "@mentioned" and "commented on your task" notifications;
 * publishing it now keeps that phase additive.
 */
public final class CommentEvents {

    private CommentEvents() {}

    /**
     * A comment was created. {@code mentionedUserIds} are already resolved to real org members (the author
     * may be among them; the notify phase excludes self-notifications). {@code snippet} is a short plain-text
     * preview for a self-contained notification payload.
     */
    public record CommentCreated(
            UUID commentId,
            UUID taskId,
            UUID projectId,
            UUID authorId,
            String snippet,
            Set<UUID> mentionedUserIds) {}
}
