package app.cairn.api.projects;

import java.util.UUID;

/**
 * Published synchronously by {@link ProjectService#delete} inside its transaction, before the project
 * (and its sections) are removed. The {@code tasks} module listens and deletes the project's tasks, so
 * the cascade crosses the module boundary via an event rather than a direct service dependency
 * (CLAUDE.md: cross-module comms via {@code ApplicationEventPublisher}).
 */
public record ProjectDeletingEvent(UUID projectId) {}
