package app.cairn.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Emits the committed {@code apps/api/openapi.json} by booting the springdoc-backed web context and
 * fetching the generated document, then re-serializing it deterministically (map keys sorted, LF
 * newlines, 2-space indent) so the CI client-drift check can regenerate and diff it byte-for-byte.
 *
 * <p>Runs in the normal {@code mvn test} lane and is DB-independent: Flyway is disabled and the
 * datasource pool is configured not to fail if Postgres is unreachable, since generating the spec never
 * touches the database.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.hikari.initialization-fail-timeout=-1",
            "spring.datasource.hikari.minimum-idle=0",
            "cairn.auth.ratelimit.enabled=false"
        })
class OpenApiSpecWriterTest {

    @Autowired private TestRestTemplate rest;

    @Test
    void writesStableSortedOpenApiJson() throws Exception {
        String raw = rest.getForObject("/api/docs", String.class);
        assertThat(raw).as("springdoc served a spec").isNotBlank();

        // Re-serialize deterministically: parse to a generic tree, sort map keys, fixed indentation.
        Object tree = new ObjectMapper().readValue(raw, Object.class);
        ObjectMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        ObjectWriter writer = mapper.writer(printer);
        String pretty = writer.writeValueAsString(tree) + "\n";

        Path out = Paths.get(System.getProperty("cairn.openapi.output", "openapi.json")).toAbsolutePath();
        Files.writeString(out, pretty, StandardCharsets.UTF_8);

        assertThat(Files.exists(out)).isTrue();
        assertThat(Files.size(out)).isGreaterThan(0);
        assertThat(pretty).contains("\"openapi\"");
        // spot-check that our M1 surfaces made it into the document
        assertThat(pretty).contains("/api/v1/me/tasks");
        assertThat(pretty).contains("/api/v1/tasks/{id}/move");
        assertThat(pretty).contains("/api/v1/teams");
        System.out.println("[openapi] wrote " + out + " (" + Files.size(out) + " bytes)");
    }
}
