package app.cairn.api.orgs.invite;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A persisted invite (token hash is never surfaced beyond the repository). */
public record InviteRow(
        UUID id,
        String email,
        UUID invitedBy,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime createdAt) {

    public boolean isPending(OffsetDateTime now) {
        return revokedAt == null && acceptedAt == null && expiresAt.isAfter(now);
    }
}
