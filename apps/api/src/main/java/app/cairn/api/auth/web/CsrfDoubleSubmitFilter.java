package app.cairn.api.auth.web;

import app.cairn.api.auth.session.AuthCookies;
import app.cairn.api.auth.session.CsrfTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Double-submit CSRF defence (arch §6.4). Every state-changing request from an <em>authenticated</em>
 * caller must echo the non-httpOnly {@code cairn_csrf} cookie in the {@code X-CSRF-Token} header; a
 * missing or mismatched header is rejected with a 403 problem+json. A cross-site attacker can make the
 * browser send the cookies but cannot read them, so it cannot produce the header.
 *
 * <p>Two deliberate exemptions:
 *
 * <ul>
 *   <li><b>Unauthenticated requests.</b> There is no session to ride, so there is nothing to forge;
 *       these keep falling through to the 401 entry point rather than turning into a confusing 403.
 *       (This filter runs after {@link JwtCookieAuthFilter}, so the principal is already resolved.)
 *   <li><b>The pre-auth endpoints</b> that establish a session in the first place — bootstrap, login,
 *       refresh and invite-accept — which the caller reaches before it can hold a token.
 * </ul>
 */
@Component
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    /** Endpoints that mint/rotate a session; the caller has no token to present yet. */
    private static final Set<String> PRE_AUTH_PATHS = Set.of(
            "/api/v1/auth/bootstrap",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/invites/accept");

    private static final Set<String> SAFE_METHODS =
            Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (requiresCsrfToken(request)
                && !CsrfTokenService.matches(cookieValue(request), request.getHeader(CsrfTokenService.HEADER))) {
            ProblemDetailResponder.write(response, HttpStatus.FORBIDDEN, "Invalid or missing CSRF token");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean requiresCsrfToken(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (PRE_AUTH_PATHS.contains(request.getRequestURI())) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.CSRF.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
