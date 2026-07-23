package app.cairn.api.tasks.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /tasks/{id}/subtasks}. Plain-text title only; other fields set via PATCH. */
public record CreateSubtaskRequest(@NotBlank @Size(max = 500) String title) {}
