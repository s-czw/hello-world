package app.cairn.api.projects.web;

import java.util.UUID;

/**
 * Body for {@code POST /sections/{id}/move}. The moved section is placed after {@code afterSectionId}
 * and/or before {@code beforeSectionId} (both optional; omit both to append to the end).
 */
public record MoveSectionRequest(UUID beforeSectionId, UUID afterSectionId) {}
