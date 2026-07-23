package app.cairn.api.tasks.web;

import java.util.UUID;

/**
 * Body for {@code POST /tasks/{id}/promote}. Optional target section (null → the promoted task lands in
 * no section, appended to the project's flat list). The body itself is optional on the request.
 */
public record PromoteTaskRequest(UUID sectionId) {}
