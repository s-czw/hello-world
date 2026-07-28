package app.cairn.api.orgs.invite.web;

import app.cairn.api.orgs.invite.InviteRow;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A pending invite as listed to admins (never exposes the token). */
public record InviteResponse(UUID id, String email, OffsetDateTime expiresAt, OffsetDateTime createdAt) {

    public static InviteResponse from(InviteRow row) {
        return new InviteResponse(row.id(), row.email(), row.expiresAt(), row.createdAt());
    }
}
