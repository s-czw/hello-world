package app.cairn.api.notifications;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An in-app notification row (F1). One notification is owned by exactly one {@code recipientId}. The
 * {@code payload} is a self-contained JSON string (actor name, verb, object title, snippet, and the
 * project id for deep-linking) so the inbox can render each row without extra lookups. {@code type} is
 * one of the six trigger kinds (see {@link NotificationType}); {@code resourceType}/{@code resourceId}
 * point at the task or project the notification is about.
 */
public record Notification(
        UUID id,
        UUID recipientId,
        String type,
        UUID actorId,
        String payload,
        String resourceType,
        UUID resourceId,
        boolean read,
        OffsetDateTime createdAt) {}
