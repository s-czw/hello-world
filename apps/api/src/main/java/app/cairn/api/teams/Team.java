package app.cairn.api.teams;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A team row (a named group that owns projects). Members live in {@code team_members}. */
public record Team(UUID id, String name, String description, OffsetDateTime createdAt) {}
