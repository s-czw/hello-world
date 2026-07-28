package app.cairn.api.auth.web;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.auth.jwt.JwtException;
import app.cairn.api.auth.jwt.JwtService;
import app.cairn.api.auth.session.AuthCookies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from the {@code cairn_access} JWT cookie. A valid token installs an
 * {@link AuthPrincipal} into the security context; a missing/invalid token simply leaves the request
 * unauthenticated so the entry point can decide (permit-listed paths still proceed).
 */
@Component
public class JwtCookieAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtCookieAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = readAccessCookie(request);
            if (token != null) {
                try {
                    JwtService.AccessClaims claims = jwt.verify(token);
                    AuthPrincipal principal = new AuthPrincipal(
                            claims.userId(), claims.orgId(), claims.role(), claims.email());
                    var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + claims.role().toUpperCase(Locale.ROOT)));
                    var authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (JwtException ignored) {
                    // Invalid/expired token → remain unauthenticated.
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static String readAccessCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.ACCESS.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
