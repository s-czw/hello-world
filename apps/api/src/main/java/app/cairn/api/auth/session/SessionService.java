package app.cairn.api.auth.session;

import app.cairn.api.auth.jwt.JwtService;
import app.cairn.api.core.error.ApiException;
import app.cairn.api.core.security.Tokens;
import app.cairn.api.orgs.member.MemberAccount;
import app.cairn.api.orgs.member.MembershipService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and rotates first-party sessions independent of which provider authenticated the caller
 * (arch §4.3): a short-lived access JWT plus a rotating opaque refresh token whose hash is persisted.
 * Rotation is one-time-use — the presented refresh row is revoked and a fresh one minted.
 */
@Service
public class SessionService {

    private final JwtService jwt;
    private final AuthSessionRepository sessions;
    private final MembershipService memberships;
    private final CsrfTokenService csrf;
    private final long refreshTtlSeconds;
    private final boolean cookieSecure;

    public SessionService(
            JwtService jwt,
            AuthSessionRepository sessions,
            MembershipService memberships,
            CsrfTokenService csrf,
            @Value("${cairn.auth.jwt.refresh-ttl-seconds}") long refreshTtlSeconds,
            @Value("${cairn.auth.cookie.secure}") boolean cookieSecure) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.memberships = memberships;
        this.csrf = csrf;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.cookieSecure = cookieSecure;
    }

    /** Mint an access JWT + refresh token for a user and return the cookies to set. */
    @Transactional
    public SessionOutcome issueFor(UUID userId) {
        MemberAccount account = memberships
                .findByUserId(userId)
                .filter(MemberAccount::active)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated"));

        String accessJwt = jwt.issueAccessToken(userId, account.orgId(), account.role(), account.email());
        String refreshPlain = Tokens.randomToken();
        sessions.create(
                userId, Tokens.sha256Hex(refreshPlain), OffsetDateTime.now().plusSeconds(refreshTtlSeconds));

        // A fresh double-submit token accompanies every session issue/rotation (arch §6.4).
        List<ResponseCookie> cookies = List.of(
                AuthCookies.access(accessJwt, jwt.accessTtlSeconds(), cookieSecure),
                AuthCookies.refresh(refreshPlain, refreshTtlSeconds, cookieSecure),
                csrf.issueCookie());
        return new SessionOutcome(cookies, account);
    }

    /** Validate + rotate a refresh token, issuing a new session. 401 if invalid/expired/revoked. */
    @Transactional
    public SessionOutcome rotate(String refreshPlain) {
        if (refreshPlain == null || refreshPlain.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }
        OffsetDateTime now = OffsetDateTime.now();
        SessionRow row = sessions
                .findLiveByTokenHash(Tokens.sha256Hex(refreshPlain), now)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        sessions.revokeById(row.id(), now);
        return issueFor(row.userId());
    }

    /** Revoke the presented refresh token (logout). No-op if absent. */
    @Transactional
    public void revoke(String refreshPlain) {
        if (refreshPlain != null && !refreshPlain.isBlank()) {
            sessions.revokeByTokenHash(Tokens.sha256Hex(refreshPlain), OffsetDateTime.now());
        }
    }

    /** Cookies that clear the session client-side (logout). */
    public List<ResponseCookie> clearingCookies() {
        return List.of(
                AuthCookies.clearAccess(cookieSecure),
                AuthCookies.clearRefresh(cookieSecure),
                csrf.clearingCookie());
    }
}
