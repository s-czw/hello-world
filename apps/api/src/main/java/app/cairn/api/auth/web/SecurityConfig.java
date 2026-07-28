package app.cairn.api.auth.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The multi-provider security chain (D-020). Stateless (JWT cookie, no server session). Spring's own
 * CSRF machinery stays off — it is session/token-repository shaped — and is replaced by the stateless
 * double-submit check in {@link CsrfDoubleSubmitFilter} (arch §6.4), layered on top of the
 * {@code SameSite=Lax} session cookies. Permits first-run, login, refresh, invite-accept, health, and
 * API docs; everything else requires a valid access cookie. Auth failures render as RFC 9457
 * problem+json (401/403).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtCookieAuthFilter jwtCookieAuthFilter,
            CsrfDoubleSubmitFilter csrfDoubleSubmitFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/healthz", "/readyz")
                        .permitAll()
                        .requestMatchers("/api/docs/**", "/api/docs", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/bootstrap",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/invites/accept")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/bootstrap-status")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Runs straight after authentication so the double-submit check can tell an
                // authenticated mutation (403 on a bad token) from an anonymous one (401).
                .addFilterAfter(csrfDoubleSubmitFilter, JwtCookieAuthFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                ProblemDetailResponder.write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, deniedException) ->
                ProblemDetailResponder.write(response, HttpStatus.FORBIDDEN, "Access denied");
    }
}
