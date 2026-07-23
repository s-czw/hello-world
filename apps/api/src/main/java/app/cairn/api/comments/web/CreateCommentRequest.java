package app.cairn.api.comments.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /tasks/{id}/comments}. Plain text (D-011); @mentions are parsed server-side. */
public record CreateCommentRequest(@NotBlank @Size(max = 10000) String body) {}
