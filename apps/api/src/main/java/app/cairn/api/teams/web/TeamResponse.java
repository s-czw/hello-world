package app.cairn.api.teams.web;

import app.cairn.api.teams.Team;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A team as returned to clients, with the ids of its members. */
public record TeamResponse(
        UUID id, String name, String description, OffsetDateTime createdAt, List<UUID> memberIds) {

    public static TeamResponse from(Team team, List<UUID> memberIds) {
        return new TeamResponse(
                team.id(), team.name(), team.description(), team.createdAt(),
                memberIds == null ? List.of() : memberIds);
    }
}
