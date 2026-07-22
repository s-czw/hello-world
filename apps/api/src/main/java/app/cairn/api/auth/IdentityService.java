package app.cairn.api.auth;

import app.cairn.api.auth.provider.IdentityClaims;
import app.cairn.api.auth.provider.IdentityProvider;
import app.cairn.api.auth.ratelimit.LoginRateLimiter;
import app.cairn.api.auth.session.SessionOutcome;
import app.cairn.api.auth.session.SessionService;
import app.cairn.api.core.error.ApiException;
import app.cairn.api.orgs.BootstrapResult;
import app.cairn.api.orgs.OrgService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * Auth orchestration facade: first-run bootstrap, password login (rate-limited, provider-based),
 * refresh rotation, and logout. Session issuance is identical regardless of provider (D-020) — this
 * class picks the enabled provider, gets {@link IdentityClaims}, and delegates to {@link SessionService}.
 */
@Service
public class IdentityService {

    private final OrgService orgService;
    private final SessionService sessionService;
    private final LoginRateLimiter rateLimiter;
    private final List<IdentityProvider> providers;

    public IdentityService(
            OrgService orgService,
            SessionService sessionService,
            LoginRateLimiter rateLimiter,
            List<IdentityProvider> providers) {
        this.orgService = orgService;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.providers = providers;
    }

    public boolean needsBootstrap() {
        return orgService.needsBootstrap();
    }

    /** First-run: create the org + admin and immediately issue that admin a session. */
    public SessionOutcome bootstrap(String orgName, String adminName, String email, String password) {
        BootstrapResult result = orgService.bootstrap(orgName, adminName, email, password);
        return sessionService.issueFor(result.adminUserId());
    }

    /** Password login. 429 if rate-limited; 401 on bad credentials (no email/password distinction). */
    public SessionOutcome login(String email, String password, String clientIp) {
        if (!rateLimiter.tryAcquire(clientIp, email)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts; try again shortly");
        }
        IdentityProvider provider = providers.stream()
                .filter(IdentityProvider::enabled)
                .filter(p -> "local".equals(p.key()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Local login is disabled"));

        IdentityClaims claims = provider.authenticate(new IdentityProvider.Credentials(email, password));
        UUID userId;
        try {
            userId = UUID.fromString(claims.subject());
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return sessionService.issueFor(userId);
    }

    public SessionOutcome refresh(String refreshTokenPlain) {
        return sessionService.rotate(refreshTokenPlain);
    }

    public void logout(String refreshTokenPlain) {
        sessionService.revoke(refreshTokenPlain);
    }

    /** Cookies that clear the client-side session (logout). */
    public java.util.List<org.springframework.http.ResponseCookie> clearingCookies() {
        return sessionService.clearingCookies();
    }
}
