package app.cairn.api.auth.session;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A row of {@code auth_sessions} needed to validate/rotate a refresh token. */
public record SessionRow(UUID id, UUID userId, OffsetDateTime expiresAt) {}
