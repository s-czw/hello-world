package app.cairn.api.auth.session;

import app.cairn.api.core.security.Tokens;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Owns the {@code cairn_csrf} double-submit token (arch §6.4). The session cookies are already
 * {@code SameSite=Lax}, so this is defence-in-depth: a fresh 256-bit token is planted in a
 * non-httpOnly cookie whenever a session is established, same-origin JS echoes it in the
 * {@code X-CSRF-Token} header, and {@code CsrfDoubleSubmitFilter} requires the two to match on every
 * state-changing request. The token is stateless — nothing is stored server-side; a cross-site
 * attacker can ride the cookie but cannot read it to forge the header.
 */
@Service
public class CsrfTokenService {

    /** Header the client must echo the {@code cairn_csrf} cookie value in. */
    public static final String HEADER = "X-CSRF-Token";

    private final long ttlSeconds;
    private final boolean cookieSecure;

    public CsrfTokenService(
            @Value("${cairn.auth.jwt.refresh-ttl-seconds}") long ttlSeconds,
            @Value("${cairn.auth.cookie.secure}") boolean cookieSecure) {
        this.ttlSeconds = ttlSeconds;
        this.cookieSecure = cookieSecure;
    }

    /** A fresh CSRF cookie, living as long as the refresh token that accompanies it. */
    public ResponseCookie issueCookie() {
        return AuthCookies.csrf(Tokens.randomToken(), ttlSeconds, cookieSecure);
    }

    /** Plant a fresh CSRF cookie on a response that isn't built from a {@link SessionOutcome}. */
    public void issueTo(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, issueCookie().toString());
    }

    /** The cookie that clears the CSRF token client-side (logout). */
    public ResponseCookie clearingCookie() {
        return AuthCookies.clearCsrf(cookieSecure);
    }

    /** Constant-time equality for the cookie/header comparison; blank values never match. */
    public static boolean matches(String cookieValue, String headerValue) {
        if (cookieValue == null || headerValue == null || cookieValue.isBlank() || headerValue.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                cookieValue.getBytes(StandardCharsets.UTF_8), headerValue.getBytes(StandardCharsets.UTF_8));
    }
}
