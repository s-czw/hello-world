package app.cairn.api.orgs.invite.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public payload to accept an invite: the token plus the new user's name + password. */
public record AcceptInviteRequest(
        @NotBlank String token,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(min = 8, max = 200) String password) {}
