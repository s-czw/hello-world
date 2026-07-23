package app.cairn.api.comments.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code PATCH /comments/{id}}. Editing sets the {@code edited_at} marker. */
public record UpdateCommentRequest(@NotBlank @Size(max = 10000) String body) {}
