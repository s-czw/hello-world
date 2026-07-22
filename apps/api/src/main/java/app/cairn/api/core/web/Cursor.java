package app.cairn.api.core.web;

import app.cairn.api.core.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Opaque keyset cursor over a UUIDv7 id (arch §4: cursor = base64 of the sort tuple).
 *
 * <p>Because ids are UUIDv7 (time-ordered), the id alone is a stable keyset for created-order
 * pagination in M1. Offset pagination is banned (CLAUDE.md invariant).
 */
public final class Cursor {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private Cursor() {}

    /** Clamp a caller-supplied limit into {@code [1, MAX_LIMIT]}, applying the default for null. */
    public static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /** Encode a UUID keyset value into an opaque cursor, or null for "no more pages". */
    public static String encode(UUID id) {
        if (id == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Decode an opaque cursor back into its UUID keyset value; 400 on a malformed cursor. */
    public static UUID decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }
}
