package app.cairn.api.teams.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /teams}. */
public record CreateTeamRequest(
        @NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {}
