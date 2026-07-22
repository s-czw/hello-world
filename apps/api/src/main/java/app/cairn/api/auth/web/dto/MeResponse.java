package app.cairn.api.auth.web.dto;

import app.cairn.api.orgs.member.MemberAccount;
import java.util.UUID;

/** The authenticated caller, returned by bootstrap/login/refresh/me (never includes the password hash). */
public record MeResponse(UUID id, String email, String name, String role, UUID orgId, String orgName) {

    public static MeResponse from(MemberAccount account, String orgName) {
        return new MeResponse(
                account.userId(), account.email(), account.name(), account.role(), account.orgId(), orgName);
    }
}
