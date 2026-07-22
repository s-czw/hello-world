package app.cairn.api.auth;

import app.cairn.api.orgs.member.Role;
import java.util.UUID;

/**
 * The authenticated caller, derived from a verified {@code cairn_access} token and stored as the
 * Spring Security principal. Carries just enough to authorize and scope a request.
 */
public record AuthPrincipal(UUID userId, UUID orgId, String role, String email) {

    public boolean isAdmin() {
        return Role.ADMIN.equals(role);
    }
}
