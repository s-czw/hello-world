package app.cairn.api.portfolios;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.core.error.ConflictException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.projects.ProjectDeletingEvent;
import app.cairn.api.projects.ProjectService;
import app.cairn.api.tasks.ordering.Ordering;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portfolios domain service (D1): CRUD, membership (add/remove/reorder member projects), and the
 * roll-up. Any member may manage portfolios (spec §2.1 — no per-object roles). Deleting a portfolio
 * removes its membership rows but never the member projects. Removing a project from every portfolio is
 * also the project-delete cascade so the FK never blocks a project deletion.
 */
@Service
public class PortfolioService {

    private final PortfolioRepository portfolios;
    private final PortfolioProjectRepository members;
    private final PortfolioRollupRepository rollups;
    private final ProjectService projects;
    private final MembershipService memberships;

    public PortfolioService(
            PortfolioRepository portfolios,
            PortfolioProjectRepository members,
            PortfolioRollupRepository rollups,
            ProjectService projects,
            MembershipService memberships) {
        this.portfolios = portfolios;
        this.members = members;
        this.rollups = rollups;
        this.projects = projects;
        this.memberships = memberships;
    }

    public List<Portfolio> list(UUID afterId, int limit) {
        return portfolios.page(afterId, limit);
    }

    public Portfolio get(UUID id) {
        return portfolios.findById(id).orElseThrow(() -> NotFoundException.of("Portfolio"));
    }

    private void requirePortfolio(UUID id) {
        if (!portfolios.exists(id)) {
            throw NotFoundException.of("Portfolio");
        }
    }

    @Transactional
    public Portfolio create(
            AuthPrincipal caller, String name, String description, String color, UUID ownerId) {
        UUID owner = ownerId != null ? ownerId : caller.userId();
        requireMember(owner);
        String sortKey = Ordering.after(portfolios.maxSortKey().orElse(null));
        UUID id = portfolios.insert(owner, name.trim(), trimToNull(description), trimToNull(color), sortKey);
        return portfolios.findById(id).orElseThrow();
    }

    @Transactional
    public Portfolio update(UUID id, PortfolioUpdate u) {
        requirePortfolio(id);
        if (u.ownerSet() && u.ownerId() != null) {
            requireMember(u.ownerId());
        }
        portfolios.update(id, u);
        return portfolios.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(UUID id) {
        requirePortfolio(id);
        members.deleteByPortfolio(id); // remove membership rows only; never the member projects
        portfolios.delete(id);
    }

    // --- membership ----------------------------------------------------------

    @Transactional
    public void addProject(UUID portfolioId, UUID projectId) {
        requirePortfolio(portfolioId);
        projects.requireProject(projectId);
        if (members.exists(portfolioId, projectId)) {
            throw new ConflictException("Project is already in this portfolio");
        }
        String sortKey = Ordering.after(members.maxSortKey(portfolioId).orElse(null));
        members.insert(portfolioId, projectId, sortKey);
    }

    @Transactional
    public void removeProject(UUID portfolioId, UUID projectId) {
        requirePortfolio(portfolioId);
        members.delete(portfolioId, projectId); // idempotent; never touches the project itself
    }

    /** Reorder a member project between the given neighbours (each optional), reusing fractional keys. */
    @Transactional
    public void moveProject(UUID portfolioId, UUID projectId, UUID beforeProjectId, UUID afterProjectId) {
        requirePortfolio(portfolioId);
        if (members.sortKeyOf(portfolioId, projectId).isEmpty()) {
            throw NotFoundException.of("Portfolio project");
        }
        List<PortfolioProjectRef> ordered = new ArrayList<>(members.listOrdered(portfolioId));
        ordered.removeIf(r -> r.projectId().equals(projectId));

        String afterKey;
        String beforeKey;
        if (afterProjectId != null && beforeProjectId != null) {
            afterKey = keyOf(ordered, afterProjectId);
            beforeKey = keyOf(ordered, beforeProjectId);
        } else if (afterProjectId != null) {
            int idx = indexOf(ordered, afterProjectId);
            afterKey = idx >= 0 ? ordered.get(idx).sortKey() : null;
            beforeKey = idx >= 0 && idx + 1 < ordered.size() ? ordered.get(idx + 1).sortKey() : null;
        } else if (beforeProjectId != null) {
            int idx = indexOf(ordered, beforeProjectId);
            beforeKey = idx >= 0 ? ordered.get(idx).sortKey() : null;
            afterKey = idx - 1 >= 0 ? ordered.get(idx - 1).sortKey() : null;
        } else {
            afterKey = ordered.isEmpty() ? null : ordered.get(ordered.size() - 1).sortKey();
            beforeKey = null;
        }

        String newKey = Ordering.between(afterKey, beforeKey);
        if (Ordering.needsRebalance(newKey)) {
            rebalanceAndPlace(portfolioId, projectId, ordered, afterKey, beforeKey);
        } else {
            members.updateSortKey(portfolioId, projectId, newKey);
        }
    }

    private void rebalanceAndPlace(
            UUID portfolioId, UUID projectId, List<PortfolioProjectRef> ordered, String afterKey, String beforeKey) {
        int insertAt = ordered.size();
        for (int i = 0; i < ordered.size(); i++) {
            if (beforeKey != null && ordered.get(i).sortKey().equals(beforeKey)) {
                insertAt = i;
                break;
            }
            if (afterKey != null && ordered.get(i).sortKey().equals(afterKey)) {
                insertAt = i + 1;
            }
        }
        List<UUID> ids = new ArrayList<>(ordered.stream().map(PortfolioProjectRef::projectId).toList());
        ids.add(insertAt, projectId);
        List<String> keys = Ordering.rebalance(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            members.updateSortKey(portfolioId, ids.get(i), keys.get(i));
        }
    }

    // --- roll-up -------------------------------------------------------------

    public PortfolioRollup rollup(UUID portfolioId) {
        Portfolio portfolio = get(portfolioId);
        List<RollupProjectRow> rows = rollups.rollup(portfolioId);
        OffsetDateTime now = OffsetDateTime.now();
        return new PortfolioRollup(portfolio, rows, RollupSummary.of(rows, now), now);
    }

    // --- project-delete cascade ----------------------------------------------

    /** When a project is deleted, drop its portfolio memberships (sync, same tx) so the FK is clear. */
    @EventListener
    public void onProjectDeleting(ProjectDeletingEvent event) {
        members.deleteByProject(event.projectId());
    }

    // --- helpers -------------------------------------------------------------

    private void requireMember(UUID userId) {
        if (memberships.findByUserId(userId).isEmpty()) {
            throw new NotFoundException("User " + userId + " is not a member of this organization");
        }
    }

    private static int indexOf(List<PortfolioProjectRef> ordered, UUID projectId) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).projectId().equals(projectId)) {
                return i;
            }
        }
        return -1;
    }

    private static String keyOf(List<PortfolioProjectRef> ordered, UUID projectId) {
        int idx = indexOf(ordered, projectId);
        return idx >= 0 ? ordered.get(idx).sortKey() : null;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
