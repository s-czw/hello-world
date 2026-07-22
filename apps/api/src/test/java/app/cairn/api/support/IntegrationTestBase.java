package app.cairn.api.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base for {@code @Tag("integration")} tests. Points the datasource at {@code TEST_JDBC_URL} (a real
 * Postgres, default {@code cairn_test}), disables the login rate limit for determinism, and gives each
 * test a pristine schema: Flyway {@code clean+migrate} once on context start, then a truncate before
 * every method. Uses the full Spring Security filter chain via {@link MockMvc}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@Import(IntegrationTestBase.CleanMigrateConfig.class)
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbc;

    private static final Pattern USERINFO =
            Pattern.compile("jdbc:postgresql://([^:/@]+):([^@]+)@(.+)");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        String raw = System.getenv().getOrDefault(
                "TEST_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/cairn_test");
        String user = "cairn";
        String password = "cairn";
        String url = raw;
        Matcher m = USERINFO.matcher(raw);
        if (m.matches()) {
            user = m.group(1);
            password = m.group(2);
            url = "jdbc:postgresql://" + m.group(3);
        }
        final String fUrl = url;
        final String fUser = user;
        final String fPassword = password;
        registry.add("spring.datasource.url", () -> fUrl);
        registry.add("spring.datasource.username", () -> fUser);
        registry.add("spring.datasource.password", () -> fPassword);
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("cairn.auth.ratelimit.enabled", () -> "false");
    }

    @BeforeEach
    void truncateAll() {
        jdbc.execute(
                "TRUNCATE project_status_updates, portfolio_projects, portfolios, tasks, sections, projects,"
                        + " team_members, teams, invites, auth_sessions, memberships, users, organizations"
                        + " RESTART IDENTITY CASCADE");
    }

    // ---- helpers ------------------------------------------------------------

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Cookie[] cookiesOf(MvcResult result) {
        return result.getResponse().getCookies();
    }

    protected String cookieValue(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        return cookie == null ? null : cookie.getValue();
    }

    /** Provides a clean-then-migrate strategy so each context start yields a pristine schema. */
    @TestConfiguration
    static class CleanMigrateConfig {
        @Bean
        FlywayMigrationStrategy cleanMigrateStrategy() {
            return flyway -> {
                FluentConfiguration cfg = org.flywaydb.core.Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .cleanDisabled(false);
                org.flywaydb.core.Flyway f = cfg.load();
                f.clean();
                f.migrate();
            };
        }
    }
}
