package app.cairn.api.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /projects/{id}/sections}. New sections append to the end of the project. */
public record CreateSectionRequest(@NotBlank @Size(max = 200) String name) {}
