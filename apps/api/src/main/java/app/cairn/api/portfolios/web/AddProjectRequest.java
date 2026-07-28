package app.cairn.api.portfolios.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body for {@code POST /portfolios/{id}/projects}. */
public record AddProjectRequest(@NotNull UUID projectId) {}
