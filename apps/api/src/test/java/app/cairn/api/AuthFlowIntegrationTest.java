package app.cairn.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.cairn.api.auth.session.AuthCookies;
import app.cairn.api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end auth/orgs/users/invites flows against a real Postgres via the full security chain. */
class AuthFlowIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "ada@acme.test";
    private static final String ADMIN_PW = "supersecret1";

    // --- bootstrap → me → refresh → login ------------------------------------

    @Test
    void bootstrapThenSessionLifecycle() throws Exception {
        mockMvc.perform(get("/api/v1/auth/bootstrap-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needsBootstrap").value(true));

        MvcResult boot = bootstrap();
        mockMvc.perform(get("/api/v1/auth/bootstrap-status"))
                .andExpect(jsonPath("$.data.needsBootstrap").value(false));

        Cookie[] cookies = cookiesOf(boot);
        assertThat(cookieValue(boot, AuthCookies.ACCESS)).isNotBlank();
        assertThat(cookieValue(boot, AuthCookies.REFRESH)).isNotBlank();

        // me with cookie
        mockMvc.perform(get("/api/v1/auth/me").cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.role").value("admin"))
                .andExpect(jsonPath("$.data.orgName").value("Acme Ops"));

        // me without cookie → 401
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        // refresh rotates and yields a working new session
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookies))
                .andExpect(status().isOk())
                .andReturn();
        String oldRefresh = cookieValue(boot, AuthCookies.REFRESH);
        String newRefresh = cookieValue(refreshed, AuthCookies.REFRESH);
        assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);

        // old refresh token is now revoked
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH, oldRefresh)))
                .andExpect(status().isUnauthorized());

        // login: wrong then right
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, ADMIN_PW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL));
    }

    @Test
    void secondBootstrapConflicts() throws Exception {
        bootstrap();
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"orgName":"Dup","adminName":"D","email":"d@d.test","password":"password12"}"""))
                .andExpect(status().isConflict());
    }

    // --- invite → accept → login ---------------------------------------------

    @Test
    void inviteAcceptThenLogin() throws Exception {
        Cookie[] admin = cookiesOf(bootstrap());

        MvcResult created = mockMvc.perform(post("/api/v1/invites")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Bob@Acme.Test"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("bob@acme.test"))
                .andReturn();
        String token = tokenFromAcceptUrl(body(created).get("data").get("acceptUrl").asText());

        mockMvc.perform(get("/api/v1/invites").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("bob@acme.test"));

        // accept
        mockMvc.perform(post("/api/v1/invites/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AcceptBody(token, "Bob Member", "memberpass1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("bob@acme.test"));

        // token is single-use
        mockMvc.perform(post("/api/v1/invites/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AcceptBody(token, "Bob", "memberpass1"))))
                .andExpect(status().isConflict());

        // new member can log in
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("bob@acme.test", "memberpass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("member"));
    }

    // --- RBAC ----------------------------------------------------------------

    @Test
    void memberForbiddenFromPatchingUsers() throws Exception {
        Cookie[] admin = cookiesOf(bootstrap());
        Cookie[] member = cookiesOf(inviteAcceptAndLogin(admin, "carol@acme.test", "carolpass12", "Carol"));
        UUID carolId = userId("carol@acme.test");

        // members may list
        mockMvc.perform(get("/api/v1/users").cookie(member)).andExpect(status().isOk());

        // but not mutate
        mockMvc.perform(patch("/api/v1/users/" + carolId)
                        .cookie(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}"""))
                .andExpect(status().isForbidden());

        // unauthenticated → 401
        mockMvc.perform(patch("/api/v1/users/" + carolId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}"""))
                .andExpect(status().isUnauthorized());
    }

    // --- last-admin guard ----------------------------------------------------

    @Test
    void cannotDemoteOrDeactivateLastActiveAdmin() throws Exception {
        Cookie[] admin = cookiesOf(bootstrap());
        UUID adminId = userId(ADMIN_EMAIL);

        // demote the only admin → 409
        mockMvc.perform(patch("/api/v1/users/" + adminId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"member"}"""))
                .andExpect(status().isConflict());

        // deactivate the only admin → 409
        mockMvc.perform(patch("/api/v1/users/" + adminId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false}"""))
                .andExpect(status().isConflict());

        // add a second admin, then the first may be demoted
        inviteAcceptAndLogin(admin, "dave@acme.test", "davepass123", "Dave");
        UUID daveId = userId("dave@acme.test");
        mockMvc.perform(patch("/api/v1/users/" + daveId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}"""))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/users/" + adminId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"member"}"""))
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

    /** Admin invites an email, that person accepts and logs in; returns their login result (cookies). */
    private MvcResult inviteAcceptAndLogin(Cookie[] admin, String email, String password, String name)
            throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/invites")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new InviteBody(email))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = tokenFromAcceptUrl(body(created).get("data").get("acceptUrl").asText());
        mockMvc.perform(post("/api/v1/invites/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AcceptBody(token, name, password))))
                .andExpect(status().isCreated());
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject(
                "select id from users where lower(email) = lower(?)", UUID.class, email);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String tokenFromAcceptUrl(String acceptUrl) {
        int i = acceptUrl.indexOf("token=");
        return acceptUrl.substring(i + "token=".length());
    }

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    private record InviteBody(String email) {}

    private record AcceptBody(String token, String name, String password) {}
}
