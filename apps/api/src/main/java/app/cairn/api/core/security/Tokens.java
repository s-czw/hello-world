package app.cairn.api.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque-token helpers for refresh tokens and invite tokens.
 *
 * <p>Tokens are 256 bits of {@link SecureRandom} entropy, URL-safe base64 (no padding). Only their
 * SHA-256 hash is persisted — the plaintext lives only in the cookie / invite link. SHA-256 (not
 * argon2) is appropriate here because the token is already high-entropy and unguessable; the hash
 * merely prevents a DB reader from replaying stored tokens.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL64 = Base64.getUrlEncoder().withoutPadding();

    private Tokens() {}

    /** A fresh 256-bit URL-safe opaque token. */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL64.encodeToString(bytes);
    }

    /** Lower-case hex SHA-256 of the token, suitable for a unique index lookup. */
    public static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
