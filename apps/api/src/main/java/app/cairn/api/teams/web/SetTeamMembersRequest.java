package app.cairn.api.teams.web;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Body for {@code PUT /teams/{id}/members} — the full desired member set (replaces the current one). */
public record SetTeamMembersRequest(@NotNull List<UUID> memberIds) {}
