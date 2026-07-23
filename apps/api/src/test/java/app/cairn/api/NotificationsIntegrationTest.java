package app.cairn.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.cairn.api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * In-app notification fan-out (F1): each of the six triggers fires exactly one notification to the right
 * recipient and none to the actor, plus read / read-all / unread-count and the never-self-notify rule.
 * Cross-user notifications are exercised with a genuinely separate second user (invite → accept → login).
 */
class NotificationsIntegrationTest extends IntegrationTestBase {

    // --- (1) assignment ------------------------------------------------------

    @Test
    void assignmentNotifiesAssigneeNotActor() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), null);
        UUID task = createTask(admin, project, "Wire it up");

        // admin assigns the task to bob
        assign(admin, task, bob.id());

        assertThat(unreadOfType(bob.cookies(), "task_assigned")).hasSize(1);
        JsonNode n = unreadOfType(bob.cookies(), "task_assigned").get(0);
        assertThat(n.get("actorId").asText()).isEqualTo(me(admin).toString());
        assertThat(n.get("resourceId").asText()).isEqualTo(task.toString());
        assertThat(n.get("payload").get("actorName").asText()).isEqualTo("Ada");
        assertThat(n.get("payload").get("objectTitle").asText()).isEqualTo("Wire it up");
        assertThat(n.get("payload").get("projectId").asText()).isEqualTo(project.toString());
        // the actor is never notified
        assertThat(unreadCount(admin)).isZero();
    }

    // --- (2) @mention --------------------------------------------------------

    @Test
    void mentionNotifiesMentionedUserNotAuthor() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), null);
        UUID task = createTask(admin, project, "Discuss"); // unassigned, created by admin

        // admin @mentions bob (by uuid token) — bob is neither assignee nor creator
        comment(admin, task, "hey @" + bob.id() + " take a look");

        assertThat(unreadOfType(bob.cookies(), "comment_mention")).hasSize(1);
        assertThat(unreadOfType(bob.cookies(), "comment_added")).isEmpty();
        JsonNode n = unreadOfType(bob.cookies(), "comment_mention").get(0);
        assertThat(n.get("payload").get("snippet").asText()).contains("take a look");
        assertThat(unreadCount(admin)).isZero();
    }

    // --- (3) comment on a task you're assigned to / created ------------------

    @Test
    void commentNotifiesTaskAssignee() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), null);
        UUID task = createTask(admin, project, "Ship it");
        assign(admin, task, bob.id());
        markAllRead(bob.cookies()); // clear the assignment notification so the count is clean

        // admin comments (no mention) on bob's task
        comment(admin, task, "any progress?");

        assertThat(unreadOfType(bob.cookies(), "comment_added")).hasSize(1);
        assertThat(unreadOfType(bob.cookies(), "comment_mention")).isEmpty();
        assertThat(unreadCount(admin)).isZero();
    }

    @Test
    void mentionAndAssigneeCollapseToOneNotification() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), null);
        UUID task = createTask(admin, project, "Ship it");
        assign(admin, task, bob.id());
        markAllRead(bob.cookies());

        // bob is BOTH the assignee and @mentioned → exactly one (the mention), never two
        comment(admin, task, "@" + bob.id() + " ping");

        assertThat(unreadCount(bob.cookies())).isEqualTo(1);
        assertThat(unreadOfType(bob.cookies(), "comment_mention")).hasSize(1);
        assertThat(unreadOfType(bob.cookies(), "comment_added")).isEmpty();
    }

    // --- (4) status update on a project/portfolio you own --------------------

    @Test
    void statusUpdateNotifiesProjectOwner() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), bob.id()); // owned by bob

        postStatusUpdate(admin, project, "at_risk", "Slipping", "we need more hands");

        assertThat(unreadOfType(bob.cookies(), "status_update")).hasSize(1);
        JsonNode n = unreadOfType(bob.cookies(), "status_update").get(0);
        assertThat(n.get("resourceType").asText()).isEqualTo("project");
        assertThat(n.get("resourceId").asText()).isEqualTo(project.toString());
        assertThat(n.get("payload").get("status").asText()).isEqualTo("at_risk");
        assertThat(n.get("payload").get("snippet").asText()).isEqualTo("Slipping");
        assertThat(unreadCount(admin)).isZero();
    }

    @Test
    void statusUpdateNotifiesPortfolioOwner() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), null); // owned by admin
        UUID portfolio = createPortfolio(admin, "Q3", bob.id()); // owned by bob
        addProjectToPortfolio(admin, portfolio, project);

        postStatusUpdate(admin, project, "on_track", "All good", null);

        // bob owns the containing portfolio (not the project) → still notified, exactly once
        assertThat(unreadOfType(bob.cookies(), "status_update")).hasSize(1);
        assertThat(unreadCount(admin)).isZero();
    }

    // --- (5) due-date change on a task assigned to you -----------------------

    @Test
    void dueChangeNotifiesAssignee() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), null);
        UUID task = createTask(admin, project, "Deadline task");
        assign(admin, task, bob.id());
        markAllRead(bob.cookies());

        patchTask(admin, task, "{\"dueDate\":\"2026-09-01\"}");

        assertThat(unreadOfType(bob.cookies(), "task_due_changed")).hasSize(1);
        assertThat(unreadOfType(bob.cookies(), "task_due_changed").get(0).get("payload").get("snippet").asText())
                .contains("2026-09-01");
        assertThat(unreadCount(admin)).isZero();
    }

    // --- (6) your task completed by someone else -----------------------------

    @Test
    void completionByOtherNotifiesAssignee() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), null);
        UUID task = createTask(admin, project, "Bob's task");
        assign(admin, task, bob.id());
        markAllRead(bob.cookies());

        // admin (not the assignee) completes bob's task
        patchTask(admin, task, "{\"completed\":true}");

        assertThat(unreadOfType(bob.cookies(), "task_completed")).hasSize(1);
        assertThat(unreadCount(admin)).isZero();
    }

    // --- never self-notify ---------------------------------------------------

    @Test
    void noSelfNotification() throws Exception {
        Cookie[] admin = bootstrap();
        UUID adminId = me(admin);
        UUID project = createProject(admin, "Solo", generalTeamId(), null);
        UUID task = createTask(admin, project, "Mine");

        // assign to self, set my own due date, complete my own task, mention myself, post my own status
        assign(admin, task, adminId);
        patchTask(admin, task, "{\"dueDate\":\"2026-10-01\"}");
        comment(admin, task, "note to @" + adminId + " self");
        patchTask(admin, task, "{\"completed\":true}");
        postStatusUpdate(admin, project, "on_track", "fine", null); // admin owns the project

        assertThat(unreadCount(admin)).isZero();
    }

    // --- read / read-all / unread-count endpoints ----------------------------

    @Test
    void readReadAllAndUnreadCount() throws Exception {
        Cookie[] admin = bootstrap();
        User bob = inviteAndLogin(admin, "bob@acme.test", "Bob");
        UUID project = createProject(admin, "Launch", generalTeamId(), null);
        UUID t1 = createTask(admin, project, "One");
        UUID t2 = createTask(admin, project, "Two");

        assign(admin, t1, bob.id());
        assign(admin, t2, bob.id());
        assertThat(unreadCount(bob.cookies())).isEqualTo(2);

        // mark one read → unread drops to 1, and that row reads back read=true
        List<JsonNode> all = notifications(bob.cookies());
        assertThat(all).hasSize(2);
        UUID firstId = UUID.fromString(all.get(0).get("id").asText());
        mockMvc.perform(post("/api/v1/notifications/" + firstId + "/read").cookie(bob.cookies()))
                .andExpect(status().isNoContent());
        assertThat(unreadCount(bob.cookies())).isEqualTo(1);

        // a foreign / unknown notification id → 404, no leak (admin cannot read bob's)
        mockMvc.perform(post("/api/v1/notifications/" + firstId + "/read").cookie(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/notifications/" + UUID.randomUUID() + "/read").cookie(bob.cookies()))
                .andExpect(status().isNotFound());

        // read-all → zero unread
        mockMvc.perform(post("/api/v1/notifications/read-all").cookie(bob.cookies()))
                .andExpect(status().isNoContent());
        assertThat(unreadCount(bob.cookies())).isZero();

        // newest-first ordering: the second assignment appears before the first
        List<JsonNode> ordered = notifications(bob.cookies());
        assertThat(ordered.get(0).get("resourceId").asText()).isEqualTo(t2.toString());
        assertThat(ordered.get(1).get("resourceId").asText()).isEqualTo(t1.toString());
    }

    @Test
    void notificationsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    // --- helpers -------------------------------------------------------------

    private record User(Cookie[] cookies, UUID id) {}

    private Cookie[] bootstrap() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"orgName\":\"Acme\",\"adminName\":\"Ada\",\"email\":\"ada@acme.test\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return cookiesOf(r);
    }

    /** Invite an email, accept the invite (extracting the token from the accept URL), then log in. */
    private User inviteAndLogin(Cookie[] admin, String email, String name) throws Exception {
        MvcResult created = mockMvc.perform(postJson("/api/v1/invites", admin, "{\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String acceptUrl = body(created).get("data").get("acceptUrl").asText();
        String token = acceptUrl.substring(acceptUrl.indexOf("token=") + "token=".length());

        mockMvc.perform(post("/api/v1/invites/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"name\":\"" + name + "\",\"password\":\"supersecret2\"}"))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"supersecret2\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie[] cookies = cookiesOf(login);
        return new User(cookies, me(cookies));
    }

    private UUID me(Cookie[] cookies) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/auth/me").cookie(cookies))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private UUID generalTeamId() {
        return jdbc.queryForObject("select id from teams where name = 'General'", UUID.class);
    }

    private UUID createProject(Cookie[] cookies, String name, UUID teamId, UUID ownerId) throws Exception {
        String owner = ownerId == null ? "" : ",\"ownerId\":\"" + ownerId + "\"";
        MvcResult r = mockMvc.perform(postJson("/api/v1/projects", cookies,
                        "{\"name\":\"" + name + "\",\"teamId\":\"" + teamId + "\"" + owner + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private UUID createTask(Cookie[] cookies, UUID projectId, String title) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/tasks", cookies,
                        "{\"title\":\"" + title + "\",\"sectionId\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private void assign(Cookie[] cookies, UUID taskId, UUID userId) throws Exception {
        patchTask(cookies, taskId, "{\"assigneeId\":\"" + userId + "\"}");
    }

    private void patchTask(Cookie[] cookies, UUID taskId, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .cookie(cookies)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void comment(Cookie[] cookies, UUID taskId, String body) throws Exception {
        mockMvc.perform(postJson("/api/v1/tasks/" + taskId + "/comments", cookies,
                        "{\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated());
    }

    private void postStatusUpdate(Cookie[] cookies, UUID projectId, String stat, String title, String body)
            throws Exception {
        String bodyJson = body == null ? "null" : "\"" + body + "\"";
        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/status-updates", cookies,
                        "{\"status\":\"" + stat + "\",\"title\":\"" + title + "\",\"body\":" + bodyJson + "}"))
                .andExpect(status().isCreated());
    }

    private UUID createPortfolio(Cookie[] cookies, String name, UUID ownerId) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/portfolios", cookies,
                        "{\"name\":\"" + name + "\",\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private void addProjectToPortfolio(Cookie[] cookies, UUID portfolioId, UUID projectId) throws Exception {
        mockMvc.perform(postJson("/api/v1/portfolios/" + portfolioId + "/projects", cookies,
                        "{\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private List<JsonNode> notifications(Cookie[] cookies) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/notifications").cookie(cookies))
                .andExpect(status().isOk())
                .andReturn();
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode n : body(r).get("data")) {
            out.add(n);
        }
        return out;
    }

    private List<JsonNode> unreadOfType(Cookie[] cookies, String type) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode n : notifications(cookies)) {
            if (!n.get("read").asBoolean() && type.equals(n.get("type").asText())) {
                out.add(n);
            }
        }
        return out;
    }

    private int unreadCount(Cookie[] cookies) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(cookies))
                .andExpect(status().isOk())
                .andReturn();
        return body(r).get("data").get("count").asInt();
    }

    private void markAllRead(Cookie[] cookies) throws Exception {
        mockMvc.perform(post("/api/v1/notifications/read-all").cookie(cookies))
                .andExpect(status().isNoContent());
    }

    private MockHttpServletRequestBuilder postJson(String url, Cookie[] cookies, String body) {
        return post(url).cookie(cookies).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
