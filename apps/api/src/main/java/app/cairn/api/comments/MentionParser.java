package app.cairn.api.comments;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses @mentions out of a plain-text comment body (D-011 — no rich text). Two forms are recognised:
 *
 * <ul>
 *   <li>{@code @<uuid>} — mention by user id (the unambiguous form the web typeahead inserts), and
 *   <li>{@code @<email>} — mention by email address.
 * </ul>
 *
 * The parser is pure and side-effect free; {@link CommentService} resolves the extracted candidates
 * against the current org's members (unknown or non-member candidates are dropped there).
 */
public final class MentionParser {

    private static final Pattern UUID_MENTION = Pattern.compile(
            "@([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private static final Pattern EMAIL_MENTION = Pattern.compile(
            "@([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    private MentionParser() {}

    /** Extracted mention candidates: user ids and lower-cased email addresses (both de-duplicated). */
    public record Mentions(Set<UUID> userIds, Set<String> emails) {
        public boolean isEmpty() {
            return userIds.isEmpty() && emails.isEmpty();
        }
    }

    public static Mentions parse(String body) {
        Set<UUID> ids = new LinkedHashSet<>();
        Set<String> emails = new LinkedHashSet<>();
        if (body == null || body.isEmpty()) {
            return new Mentions(ids, emails);
        }
        Matcher u = UUID_MENTION.matcher(body);
        while (u.find()) {
            try {
                ids.add(UUID.fromString(u.group(1)));
            } catch (IllegalArgumentException ignored) {
                // not a valid UUID after all — skip
            }
        }
        Matcher e = EMAIL_MENTION.matcher(body);
        while (e.find()) {
            emails.add(e.group(1).toLowerCase());
        }
        return new Mentions(ids, emails);
    }
}
