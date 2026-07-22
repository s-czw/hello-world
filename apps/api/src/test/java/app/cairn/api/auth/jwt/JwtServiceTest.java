package app.cairn.api.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";
    private final JwtService jwt = new JwtService(SECRET, 900);

    @Test
    void issuesAndVerifiesRoundTrip() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        String token = jwt.issueAccessToken(userId, orgId, "admin", "ada@acme.test");
        JwtService.AccessClaims claims = jwt.verify(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.orgId()).isEqualTo(orgId);
        assertThat(claims.role()).isEqualTo("admin");
        assertThat(claims.email()).isEqualTo("ada@acme.test");
    }

    @Test
    void rejectsTamperedPayload() {
        String token = jwt.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), "member", "x@y.z");
        String[] parts = token.split("\\.");
        // Flip a character in the payload segment.
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThatThrownBy(() -> jwt.verify(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongSignature() {
        String token = jwt.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), "member", "x@y.z");
        JwtService other = new JwtService("a-completely-different-signing-secret-value", 900);

        assertThatThrownBy(() -> other.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService expiring = new JwtService(SECRET, -10); // exp in the past
        String token = expiring.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), "admin", "x@y.z");

        assertThatThrownBy(() -> expiring.verify(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwt.verify("not-a-jwt")).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.verify(null)).isInstanceOf(JwtException.class);
    }
}
