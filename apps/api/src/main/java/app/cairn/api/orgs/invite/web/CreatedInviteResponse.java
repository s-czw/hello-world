package app.cairn.api.orgs.invite.web;

import app.cairn.api.orgs.invite.InviteService.CreatedInvite;
import java.time.OffsetDateTime;
import java.util.UUID;

/** POST /invites result: metadata plus the one-time copy-paste accept link (shown only here). */
public record CreatedInviteResponse(UUID id, String email, OffsetDateTime expiresAt, String acceptUrl) {

    public static CreatedInviteResponse from(CreatedInvite created) {
        return new CreatedInviteResponse(
                created.id(), created.email(), created.expiresAt(), created.acceptUrl());
    }
}
