package app.cairn.api.projects;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A partial project update: each {@code *Set} flag says whether the caller supplied that field (so an
 * explicit {@code null} clears a nullable column while an omitted field is left untouched). Built by
 * {@link ProjectService} from the web DTO; mapped to jOOQ inside {@link ProjectRepository}.
 *
 * <p>{@code current_status}/{@code status_updated_at} are intentionally absent: they are denormalized
 * only by posting a status update (D3), never edited directly through PATCH.
 */
public record ProjectUpdate(
        boolean nameSet, String name,
        boolean descriptionSet, String description,
        boolean colorSet, String color,
        boolean defaultViewSet, String defaultView,
        boolean archivedSet, Boolean archived,
        boolean ownerSet, UUID ownerId,
        boolean teamSet, UUID teamId,
        boolean startDateSet, LocalDate startDate,
        boolean endDateSet, LocalDate endDate) {}
