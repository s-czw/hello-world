package app.cairn.api.comments.web;

import app.cairn.api.comments.Comment;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A comment as returned to clients. {@code edited} is true once it has been edited. */
public record CommentResponse(
        UUID id,
        UUID taskId,
        UUID authorId,
        String body,
        boolean edited,
        OffsetDateTime editedAt,
        OffsetDateTime createdAt) {

    public static CommentResponse from(Comment c) {
        return new CommentResponse(
                c.id(), c.taskId(), c.authorId(), c.body(), c.editedAt() != null, c.editedAt(), c.createdAt());
    }
}
