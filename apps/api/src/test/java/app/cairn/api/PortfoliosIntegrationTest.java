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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Portfolios: CRUD + membership + reorder, status updates + history, and the roll-up (incl. perf). */
class PortfoliosIntegrationTest extends IntegrationTestBase {

    // --- portfolio CRUD + add/remove/reorder projects -------------------------

    @Test
    void portfolioCrudMembershipAndReorder() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID p1 = createProject(admin, "Alpha", teamId);
        UUID p2 = createProject(admin, "Beta", teamId);
        UUID p3 = createProject(admin, "Gamma", teamId);

        // create + get + patch
        UUID portfolio = createPortfolio(admin, "Q3 Portfolio", "the desc");
        mockMvc.perform(get("/api/v1/portfolios/" + portfolio).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Q3 Portfolio"))
                .andExpect(jsonPath("$.data.description").value("the desc"));
        mockMvc.perform(patchJson("/api/v1/portfolios/" + portfolio, admin,
                        "{\"name\":\"Q3\",\"description\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Q3"))
                .andExpect(jsonPath("$.data.description").doesNotExist());

        // add projects (append order p1, p2, p3)
        addProject(admin, portfolio, p1);
        addProject(admin, portfolio, p2);
        addProject(admin, portfolio, p3);
        assertThat(rollupProjectIds(admin, portfolio)).containsExactly(p1.toString(), p2.toString(), p3.toString());

        // adding the same project again → 409
        mockMvc.perform(postJson("/api/v1/portfolios/" + portfolio + "/projects", admin,
                        "{\"projectId\":\"" + p1 + "\"}"))
                .andExpect(status().isConflict());

        // reorder: move p3 to the front (before p1)
        mockMvc.perform(postJson("/api/v1/portfolios/" + portfolio + "/projects/" + p3 + "/move", admin,
                        "{\"beforeId\":\"" + p1 + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(rollupProjectIds(admin, portfolio)).containsExactly(p3.toString(), p1.toString(), p2.toString());

        // move p3 between p1 and p2
        mockMvc.perform(postJson("/api/v1/portfolios/" + portfolio + "/projects/" + p3 + "/move", admin,
                        "{\"afterId\":\"" + p1 + "\",\"beforeId\":\"" + p2 + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(rollupProjectIds(admin, portfolio)).containsExactly(p1.toString(), p3.toString(), p2.toString());

        // remove p2 (project itself survives)
        mockMvc.perform(delete("/api/v1/portfolios/" + portfolio + "/projects/" + p2).cookie(admin))
                .andExpect(status().isNoContent());
        assertThat(rollupProjectIds(admin, portfolio)).containsExactly(p1.toString(), p3.toString());
        mockMvc.perform(get("/api/v1/projects/" + p2).cookie(admin)).andExpect(status().isOk());

        // delete the portfolio → gone, but member projects still exist
        mockMvc.perform(delete("/api/v1/portfolios/" + portfolio).cookie(admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/portfolios/" + portfolio).cookie(admin)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/" + p1).cookie(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/" + p3).cookie(admin)).andExpect(status().isOk());
    }

    // --- deleting a project removes it from its portfolios --------------------

    @Test
    void deletingProjectDropsPortfolioMembership() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID keep = createProject(admin, "Keep", teamId);
        UUID doomed = createProject(admin, "Doomed", teamId);
        UUID portfolio = createPortfolio(admin, "P", null);
        addProject(admin, portfolio, keep);
        addProject(admin, portfolio, doomed);

        mockMvc.perform(delete("/api/v1/projects/" + doomed).cookie(admin)).andExpect(status().isNoContent());

        // roll-up no longer lists the deleted project; the FK never blocked the delete
        assertThat(rollupProjectIds(admin, portfolio)).containsExactly(keep.toString());
    }

    // --- status update sets current_status + denormalizes + history -----------

    @Test
    void statusUpdateSetsCurrentStatusAndKeepsHistory() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID project = createProject(admin, "Launch", teamId);

        // fresh project has no status
        mockMvc.perform(get("/api/v1/projects/" + project).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").doesNotExist())
                .andExpect(jsonPath("$.data.statusUpdatedAt").doesNotExist());

        // post a status update → denormalized onto the project
        mockMvc.perform(postJson("/api/v1/projects/" + project + "/status-updates", admin,
                        "{\"status\":\"at_risk\",\"title\":\"Week 1\",\"body\":\"slipping a bit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("at_risk"));
        mockMvc.perform(get("/api/v1/projects/" + project).cookie(admin))
                .andExpect(jsonPath("$.data.currentStatus").value("at_risk"))
                .andExpect(jsonPath("$.data.statusUpdatedAt").isNotEmpty());

        // a second update changes current_status; history keeps both, newest first
        mockMvc.perform(postJson("/api/v1/projects/" + project + "/status-updates", admin,
                        "{\"status\":\"on_track\",\"title\":\"Week 2\",\"body\":\"back on track\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/projects/" + project).cookie(admin))
                .andExpect(jsonPath("$.data.currentStatus").value("on_track"));

        MvcResult hist = mockMvc.perform(get("/api/v1/projects/" + project + "/status-updates").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = body(hist).get("data");
        assertThat(data.size()).isEqualTo(2);
        assertThat(data.get(0).get("title").asText()).isEqualTo("Week 2"); // newest first
        assertThat(data.get(1).get("title").asText()).isEqualTo("Week 1");

        // invalid status → 400
        mockMvc.perform(postJson("/api/v1/projects/" + project + "/status-updates", admin,
                        "{\"status\":\"bogus\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- start/end editable via PATCH /projects -------------------------------

    @Test
    void projectScheduleDatesEditableViaPatch() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID project = createProject(admin, "Scheduled", teamId);

        mockMvc.perform(patchJson("/api/v1/projects/" + project, admin,
                        "{\"startDate\":\"2026-08-01\",\"endDate\":\"2026-09-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-09-30"));

        // clear a date (explicit null)
        mockMvc.perform(patchJson("/api/v1/projects/" + project, admin, "{\"endDate\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.endDate").doesNotExist());

        // end before start → 400
        mockMvc.perform(patchJson("/api/v1/projects/" + project, admin,
                        "{\"startDate\":\"2026-08-01\",\"endDate\":\"2026-07-01\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- roll-up: single aggregate over ~50 projects / 10k tasks, under 500ms --

    @Test
    void rollupOverFiftyProjectsAndTenThousandTasksIsFastAndCorrect() throws Exception {
        Cookie[] admin = bootstrap();
        UUID org = jdbc.queryForObject("select id from organizations limit 1", UUID.class);
        UUID owner = jdbc.queryForObject("select id from users limit 1", UUID.class);
        UUID team = generalTeamId();
        UUID portfolio = createPortfolio(admin, "Big", null);

        int projectCount = 50;
        int tasksPerProject = 200; // 50 * 200 = 10_000 tasks
        int completePerProject = 100;
        Timestamp recent = Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS));

        List<Object[]> projectRows = new ArrayList<>();
        List<Object[]> memberRows = new ArrayList<>();
        List<Object[]> taskRows = new ArrayList<>();
        String[] statuses = {"on_track", "at_risk", "off_track", "on_hold"};
        List<UUID> ids = new ArrayList<>();

        for (int i = 0; i < projectCount; i++) {
            UUID pid = UUID.randomUUID();
            ids.add(pid);
            String status = i < 40 ? statuses[i % 4] : null; // 40 fresh (10 each), 10 with no update
            Timestamp updatedAt = i < 40 ? recent : null;
            projectRows.add(new Object[] {pid, org, team, owner, "Proj " + i, "list", false, status, updatedAt});
            memberRows.add(new Object[] {org, portfolio, pid, String.format("k%04d", i)});
            for (int t = 0; t < tasksPerProject; t++) {
                UUID tid = UUID.randomUUID();
                boolean completed = t < completePerProject;
                taskRows.add(new Object[] {tid, org, pid, "T" + i + "-" + t, "none", completed, "s" + t});
            }
        }

        jdbc.batchUpdate(
                "insert into projects (id, organization_id, team_id, owner_id, name, default_view, archived,"
                        + " current_status, status_updated_at, created_at) values (?,?,?,?,?,?,?,?,?, now())",
                projectRows);
        jdbc.batchUpdate(
                "insert into portfolio_projects (organization_id, portfolio_id, project_id, sort_key, added_at)"
                        + " values (?,?,?,?, now())",
                memberRows);
        jdbc.batchUpdate(
                "insert into tasks (id, organization_id, project_id, title, priority, completed, sort_key,"
                        + " created_at, updated_at) values (?,?,?,?,?,?,?, now(), now())",
                taskRows);

        String url = "/api/v1/portfolios/" + portfolio + "/rollup";
        // warm up (bind request scope + prime the plan cache) then measure the single aggregate query
        mockMvc.perform(get(url).cookie(admin)).andExpect(status().isOk());
        long startNs = System.nanoTime();
        MvcResult res = mockMvc.perform(get(url).cookie(admin)).andExpect(status().isOk()).andReturn();
        long ms = (System.nanoTime() - startNs) / 1_000_000;
        System.out.println("[rollup-perf] 50 projects / 10k tasks roll-up endpoint took " + ms + "ms");

        JsonNode root = body(res).get("data");
        JsonNode projects = root.get("projects");
        assertThat(projects.size()).isEqualTo(projectCount);

        // task counts are correct (aggregated in the one query, not per-project)
        JsonNode first = projects.get(0);
        assertThat(first.get("tasksTotal").asInt()).isEqualTo(tasksPerProject);
        assertThat(first.get("tasksComplete").asInt()).isEqualTo(completePerProject);

        // header summary: 10 each of the four statuses, 10 with no update
        JsonNode summary = root.get("summary");
        assertThat(summary.get("total").asInt()).isEqualTo(50);
        assertThat(summary.get("onTrack").asInt()).isEqualTo(10);
        assertThat(summary.get("atRisk").asInt()).isEqualTo(10);
        assertThat(summary.get("offTrack").asInt()).isEqualTo(10);
        assertThat(summary.get("onHold").asInt()).isEqualTo(10);
        assertThat(summary.get("noUpdate").asInt()).isEqualTo(10);

        assertThat(ms).as("roll-up (one aggregate query over 10k tasks) under 500ms; was " + ms + "ms")
                .isLessThan(500);
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

    private UUID generalTeamId() {
        return jdbc.queryForObject("select id from teams where name = 'General'", UUID.class);
    }

    private UUID createProject(Cookie[] admin, String name, UUID teamId) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/projects", admin,
                        "{\"name\":\"" + name + "\",\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private UUID createPortfolio(Cookie[] admin, String name, String description) throws Exception {
        String desc = description == null ? "" : ",\"description\":\"" + description + "\"";
        MvcResult r = mockMvc.perform(postJson("/api/v1/portfolios", admin,
                        "{\"name\":\"" + name + "\"" + desc + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private void addProject(Cookie[] admin, UUID portfolio, UUID projectId) throws Exception {
        mockMvc.perform(postJson("/api/v1/portfolios/" + portfolio + "/projects", admin,
                        "{\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isNoContent());
    }

    private List<String> rollupProjectIds(Cookie[] admin, UUID portfolio) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/portfolios/" + portfolio + "/rollup").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        List<String> out = new ArrayList<>();
        for (JsonNode n : body(r).get("data").get("projects")) {
            out.add(n.get("projectId").asText());
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
