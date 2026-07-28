package app.cairn.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.cairn.api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Attachments (C4): upload → authz'd download, plus oversize + dangerous-type rejection. */
class AttachmentsIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        String dir = System.getProperty("java.io.tmpdir") + "/cairn-attach-test-" + UUID.randomUUID();
        registry.add("cairn.storage.dir", () -> dir);
        // small cap so an oversize upload is rejected without a huge test payload
        registry.add("cairn.attachments.max-size-bytes", () -> "100");
    }

    @Test
    void uploadThenDownloadThroughAuthz() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Launch", teamId);
        UUID task = createTask(admin, projectId, "Has files");

        byte[] content = "hello cairn".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", content);

        MvcResult up = mockMvc.perform(multipart("/api/v1/tasks/" + task + "/attachments")
                        .file(file)
                        .cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("notes.txt"))
                .andExpect(jsonPath("$.data.contentType").value("text/plain"))
                .andExpect(jsonPath("$.data.sizeBytes").value(content.length))
                .andReturn();
        UUID attId = UUID.fromString(body(up).get("data").get("id").asText());

        // listed under the task
        mockMvc.perform(get("/api/v1/tasks/" + task + "/attachments").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(attId.toString()));

        // download streams the bytes with a nosniff + attachment disposition
        MvcResult dl = mockMvc.perform(get("/api/v1/attachments/" + attId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        assertThat(dl.getResponse().getContentAsByteArray()).isEqualTo(content);

        // authz: no cookie → 401; unknown id → 404
        mockMvc.perform(get("/api/v1/attachments/" + attId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/attachments/" + UUID.randomUUID()).cookie(admin))
                .andExpect(status().isNotFound());

        // delete → 204, then download 404
        mockMvc.perform(delete("/api/v1/attachments/" + attId).cookie(admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/attachments/" + attId).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void oversizeUploadRejected() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Launch", teamId);
        UUID task = createTask(admin, projectId, "Big");

        byte[] big = new byte[200]; // exceeds the 100-byte test cap
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", big);
        mockMvc.perform(multipart("/api/v1/tasks/" + task + "/attachments").file(file).cookie(admin))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void dangerousTypesRejected() throws Exception {
        Cookie[] admin = bootstrap();
        UUID teamId = generalTeamId();
        UUID projectId = createProject(admin, "Launch", teamId);
        UUID task = createTask(admin, projectId, "Danger");

        // disallowed extension (.html) → 415
        MockMultipartFile html = new MockMultipartFile(
                "file", "index.html", "text/html", "<h1>hi</h1>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/tasks/" + task + "/attachments").file(html).cookie(admin))
                .andExpect(status().isUnsupportedMediaType());

        // allowed extension (.png) but HTML/script content → rejected by the content sniff (415)
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "evil.png", "image/png",
                "<!DOCTYPE html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/tasks/" + task + "/attachments").file(disguised).cookie(admin))
                .andExpect(status().isUnsupportedMediaType());
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

    private UUID createTask(Cookie[] admin, UUID projectId, String title) throws Exception {
        MvcResult r = mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/tasks", admin,
                        "{\"title\":\"" + title + "\",\"sectionId\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(body(r).get("data").get("id").asText());
    }

    private MockHttpServletRequestBuilder postJson(String url, Cookie[] cookies, String body) {
        return post(url).cookie(cookies).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
