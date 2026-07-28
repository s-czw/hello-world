package app.cairn.api.portfolios.web;

import java.util.UUID;

/**
 * Body for {@code POST /portfolios/{id}/projects/{projectId}/move}. Both anchors are optional: send the
 * project id to place after ({@code afterId}) and/or before ({@code beforeId}); omit both to append.
 */
public record MoveProjectRequest(UUID beforeId, UUID afterId) {}
