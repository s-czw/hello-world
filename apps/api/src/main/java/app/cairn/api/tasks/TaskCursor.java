package app.cairn.api.tasks;

import app.cairn.api.core.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Composite keyset cursor for the grouped project task list, ordered by
 * {@code (section sort_key, task sort_key, created_at, id)}. Encodes the four keyset values of the last
 * row of a page; the id is the final, unique tiebreak so the cursor is a total order. Offset pagination
 * is banned (CLAUDE.md); this is the keyset equivalent for the multi-column order.
 */
public record TaskCursor(String sectionSortKey, String taskSortKey, OffsetDateTime createdAt, UUID id) {

    private static final char SEP = '\n'; // absent from base-62 sort keys, ISO timestamps, and UUIDs

    public String encode() {
        String raw = sectionSortKey + SEP + taskSortKey + SEP + createdAt.toString() + SEP + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static TaskCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(String.valueOf(SEP), -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("bad cursor arity");
            }
            return new TaskCursor(parts[0], parts[1], OffsetDateTime.parse(parts[2]), UUID.fromString(parts[3]));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }
}
