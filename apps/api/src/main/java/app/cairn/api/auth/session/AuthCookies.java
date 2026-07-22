package app.cairn.api.auth.session;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * Builds the first-party session cookies (arch §4.3): {@code cairn_access} (the JWT) and
 * {@code cairn_refresh} (the opaque rotating token). Both are httpOnly + SameSite=Lax; {@code Secure}
 * is on in production and off in dev (env {@code AUTH_COOKIE_SECURE}). No tokens ever reach JS/localStorage.
 */
public final class AuthCookies {

    public static final String ACCESS = "cairn_access";
    public static final String REFRESH = "cairn_refresh";
    private static final String PATH = "/";

    private AuthCookies() {}

    public static ResponseCookie access(String jwt, long ttlSeconds, boolean secure) {
        return base(ACCESS, jwt, secure).maxAge(Duration.ofSeconds(ttlSeconds)).build();
    }

    public static ResponseCookie refresh(String token, long ttlSeconds, boolean secure) {
        return base(REFRESH, token, secure).maxAge(Duration.ofSeconds(ttlSeconds)).build();
    }

    public static ResponseCookie clearAccess(boolean secure) {
        return base(ACCESS, "", secure).maxAge(Duration.ZERO).build();
    }

    public static ResponseCookie clearRefresh(boolean secure) {
        return base(REFRESH, "", secure).maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String name, String value, boolean secure) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH);
    }
}
