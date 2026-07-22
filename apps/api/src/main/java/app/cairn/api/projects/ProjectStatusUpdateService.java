package app.cairn.api.projects;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.core.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project status updates (D3). Posting an update appends a history row and denormalizes the project's
 * {@code current_status} + {@code status_updated_at} in the <em>same transaction</em>, so the portfolio
 * roll-up can read a project's status without a per-project subquery. History is newest-first; "Copy
 * previous update" is a client convenience over {@link #history}.
 */
@Service
public class ProjectStatusUpdateService {

    private final ProjectStatusUpdateRepository updates;
    private final ProjectRepository projectRepo;
    private final ProjectService projects;

    public ProjectStatusUpdateService(
            ProjectStatusUpdateRepository updates, ProjectRepository projectRepo, ProjectService projects) {
        this.updates = updates;
        this.projectRepo = projectRepo;
        this.projects = projects;
    }

    public List<ProjectStatusUpdate> history(UUID projectId, UUID afterId, int limit) {
        projects.requireProject(projectId);
        return updates.pageByProject(projectId, afterId, limit);
    }

    @Transactional
    public ProjectStatusUpdate create(
            AuthPrincipal caller, UUID projectId, String status, String title, String body) {
        projects.requireProject(projectId);
        if (!ProjectStatus.isValid(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "status must be on_track|at_risk|off_track|on_hold");
        }
        UUID id = updates.insert(projectId, caller.userId(), status, trimToNull(title), trimToNull(body));
        ProjectStatusUpdate created = updates.findById(id).orElseThrow();
        // Denormalize onto the project in the same transaction (D3), keeping the two timestamps equal.
        projectRepo.updateStatus(projectId, created.status(), created.createdAt());
        return created;
    }

    /** Drop a project's status-update history when the project is deleted (sync, same tx, before the FK). */
    @EventListener
    public void onProjectDeleting(ProjectDeletingEvent event) {
        updates.deleteByProject(event.projectId());
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
