package app.cairn.api.projects;

import java.util.UUID;

/**
 * A partial project update: each {@code *Set} flag says whether the caller supplied that field (so an
 * explicit {@code null} clears a nullable column while an omitted field is left untouched). Built by
 * {@link ProjectService} from the web DTO; mapped to jOOQ inside {@link ProjectRepository}.
 */
public record ProjectUpdate(
        boolean nameSet, String name,
        boolean descriptionSet, String description,
        boolean colorSet, String color,
        boolean defaultViewSet, String defaultView,
        boolean archivedSet, Boolean archived,
        boolean ownerSet, UUID ownerId,
        boolean teamSet, UUID teamId) {}
