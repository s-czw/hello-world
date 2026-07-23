package app.cairn.api.projects.event;

import java.util.UUID;

/**
 * Domain events published by {@code ProjectStatusUpdateService}. The {@code notifications} module (F1,
 * type 4) consumes {@link StatusUpdatePosted} to notify the project owner and the owners of every
 * portfolio that contains the project (excluding the actor). Published within the posting transaction so
 * a transactional (after-commit) listener sees the committed update.
 */
public final class ProjectStatusUpdateEvents {

    private ProjectStatusUpdateEvents() {}

    /**
     * A project status update was posted. {@code snippet} is a short plain-text preview (the update's
     * title, or a slice of its body) for a self-contained notification payload.
     */
    public record StatusUpdatePosted(
            UUID projectId, UUID actorId, String status, String snippet) {}
}
