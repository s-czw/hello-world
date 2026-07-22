package app.cairn.api.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mints and verifies the first-party {@code cairn_access} token: a compact HS256 JWT (arch §4.3).
 *
 * <p>Hand-rolled (no external JWT dependency) because HS256 is small and fully testable: header
 * {@code {"alg":"HS256","typ":"JWT"}}, JSON claims, URL-safe base64 (no padding), HMAC-SHA256 over
 * {@code header.payload}. Verification uses a constant-time MAC comparison and enforces {@code exp}.
 * The signing secret comes from {@code AUTH_JWT_SECRET} (a dev-only default exists; production must
 * override with >= 32 bytes).
 */
@Service
public class JwtService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = base64Json(Map.of("alg", "HS256", "typ", "JWT"));

    private final ObjectMapper mapper = new ObjectMapper();
    private final byte[] secret;
    private final long accessTtlSeconds;

    public JwtService(
            @Value("${cairn.auth.jwt.secret}") String secret,
            @Value("${cairn.auth.jwt.access-ttl-seconds}") long accessTtlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    /** Issue an access token for a principal, expiring {@code accessTtlSeconds} from now. */
    public String issueAccessToken(UUID userId, UUID orgId, String role, String email) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId.toString());
        claims.put("org", orgId.toString());
        claims.put("role", role);
        claims.put("email", email);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(accessTtlSeconds).getEpochSecond());
        String payload = base64Json(claims);
        String signingInput = HEADER + "." + payload;
        return signingInput + "." + B64.encodeToString(hmac(signingInput));
    }

    /**
     * Verify signature and expiry, returning the claims. Throws {@link JwtException} on any tampering,
     * malformed structure, bad signature, or expiry.
     */
    public AccessClaims verify(String token) {
        if (token == null) {
            throw new JwtException("missing token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("malformed token");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput);
        byte[] provided;
        try {
            provided = B64D.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtException("malformed signature");
        }
        if (!java.security.MessageDigest.isEqual(expected, provided)) {
            throw new JwtException("bad signature");
        }
        Map<String, Object> claims;
        try {
            claims = mapper.readValue(B64D.decode(parts[1]), Map.class);
        } catch (Exception e) {
            throw new JwtException("malformed claims");
        }
        long exp = asLong(claims.get("exp"));
        if (Instant.now().getEpochSecond() >= exp) {
            throw new JwtException("token expired");
        }
        try {
            return new AccessClaims(
                    UUID.fromString((String) claims.get("sub")),
                    UUID.fromString((String) claims.get("org")),
                    (String) claims.get("role"),
                    (String) claims.get("email"));
        } catch (RuntimeException e) {
            throw new JwtException("invalid claims");
        }
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        throw new JwtException("invalid exp claim");
    }

    private static String base64Json(Map<String, ?> value) {
        try {
            return B64.encodeToString(new ObjectMapper().writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize JWT segment", e);
        }
    }

    /** Verified access-token claims. */
    public record AccessClaims(UUID userId, UUID orgId, String role, String email) {}
}
