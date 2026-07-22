package app.cairn.api.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code PATCH /sections/{id}} (rename). */
public record UpdateSectionRequest(@NotBlank @Size(max = 200) String name) {}
