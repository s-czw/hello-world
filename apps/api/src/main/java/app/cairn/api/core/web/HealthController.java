package app.cairn.api.core.web;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness ({@code /healthz}) and readiness ({@code /readyz}) probes.
 * Readiness verifies the DB connection is valid.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> readyz() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            if (valid) {
                return ResponseEntity.ok(Map.of("status", "ready"));
            }
        } catch (Exception ignored) {
            // fall through to 503
        }
        return ResponseEntity.status(503).body(Map.of("status", "unavailable"));
    }
}
