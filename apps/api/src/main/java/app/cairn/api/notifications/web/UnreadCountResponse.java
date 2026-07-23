package app.cairn.api.notifications.web;

/** Body of {@code GET /notifications/unread-count}: the caller's unread notification count. */
public record UnreadCountResponse(int count) {}
