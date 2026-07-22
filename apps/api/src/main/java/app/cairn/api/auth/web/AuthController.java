package app.cairn.api.auth.web;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.auth.CurrentUser;
import app.cairn.api.auth.IdentityService;
import app.cairn.api.auth.session.AuthCookies;
import app.cairn.api.auth.session.SessionOutcome;
import app.cairn.api.auth.web.dto.BootstrapRequest;
import app.cairn.api.auth.web.dto.BootstrapStatusResponse;
import app.cairn.api.auth.web.dto.LoginRequest;
import app.cairn.api.auth.web.dto.MeResponse;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.orgs.OrgService;
import app.cairn.api.orgs.member.MemberAccount;
import app.cairn.api.orgs.member.MembershipService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auth endpoints (D-020): bootstrap, login, logout, refresh, me. Sessions are cookie-based. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IdentityService identityService;
    private final MembershipService membershipService;
    private final OrgService orgService;
    private final CurrentUser currentUser;

    public AuthController(
            IdentityService identityService,
            MembershipService membershipService,
            OrgService orgService,
            CurrentUser currentUser) {
        this.identityService = identityService;
        this.membershipService = membershipService;
        this.orgService = orgService;
        this.currentUser = currentUser;
    }

    @GetMapping("/bootstrap-status")
    public ApiResponse<BootstrapStatusResponse> bootstrapStatus() {
        return ApiResponse.of(new BootstrapStatusResponse(identityService.needsBootstrap()));
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<ApiResponse<MeResponse>> bootstrap(@Valid @RequestBody BootstrapRequest req) {
        SessionOutcome outcome =
                identityService.bootstrap(req.orgName(), req.adminName(), req.email(), req.password());
        return sessionResponse(outcome, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MeResponse>> login(
            @Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        SessionOutcome outcome = identityService.login(req.email(), req.password(), http.getRemoteAddr());
        return sessionResponse(outcome, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<MeResponse>> refresh(HttpServletRequest http) {
        SessionOutcome outcome = identityService.refresh(cookie(http, AuthCookies.REFRESH));
        return sessionResponse(outcome, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        identityService.logout(cookie(http, AuthCookies.REFRESH));
        ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.noContent();
        for (ResponseCookie clearing : identityService.clearingCookies()) {
            builder.header(HttpHeaders.SET_COOKIE, clearing.toString());
        }
        return builder.build();
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        AuthPrincipal principal = currentUser.require();
        MemberAccount account = membershipService
                .findByUserId(principal.userId())
                .orElseThrow(() -> NotFoundException.of("User"));
        return ApiResponse.of(MeResponse.from(account, orgService.orgName(account.orgId())));
    }

    private ResponseEntity<ApiResponse<MeResponse>> sessionResponse(SessionOutcome outcome, HttpStatus status) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        for (ResponseCookie cookie : outcome.cookies()) {
            builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        MeResponse me = MeResponse.from(outcome.account(), orgService.orgName(outcome.account().orgId()));
        return builder.body(ApiResponse.of(me));
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
