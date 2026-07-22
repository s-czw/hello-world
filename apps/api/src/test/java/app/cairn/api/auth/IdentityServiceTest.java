package app.cairn.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.cairn.api.auth.provider.IdentityClaims;
import app.cairn.api.auth.provider.IdentityProvider;
import app.cairn.api.auth.ratelimit.LoginRateLimiter;
import app.cairn.api.auth.session.SessionOutcome;
import app.cairn.api.auth.session.SessionService;
import app.cairn.api.core.error.ApiException;
import app.cairn.api.orgs.OrgService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;

class IdentityServiceTest {

    private OrgService orgService;
    private SessionService sessionService;
    private LoginRateLimiter rateLimiter;
    private IdentityProvider localProvider;
    private IdentityService identityService;

    @BeforeEach
    void setUp() {
        orgService = mock(OrgService.class);
        sessionService = mock(SessionService.class);
        rateLimiter = mock(LoginRateLimiter.class);
        localProvider = mock(IdentityProvider.class);
        when(localProvider.key()).thenReturn("local");
        when(localProvider.enabled()).thenReturn(true);
        identityService = new IdentityService(orgService, sessionService, rateLimiter, List.of(localProvider));
    }

    @Test
    void loginRateLimitedYields429AndNeverCallsProvider() {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> identityService.login("a@b.com", "pw", "1.1.1.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        verify(localProvider, never()).authenticate(any());
        verify(sessionService, never()).issueFor(any());
    }

    @Test
    void loginPropagatesBadCredentials() {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(localProvider.authenticate(any())).thenThrow(new BadCredentialsException("nope"));

        assertThatThrownBy(() -> identityService.login("a@b.com", "pw", "1.1.1.1"))
                .isInstanceOf(BadCredentialsException.class);
        verify(sessionService, never()).issueFor(any());
    }

    @Test
    void loginSuccessIssuesSessionForResolvedUser() {
        UUID userId = UUID.randomUUID();
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(localProvider.authenticate(any()))
                .thenReturn(new IdentityClaims("local", userId.toString(), "a@b.com", true, "Ada"));
        SessionOutcome outcome = new SessionOutcome(List.of(), null);
        when(sessionService.issueFor(userId)).thenReturn(outcome);

        SessionOutcome result = identityService.login("a@b.com", "pw", "1.1.1.1");

        assertThat(result).isSameAs(outcome);
        verify(sessionService).issueFor(userId);
    }

    @Test
    void loginFailsWhenLocalProviderDisabled() {
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(localProvider.enabled()).thenReturn(false);

        assertThatThrownBy(() -> identityService.login("a@b.com", "pw", "1.1.1.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void bootstrapDelegatesThenIssuesSessionForAdmin() {
        UUID adminId = UUID.randomUUID();
        when(orgService.bootstrap(eq("Org"), eq("Ada"), eq("a@b.com"), eq("password12")))
                .thenReturn(new app.cairn.api.orgs.BootstrapResult(
                        UUID.randomUUID(), "Org", adminId, "Ada", "a@b.com"));
        SessionOutcome outcome = new SessionOutcome(List.of(), null);
        when(sessionService.issueFor(adminId)).thenReturn(outcome);

        SessionOutcome result = identityService.bootstrap("Org", "Ada", "a@b.com", "password12");

        assertThat(result).isSameAs(outcome);
        verify(sessionService).issueFor(adminId);
    }
}
