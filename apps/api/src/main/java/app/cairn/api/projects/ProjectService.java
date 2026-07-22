package app.cairn.api.projects;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.core.error.ApiException;
import app.cairn.api.core.error.ForbiddenException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.projects.web.UpdateProjectRequest;
import app.cairn.api.teams.TeamService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects domain service. Any member may create/edit projects (spec §2.1). Deleting a project is
 * limited to an admin or the project owner (spec §3) and cascades: a {@link ProjectDeletingEvent} lets
 * the tasks module remove the project's tasks, then this service removes the sections and the project.
 */
@Service
public class ProjectService {

    private static final Set<String> VIEWS = Set.of("list", "board");

    private final ProjectRepository projects;
    private final SectionRepository sections;
    private final TeamService teams;
    private final MembershipService memberships;
    private final ApplicationEventPublisher events;

    public ProjectService(
            ProjectRepository projects,
            SectionRepository sections,
            TeamService teams,
            MembershipService memberships,
            ApplicationEventPublisher events) {
        this.projects = projects;
        this.sections = sections;
        this.teams = teams;
        this.memberships = memberships;
        this.events = events;
    }

    public List<Project> list(UUID afterId, int limit, boolean includeArchived) {
        return projects.page(afterId, limit, includeArchived);
    }

    public Project get(UUID id) {
        return projects.findById(id).orElseThrow(() -> NotFoundException.of("Project"));
    }

    /** Assert a project exists in the current org (used by sections/tasks). 404 otherwise. */
    public void requireProject(UUID id) {
        if (!projects.exists(id)) {
            throw NotFoundException.of("Project");
        }
    }

    /** True if any project references the given team (the team-delete guard). */
    public boolean hasProjectsForTeam(UUID teamId) {
        return projects.existsByTeam(teamId);
    }

    public Map<UUID, ProjectMeta> metaByIds(Collection<UUID> ids) {
        return projects.metaByIds(ids);
    }

    /** The three sections every new project starts with (spec B2 AC). */
    private static final List<String> DEFAULT_SECTIONS = List.of("To do", "In progress", "Done");

    @Transactional
    public Project create(
            AuthPrincipal caller,
            String name,
            String description,
            String color,
            UUID teamId,
            UUID ownerId,
            String defaultView) {
        teams.requireTeam(teamId);
        UUID owner = ownerId != null ? ownerId : caller.userId();
        requireMember(owner);
        String view = defaultView == null ? "list" : defaultView;
        requireValidView(view);
        UUID id = projects.insert(teamId, owner, name.trim(), trimToNull(description), trimToNull(color), view);
        seedDefaultSections(id);
        return projects.findById(id).orElseThrow();
    }

    /** Seed the three default sections in order with short, evenly-spaced fractional keys (spec B2). */
    private void seedDefaultSections(UUID projectId) {
        List<String> keys = app.cairn.api.tasks.ordering.Ordering.rebalance(DEFAULT_SECTIONS.size());
        for (int i = 0; i < DEFAULT_SECTIONS.size(); i++) {
            sections.insert(projectId, DEFAULT_SECTIONS.get(i), keys.get(i));
        }
    }

    @Transactional
    public Project update(UUID id, UpdateProjectRequest req) {
        requireProject(id);
        if (req.teamPresent() && req.getTeamId() != null) {
            teams.requireTeam(req.getTeamId());
        }
        if (req.teamPresent() && req.getTeamId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A project must belong to a team");
        }
        if (req.ownerPresent() && req.getOwnerId() != null) {
            requireMember(req.getOwnerId());
        }
        if (req.defaultViewPresent() && req.getDefaultView() != null) {
            requireValidView(req.getDefaultView());
        }
        if (req.startDatePresent() && req.endDatePresent()
                && req.getStartDate() != null && req.getEndDate() != null
                && req.getEndDate().isBefore(req.getStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate");
        }
        ProjectUpdate u = new ProjectUpdate(
                req.namePresent(), req.getName() == null ? null : req.getName().trim(),
                req.descriptionPresent(), trimToNull(req.getDescription()),
                req.colorPresent(), trimToNull(req.getColor()),
                req.defaultViewPresent(), req.getDefaultView(),
                req.archivedPresent(), req.getArchived(),
                req.ownerPresent(), req.getOwnerId(),
                req.teamPresent(), req.getTeamId(),
                req.startDatePresent(), req.getStartDate(),
                req.endDatePresent(), req.getEndDate());
        projects.update(id, u);
        return projects.findById(id).orElseThrow();
    }

    /** Delete a project (admin or owner) and cascade sections + tasks. Confirm is a web concern. */
    @Transactional
    public void delete(AuthPrincipal caller, UUID id) {
        Project project = get(id);
        boolean ownerOrAdmin = caller.isAdmin() || caller.userId().equals(project.ownerId());
        if (!ownerOrAdmin) {
            throw new ForbiddenException("Only an admin or the project owner may delete this project");
        }
        events.publishEvent(new ProjectDeletingEvent(id)); // tasks module deletes the tasks (sync)
        sections.deleteByProject(id);
        projects.delete(id);
    }

    private void requireMember(UUID userId) {
        if (memberships.findByUserId(userId).isEmpty()) {
            throw new NotFoundException("User " + userId + " is not a member of this organization");
        }
    }

    private static void requireValidView(String view) {
        if (!VIEWS.contains(view)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "default_view must be 'list' or 'board'");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
