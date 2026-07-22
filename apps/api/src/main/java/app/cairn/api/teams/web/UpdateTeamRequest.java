package app.cairn.api.teams.web;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code PATCH /teams/{id}} — both fields optional; {@code null} means "leave unchanged".
 * (Clearing a team description is not a documented M1 need, so the simple convention applies here.)
 */
public record UpdateTeamRequest(@Size(max = 200) String name, @Size(max = 2000) String description) {}
