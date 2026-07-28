package app.cairn.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.cairn.api.auth.session.AuthCookies;
import app.cairn.api.auth.session.CsrfTokenService;
import app.cairn.api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The double-submit CSRF guard (arch §6.4): authenticated state-changing requests must echo the
 * {@code cairn_csrf} cookie in {@code X-CSRF-Token}; reads and the pre-auth endpoints are untouched.
 */
class CsrfIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "ada@acme.test";
    private static final String ADMIN_PW = "supersecret1";
    private static final String TEAM_BODY = """
            {"name":"Ops"}""";

    // --- the cookie ----------------------------------------------------------

    @Test
    void sessionIssueSetsAReadableCsrfCookieAndLogoutClearsIt() throws Exception {
        MvcResult boot = bootstrap();
        Cookie csrf = boot.getResponse().getCookie(AuthCookies.CSRF);
        assertThat(csrf).isNotNull();
        assertThat(csrf.getValue()).isNotBlank();
        // Same-origin JS has to read it to echo it back — the one cookie that is not httpOnly.
        assertThat(csrf.isHttpOnly()).isFalse();
        assertThat(csrf.getSecure()).isFalse(); // dev default (AUTH_COOKIE_SECURE=false)
        assertThat(csrf.getPath()).isEqualTo("/");

        // login and refresh mint a fresh token too
        MvcResult login = login(ADMIN_EMAIL, ADMIN_PW);
        String loginToken = cookieValue(login, AuthCookies.CSRF);
        assertThat(loginToken).isNotBlank().isNotEqualTo(csrf.getValue());

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookiesOf(login)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(cookieValue(refreshed, AuthCookies.CSRF)).isNotBlank().isNotEqualTo(loginToken);

        // logout expires it alongside the session cookies
        MvcResult out = mockMvc.perform(post("/api/v1/auth/logout").cookie(cookiesOf(refreshed)))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie cleared = out.getResponse().getCookie(AuthCookies.CSRF);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
    }

    // --- enforcement ---------------------------------------------------------

    @Test
    void mutatingRequestWithoutTokenIsForbidden() throws Exception {
        Cookie[] admin = cookiesOf(bootstrap());

        mockMvc.perform(post("/api/v1/teams")
                        .cookie(admin)
                        .requestAttr(SUPPRESS_CSRF_HEADER, true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEAM_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Invalid or missing CSRF token"));

        // nothing was created
        assertThat(teamNames(admin)).doesNotContain("Ops");
    }

    @Test
    void mutatingRequestWithWrongTokenIsForbidden() throws Exception {
        Cookie[] admin = cookiesOf(bootstrap());

        mockMvc.perform(post("/api/v1/teams")
                        .cookie(admin)
                        .header(CsrfTokenService.HEADER, "not-the-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEAM_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Invalid or missing CSRF token"));

        // an empty header is a mismatch too
        mockMvc.perform(post("/api/v1/teams")
                        .cookie(admin)
                        .header(CsrfTokenService.HEADER, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEAM_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutatingRequestWithCorrectTokenSucceeds() throws Exception {
        MvcResult boot = bootstrap();
        Cookie[] admin = cookiesOf(boot);
        String token = cookieValue(boot, AuthCookies.CSRF);

        MvcResult created = mockMvc.perform(post("/api/v1/teams")
                        .cookie(admin)
                        .header(CsrfTokenService.HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEAM_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        UUID teamId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString())
                        .get("data")
                        .get("id")
                        .asText());

        // PATCH and DELETE go through the same guard
        mockMvc.perform(patch("/api/v1/teams/" + teamId)
                        .cookie(admin)
                        .header(CsrfTokenService.HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ops renamed"}"""))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/teams/" + teamId)
                        .cookie(admin)
                        .requestAttr(SUPPRESS_CSRF_HEADER, true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nope"}"""))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/teams/" + teamId)
                        .cookie(admin)
                        .requestAttr(SUPPRESS_CSRF_HEADER, true))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/teams/" + teamId)
                        .cookie(admin)
                        .header(CsrfTokenService.HEADER, token))
                .andExpect(status().isNoContent());
    }

    @Test
    void readsAreUnaffected() throws Exception {
        Cookie[] admin = cookiesOf(bootstrap());

        mockMvc.perform(get("/api/v1/auth/me").cookie(admin).requestAttr(SUPPRESS_CSRF_HEADER, true))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teams").cookie(admin).requestAttr(SUPPRESS_CSRF_HEADER, true))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").cookie(admin).requestAttr(SUPPRESS_CSRF_HEADER, true))
                .andExpect(status().isOk());
        mockMvc.perform(get("/healthz").requestAttr(SUPPRESS_CSRF_HEADER, true))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedMutationStillAnswers401NotCsrf403() throws Exception {
        bootstrap();

        // No session to ride → the auth entry point owns the response, not the CSRF guard.
        mockMvc.perform(post("/api/v1/teams")
                        .requestAttr(SUPPRESS_CSRF_HEADER, true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEAM_BODY))
                .andExpect(status().isUnauthorized());
    }

    // --- pre-auth endpoints --------------------------------------------------

    @Test
    void preAuthEndpointsWorkWithoutAToken() throws Exception {
        // 1. bootstrap — the caller cannot hold a token yet
        MvcResult boot = mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .requestAttr(SUPPRESS_CSRF_HEADER, true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"orgName":"Acme Ops","adminName":"Ada Admin","email":"%s","password":"%s"}"""
                                        .formatted(ADMIN_EMAIL, ADMIN_PW)))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie[] admin = cookiesOf(boot);

        // 2. login — even while holding stale session cookies, with no header
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(admin)
                        .requestAttr(SUPPRESS_CSRF_HEADER, true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(ADMIN_EMAIL, ADMIN_PW)))
                .andExpect(status().isOk())
                .andReturn();

        // 3. refresh
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookiesOf(login))
                        .requestAttr(SUPPRESS_CSRF_HEADER, true))
                .andExpect(status().isOk())
                .andReturn();

        // 4. invites/accept (the invitee is not a user yet, so has no session and no token)
        MvcResult invite = mockMvc.perform(post("/api/v1/invites")
                        .cookie(cookiesOf(refreshed))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@acme.test"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        String acceptUrl = bodyOf(invite).get("data").get("acceptUrl").asText();
        String token = acceptUrl.substring(acceptUrl.indexOf("token=") + "token=".length());

        MvcResult accepted = mockMvc.perform(post("/api/v1/invites/accept")
                        .requestAttr(SUPPRESS_CSRF_HEADER, true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","name":"Bob Member","password":"memberpass1"}"""
                                .formatted(token)))
                .andExpect(status().isCreated())
                .andReturn();
        // accepting hands the browser a token for the login that follows
        assertThat(cookieValue(accepted, AuthCookies.CSRF)).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/login")
                        .requestAttr(SUPPRESS_CSRF_HEADER, true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@acme.test","password":"memberpass1"}"""))
                .andExpect(status().isOk());
    }

    // --- helpers -------------------------------------------------------------

    private MvcResult bootstrap() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"orgName":"Acme Ops","adminName":"Ada Admin","email":"%s","password":"%s"}"""
                                        .formatted(ADMIN_EMAIL, ADMIN_PW)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String teamNames(Cookie[] session) throws Exception {
        return mockMvc.perform(get("/api/v1/teams").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
