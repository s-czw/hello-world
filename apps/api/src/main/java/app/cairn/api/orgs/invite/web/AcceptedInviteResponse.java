package app.cairn.api.orgs.invite.web;

import app.cairn.api.orgs.invite.InviteService.AcceptedInvite;
import java.util.UUID;

/** POST /invites/accept result: the newly created member (they then log in separately). */
public record AcceptedInviteResponse(UUID userId, String email) {

    public static AcceptedInviteResponse from(AcceptedInvite accepted) {
        return new AcceptedInviteResponse(accepted.userId(), accepted.email());
    }
}
