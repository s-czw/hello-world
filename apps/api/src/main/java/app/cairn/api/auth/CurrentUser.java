package app.cairn.api.auth;

import app.cairn.api.core.error.ApiException;
import app.cairn.api.core.error.ForbiddenException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Accessor for the authenticated {@link AuthPrincipal} and coarse RBAC checks. */
@Component
public class CurrentUser {

    public Optional<AuthPrincipal> optional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public AuthPrincipal require() {
        return optional().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    /** Require an admin caller; 403 otherwise. */
    public AuthPrincipal requireAdmin() {
        AuthPrincipal principal = require();
        if (!principal.isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }
        return principal;
    }
}
