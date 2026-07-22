package app.cairn.api.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body for {@code POST /projects}. Every project belongs to a team; owner defaults to the caller. */
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @Size(max = 32) String color,
        @NotNull UUID teamId,
        UUID ownerId,
        String defaultView) {}
