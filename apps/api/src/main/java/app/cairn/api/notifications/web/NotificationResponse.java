package app.cairn.api.notifications.web;

import app.cairn.api.notifications.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A notification as returned to clients. {@code payload} is the parsed self-contained render object
 * (actor name, verb, object title, snippet, project id) so the inbox can draw a row without extra calls.
 */
public record NotificationResponse(
        UUID id,
        String type,
        UUID actorId,
        boolean read,
        String resourceType,
        UUID resourceId,
        JsonNode payload,
        OffsetDateTime createdAt) {

    public static NotificationResponse from(Notification n, ObjectMapper mapper) {
        JsonNode payload = null;
        if (n.payload() != null) {
            try {
                payload = mapper.readTree(n.payload());
            } catch (Exception ignored) {
                payload = null;
            }
        }
        return new NotificationResponse(
                n.id(), n.type(), n.actorId(), n.read(), n.resourceType(), n.resourceId(), payload, n.createdAt());
    }
}
