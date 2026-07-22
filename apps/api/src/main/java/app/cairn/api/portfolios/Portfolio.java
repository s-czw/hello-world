package app.cairn.api.portfolios;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A portfolio: a cross-team grouping of projects (D1). The roll-up reads its member projects. */
public record Portfolio(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        String color,
        String sortKey,
        OffsetDateTime createdAt) {}
