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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Subtasks (C2), comments + edited marker (C3), and the merged activity stream (C3). */
class SubtasksCommentsActivityIntegrationTest extends IntegrationTestBase {

    // --- subtasks: CRUD + one-level guard + promote --------------------------

    @Test
    void subtaskLifecycleGuardAndPromote() throws Exception {
        Cookie[] admin = bootstrap();
        UUID adminId = me(admin);
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Launch", teamId);
        UUID sectionId = createSection(admin, projectId, "To do");
        UUID parent = createTask(admin, projectId, sectionId, "Parent task");

        // add two subtasks
        UUID sub1 = addSubtask(admin, parent, "Sub one");
        UUID sub2 = addSubtask(admin, parent, "Sub two");

        // GET /tasks/{parent} returns the subtasks array + progress count
        MvcResult detail = mockMvc.perform(get("/api/v1/tasks/" + parent).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.id").value(parent.toString()))
                .andExpect(jsonPath("$.data.subtasks.length()").value(2))
                .andExpect(jsonPath("$.data.subtaskProgress.total").value(2))
                .andExpect(jsonPath("$.data.subtaskProgress.completed").value(0))
                .andReturn();
        // subtasks carry parentTaskId
        assertThat(body(detail).get("data").get("subtasks").get(0).get("parentTaskId").asText())
                .isEqualTo(parent.toString());

        // subtasks are NOT in the project flat list
        assertThat(taskTitlesInOrder(admin, projectId)).containsExactly("Parent task");

        // completing a subtask updates the progress count
        mockMvc.perform(patchJson("/api/v1/tasks/" + sub1, admin, "{\"completed\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tasks/" + parent).cookie(admin))
                .andExpect(jsonPath("$.data.subtaskProgress.completed").value(1));

        // one-level guard: a subtask cannot have a subtask → 409
        mockMvc.perform(postJson("/api/v1/tasks/" + sub2 + "/subtasks", admin, "{\"title\":\"nope\"}"))
                .andExpect(status().isConflict());

        // a subtask assigned to me shows up in /me/tasks (sub2 is open)
        mockMvc.perform(patchJson("/api/v1/tasks/" + sub2, admin, "{\"assigneeId\":\"" + adminId + "\"}"))
                .andExpect(status().isOk());
        assertThat(myTaskIds(admin)).contains(sub2.toString());

        // promote sub2 → becomes a top-level task in the section, leaves the parent
        mockMvc.perform(postJson("/api/v1/tasks/" + sub2 + "/promote", admin,
                        "{\"sectionId\":\"" + sectionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentTaskId").doesNotExist());
        // now it appears in the flat list and the parent has one subtask left
        assertThat(taskTitlesInOrder(admin, projectId)).contains("Parent task", "Sub two");
        mockMvc.perform(get("/api/v1/tasks/" + parent).cookie(admin))
                .andExpect(jsonPath("$.data.subtaskProgress.total").value(1));
    }

    // --- comments: CRUD + edited marker --------------------------------------

    @Test
    void commentCrudAndEditedMarker() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Launch", teamId);
        UUID task = createTask(admin, projectId, null, "Discuss");

        UUID c1 = postComment(admin, task, "first comment");
        postComment(admin, task, "second comment");

        // chronological list
        MvcResult list = mockMvc.perform(get("/api/v1/tasks/" + task + "/comments").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        List<String> bodies = new ArrayList<>();
        for (JsonNode n : body(list).get("data")) {
            bodies.add(n.get("body").asText());
        }
        assertThat(bodies).containsExactly("first comment", "second comment");

        // edit → edited marker
        mockMvc.perform(patchJson("/api/v1/comments/" + c1, admin, "{\"body\":\"first comment (edited)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("first comment (edited)"))
                .andExpect(jsonPath("$.data.edited").value(true))
                .andExpect(jsonPath("$.data.editedAt").isNotEmpty());

        // delete
        mockMvc.perform(delete("/api/v1/comments/" + c1).cookie(admin)).andExpect(status().isNoContent());
        MvcResult after = mockMvc.perform(get("/api/v1/tasks/" + task + "/comments").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(after).get("data").size()).isEqualTo(1);
    }

    // --- activity stream: comments + system events merged chronologically ----

    @Test
    void activityStreamMergesCommentsAndSystemEvents() throws Exception {
        Cookie[] admin = bootstrap();
        UUID adminId = me(admin);
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Launch", teamId);
        UUID task = createTask(admin, projectId, null, "Ship it");

        // system events: assignee change, due change, completion
        mockMvc.perform(patchJson("/api/v1/tasks/" + task, admin, "{\"assigneeId\":\"" + adminId + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patchJson("/api/v1/tasks/" + task, admin, "{\"dueDate\":\"2026-09-01\"}"))
                .andExpect(status().isOk());
        postComment(admin, task, "looking good");
        mockMvc.perform(patchJson("/api/v1/tasks/" + task, admin, "{\"completed\":true}"))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(get("/api/v1/tasks/" + task + "/activity").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = body(res).get("data");

        List<String> kinds = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        boolean sawComment = false;
        for (JsonNode e : data) {
            kinds.add(e.get("kind").asText());
            if ("comment".equals(e.get("kind").asText())) {
                sawComment = true;
                assertThat(e.get("body").asText()).isEqualTo("looking good");
            } else {
                actions.add(e.get("action").asText());
            }
        }
        // created + assignee_changed + due_changed + completed system events, plus the comment
        assertThat(sawComment).isTrue();
        assertThat(actions)
                .contains("task.created", "task.assignee_changed", "task.due_changed", "task.completed");
        // chronological: createdAt is non-decreasing across the merged stream
        String prev = null;
        for (JsonNode e : data) {
            String ts = e.get("createdAt").asText();
            if (prev != null) {
                assertThat(ts.compareTo(prev)).isGreaterThanOrEqualTo(0);
            }
            prev = ts;
        }
        // the assignee_changed diff carries from/to
        JsonNode assigneeDiff = null;
        for (JsonNode e : data) {
            if ("system".equals(e.get("kind").asText())
                    && "task.assignee_changed".equals(e.get("action").asText())) {
                assigneeDiff = e.get("diff");
            }
        }
        assertThat(assigneeDiff).isNotNull();
        assertThat(assigneeDiff.get("to").asText()).isEqualTo(adminId.toString());
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

    private UUID addSubtask(Cookie[] admin, UUID parentId, String title) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/tasks/" + parentId + "/subtasks", admin,
                        "{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentTaskId").value(parentId.toString()))
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private UUID postComment(Cookie[] admin, UUID taskId, String bodyText) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/tasks/" + taskId + "/comments", admin,
                        "{\"body\":\"" + bodyText + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private List<String> myTaskIds(Cookie[] admin) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/me/tasks").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        List<String> out = new ArrayList<>();
        for (JsonNode n : body(r).get("data")) {
            out.add(n.get("id").asText());
        }
        return out;
    }

    private List<String> taskTitlesInOrder(Cookie[] admin, UUID projectId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        List<String> out = new ArrayList<>();
        for (JsonNode t : body(r).get("data")) {
            out.add(t.get("title").asText());
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
