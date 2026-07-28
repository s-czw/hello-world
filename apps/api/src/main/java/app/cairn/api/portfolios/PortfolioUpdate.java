package app.cairn.api.portfolios;

import java.util.UUID;

/**
 * A partial portfolio update: each {@code *Set} flag says whether the caller supplied that field, so an
 * explicit {@code null} clears a nullable column while an omitted field is left untouched.
 */
public record PortfolioUpdate(
        boolean nameSet, String name,
        boolean descriptionSet, String description,
        boolean colorSet, String color,
        boolean ownerSet, UUID ownerId) {}
