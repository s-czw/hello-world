package app.cairn.api.teams;

import app.cairn.api.core.error.ConflictException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.projects.ProjectService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teams domain service. Any member may create/edit teams and manage their membership (spec §2.1:
 * lead/owner confer no permissions; roles are org-level). A team can only be deleted once it owns no
 * projects — the guard consults the projects module through its public service.
 */
@Service
public class TeamService {

    private final TeamRepository teams;
    private final MembershipService memberships;
    private final ProjectService projects;

    public TeamService(
            TeamRepository teams,
            MembershipService memberships,
            @Lazy ProjectService projects) {
        this.teams = teams;
        this.memberships = memberships;
        this.projects = projects;
    }

    public List<Team> list(UUID afterId, int limit) {
        return teams.page(afterId, limit);
    }

    public Map<UUID, List<UUID>> memberIdsByTeam(List<UUID> teamIds) {
        return teams.memberIdsByTeam(teamIds);
    }

    public List<UUID> memberIds(UUID teamId) {
        requireTeam(teamId);
        return teams.memberIds(teamId);
    }

    public Team get(UUID id) {
        return teams.findById(id).orElseThrow(() -> NotFoundException.of("Team"));
    }

    /** Assert a team exists in the current org (used by the projects module on create/update). */
    public void requireTeam(UUID id) {
        if (!teams.exists(id)) {
            throw NotFoundException.of("Team");
        }
    }

    @Transactional
    public Team create(String name, String description) {
        UUID id = teams.insert(name.trim(), trimToNull(description));
        return teams.findById(id).orElseThrow();
    }

    @Transactional
    public Team update(UUID id, String name, String description) {
        requireTeam(id);
        teams.update(id, name == null ? null : name.trim(), description == null ? null : description.trim());
        return teams.findById(id).orElseThrow();
    }

    /** Delete a team; 409 if it still owns projects (they must be moved or deleted first). */
    @Transactional
    public void delete(UUID id) {
        requireTeam(id);
        if (projects.hasProjectsForTeam(id)) {
            throw new ConflictException("Team still has projects; move or delete them first");
        }
        teams.delete(id);
    }

    /** Replace a team's membership with exactly {@code userIds} (each must be an org member). */
    @Transactional
    public List<UUID> setMembers(UUID teamId, List<UUID> userIds) {
        requireTeam(teamId);
        // de-dup, preserve order, validate each is a member of this org
        LinkedHashSet<UUID> distinct = new LinkedHashSet<>(userIds);
        for (UUID userId : distinct) {
            if (memberships.findByUserId(userId).isEmpty()) {
                throw new NotFoundException("User " + userId + " is not a member of this organization");
            }
        }
        teams.replaceMembers(teamId, List.copyOf(distinct));
        return teams.memberIds(teamId);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
