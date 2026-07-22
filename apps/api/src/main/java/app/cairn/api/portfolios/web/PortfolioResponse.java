package app.cairn.api.portfolios.web;

import app.cairn.api.portfolios.Portfolio;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A portfolio as returned to clients. */
public record PortfolioResponse(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        String color,
        OffsetDateTime createdAt) {

    public static PortfolioResponse from(Portfolio p) {
        return new PortfolioResponse(
                p.id(), p.ownerId(), p.name(), p.description(), p.color(), p.createdAt());
    }
}
