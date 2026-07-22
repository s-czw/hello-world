package app.cairn.api.tasks.web;

import java.util.UUID;

/**
 * Body for {@code POST /tasks/{id}/move}. The task is placed into {@code sectionId} (null = the
 * no-section group), after {@code afterTaskId} and/or before {@code beforeTaskId} (both optional;
 * omit both to append to the end of the section). The server computes the fractional sort key.
 */
public record MoveTaskRequest(UUID sectionId, UUID beforeTaskId, UUID afterTaskId) {}
