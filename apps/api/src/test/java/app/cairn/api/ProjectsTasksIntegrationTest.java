package app.cairn.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.cairn.api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Projects / sections / tasks flows against a real Postgres via the full security chain. */
class ProjectsTasksIntegrationTest extends IntegrationTestBase {

    // --- task CRUD + move + complete -----------------------------------------

    @Test
    void taskCrudMoveAndComplete() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Launch", teamId);
        UUID sectionId = createSection(admin, projectId, "To do");

        UUID t1 = createTask(admin, projectId, sectionId, "First");
        UUID t2 = createTask(admin, projectId, sectionId, "Second");
        UUID t3 = createTask(admin, projectId, sectionId, "Third");

        // new tasks append → creation order
        assertThat(taskTitlesInOrder(admin, projectId)).containsExactly("First", "Second", "Third");

        // patch fields
        UUID adminId = me(admin);
        mockMvc.perform(patchJson("/api/v1/tasks/" + t2, admin,
                        "{\"title\":\"Second!\",\"priority\":\"high\",\"dueDate\":\"2026-08-01\",\"assigneeId\":\""
                                + adminId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Second!"))
                .andExpect(jsonPath("$.data.priority").value("high"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.assigneeId").value(adminId.toString()));

        // clearing a nullable field: unassign + clear due (explicit null vs omitted)
        mockMvc.perform(patchJson("/api/v1/tasks/" + t2, admin, "{\"assigneeId\":null,\"dueDate\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.data.dueDate").doesNotExist())
                .andExpect(jsonPath("$.data.title").value("Second!")); // untouched by omission

        // complete → completed_at set
        mockMvc.perform(patchJson("/api/v1/tasks/" + t1, admin, "{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        // uncomplete → completed_at cleared
        mockMvc.perform(patchJson("/api/v1/tasks/" + t1, admin, "{\"completed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());

        // move t3 to the front (before t1)
        mockMvc.perform(postJson("/api/v1/tasks/" + t3 + "/move", admin,
                        "{\"sectionId\":\"" + sectionId + "\",\"beforeTaskId\":\"" + t1 + "\"}"))
                .andExpect(status().isOk());
        assertThat(taskTitlesInOrder(admin, projectId)).containsExactly("Third", "First", "Second!");

        // move t3 between t1 and t2 (after t1, before t2)
        mockMvc.perform(postJson("/api/v1/tasks/" + t3 + "/move", admin,
                        "{\"sectionId\":\"" + sectionId + "\",\"afterTaskId\":\"" + t1 + "\",\"beforeTaskId\":\""
                                + t2 + "\"}"))
                .andExpect(status().isOk());
        assertThat(taskTitlesInOrder(admin, projectId)).containsExactly("First", "Third", "Second!");

        // get + delete
        mockMvc.perform(get("/api/v1/tasks/" + t3).cookie(admin)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/tasks/" + t3).cookie(admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tasks/" + t3).cookie(admin)).andExpect(status().isNotFound());
        assertThat(taskTitlesInOrder(admin, projectId)).containsExactly("First", "Second!");
    }

    // --- team delete guard ----------------------------------------------------

    @Test
    void teamDeleteWithProjectsConflicts() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        createProject(admin, "Owned", teamId);

        // General team owns a project → 409
        mockMvc.perform(delete("/api/v1/teams/" + teamId).cookie(admin))
                .andExpect(status().isConflict());

        // an empty team deletes fine
        UUID empty = createTeam(admin, "Empty");
        mockMvc.perform(delete("/api/v1/teams/" + empty).cookie(admin))
                .andExpect(status().isNoContent());
    }

    // --- archived projects hidden by default ---------------------------------

    @Test
    void archivedProjectsHiddenFromDefaultList() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID keep = createProject(admin, "Active", teamId);
        UUID archived = createProject(admin, "Archived", teamId);

        mockMvc.perform(patchJson("/api/v1/projects/" + archived, admin, "{\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(true));

        // default list excludes the archived project
        MvcResult def = mockMvc.perform(get("/api/v1/projects").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(idsOf(def)).contains(keep.toString()).doesNotContain(archived.toString());

        // includeArchived=true shows both
        MvcResult all = mockMvc.perform(get("/api/v1/projects?includeArchived=true").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(idsOf(all)).contains(keep.toString(), archived.toString());
    }

    // --- /me/tasks correctness ------------------------------------------------

    @Test
    void myTasksReturnsOpenAssignedOrderedByDueNullsLast() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID adminId = me(admin);
        UUID projectA = createProject(admin, "Alpha", teamId);
        UUID projectB = createProject(admin, "Beta", teamId);

        UUID today = createTask(admin, projectA, null, "Due today");
        UUID tomorrow = createTask(admin, projectA, null, "Due tomorrow");
        UUID noDue = createTask(admin, projectB, null, "No due date");
        UUID doneTask = createTask(admin, projectA, null, "Already done");
        createTask(admin, projectA, null, "Unassigned"); // stays unassigned → excluded

        LocalDate td = LocalDate.now();
        assign(admin, today, adminId, td.toString());
        assign(admin, tomorrow, adminId, td.plusDays(1).toString());
        assign(admin, noDue, adminId, null);
        assign(admin, doneTask, adminId, td.minusDays(1).toString());
        mockMvc.perform(patchJson("/api/v1/tasks/" + doneTask, admin, "{\"completed\":true}"))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(get("/api/v1/me/tasks").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = body(res).get("data");

        // only the three open, assigned tasks — completed and unassigned excluded
        assertThat(data.size()).isEqualTo(3);
        // ordered by due date, nulls last
        assertThat(data.get(0).get("title").asText()).isEqualTo("Due today");
        assertThat(data.get(1).get("title").asText()).isEqualTo("Due tomorrow");
        assertThat(data.get(2).get("title").asText()).isEqualTo("No due date");
        // each row carries its project id + name
        assertThat(data.get(0).get("projectName").asText()).isEqualTo("Alpha");
        assertThat(data.get(2).get("projectName").asText()).isEqualTo("Beta");
        assertThat(data.get(0).get("projectId").asText()).isEqualTo(projectA.toString());
    }

    // --- default sections seeded on project create (spec B2) ------------------

    @Test
    void newProjectSeedsThreeDefaultSections() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Fresh", teamId);

        MvcResult r = mockMvc.perform(get("/api/v1/projects/" + projectId + "/sections").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (JsonNode s : body(r).get("data")) {
            names.add(s.get("name").asText());
        }
        // exactly the three defaults, in order (fractional keys spaced by rebalance)
        assertThat(names).containsExactly("To do", "In progress", "Done");
    }

    // --- section delete requires empty or moveTo ------------------------------

    @Test
    void sectionDeleteRequiresEmptyOrMoveTo() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Sec", teamId);
        UUID a = createSection(admin, projectId, "A");
        UUID b = createSection(admin, projectId, "B");
        createTask(admin, projectId, a, "in-a-1");
        createTask(admin, projectId, a, "in-a-2");

        // non-empty, no moveTo → 409
        mockMvc.perform(delete("/api/v1/sections/" + a).cookie(admin))
                .andExpect(status().isConflict());

        // with moveTo → tasks relocate and section deletes
        mockMvc.perform(delete("/api/v1/sections/" + a + "?moveTo=" + b).cookie(admin))
                .andExpect(status().isNoContent());

        // both tasks now live under section B, in the project's flat list
        assertThat(taskTitlesInOrder(admin, projectId)).containsExactly("in-a-1", "in-a-2");
        MvcResult sec = mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode t : body(sec).get("data")) {
            assertThat(t.get("sectionId").asText()).isEqualTo(b.toString());
        }
    }

    // --- helpers -------------------------------------------------------------

    private Cookie[] bootstrap() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"orgName\":\"Acme\",\"adminName\":\"Ada\",\"email\":\"ada@acme.test\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return cookiesOf(r);
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

    private UUID createTeam(Cookie[] admin, String name) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/teams", admin, "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    // --- schedule dates on create (spec B2; regression: they were silently dropped) ----------

    @Test
    void createAcceptsScheduleDatesAndRejectsBackwardsWindow() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();

        // dates supplied at create time must persist — POST used to ignore them, leaving every new
        // project unscheduled and the portfolio schedule view empty until someone PATCHed it.
        MvcResult created = mockMvc.perform(postJson("/api/v1/projects", admin,
                        "{\"name\":\"Scheduled\",\"teamId\":\"" + teamId
                                + "\",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-09-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.startDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-09-30"))
                .andReturn();
        UUID id = UUID.fromString(body(created).get("data").get("id").asText());

        // and survive a re-read (i.e. actually hit the row, not just the response mapper)
        mockMvc.perform(get("/api/v1/projects/" + id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-07-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-09-30"));

        // both optional — omitting them is still fine
        mockMvc.perform(postJson("/api/v1/projects", admin,
                        "{\"name\":\"Undated\",\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.startDate").doesNotExist());

        // end before start is rejected on create, exactly as PATCH already rejected it
        mockMvc.perform(postJson("/api/v1/projects", admin,
                        "{\"name\":\"Backwards\",\"teamId\":\"" + teamId
                                + "\",\"startDate\":\"2026-09-30\",\"endDate\":\"2026-07-01\"}"))
                .andExpect(status().isBadRequest());
    }

    private UUID createProject(Cookie[] admin, String name, UUID teamId) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/projects", admin,
                        "{\"name\":\"" + name + "\",\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private UUID createSection(Cookie[] admin, UUID projectId, String name) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/sections", admin,
                        "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private UUID createTask(Cookie[] admin, UUID projectId, UUID sectionId, String title) throws Exception {
        String section = sectionId == null ? "null" : "\"" + sectionId + "\"";
        MvcResult r = mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/tasks", admin,
                        "{\"title\":\"" + title + "\",\"sectionId\":" + section + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private void assign(Cookie[] admin, UUID taskId, UUID assignee, String due) throws Exception {
        String dueJson = due == null ? "null" : "\"" + due + "\"";
        mockMvc.perform(patchJson("/api/v1/tasks/" + taskId, admin,
                        "{\"assigneeId\":\"" + assignee + "\",\"dueDate\":" + dueJson + "}"))
                .andExpect(status().isOk());
    }

    private java.util.List<String> taskTitlesInOrder(Cookie[] admin, UUID projectId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (JsonNode t : body(r).get("data")) {
            out.add(t.get("title").asText());
        }
        return out;
    }

    private java.util.List<String> idsOf(MvcResult result) throws Exception {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (JsonNode n : body(result).get("data")) {
            out.add(n.get("id").asText());
        }
        return out;
    }

    private MockHttpServletRequestBuilder postJson(String url, Cookie[] cookies, String body) {
        return post(url).cookie(cookies).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder patchJson(String url, Cookie[] cookies, String body) {
        return patch(url).cookie(cookies).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
