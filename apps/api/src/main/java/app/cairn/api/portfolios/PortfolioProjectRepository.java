package app.cairn.api.portfolios;

import static app.cairn.api.jooq.Tables.PORTFOLIO_PROJECTS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.jooq.tables.records.PortfolioProjectsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code portfolio_projects} (portfolio membership + ordering). */
@Repository
public class PortfolioProjectRepository {

    private final OrgScopedDsl db;

    public PortfolioProjectRepository(OrgScopedDsl db) {
        this.db = db;
    }

    public void insert(UUID portfolioId, UUID projectId, String sortKey) {
        PortfolioProjectsRecord rec = db.newRecord(PORTFOLIO_PROJECTS);
        rec.setPortfolioId(portfolioId);
        rec.setProjectId(projectId);
        rec.setSortKey(sortKey);
        rec.insert();
    }

    public boolean exists(UUID portfolioId, UUID projectId) {
        return db.dsl()
                .fetchExists(db.selectFrom(PORTFOLIO_PROJECTS)
                        .and(PORTFOLIO_PROJECTS.PORTFOLIO_ID.eq(portfolioId))
                        .and(PORTFOLIO_PROJECTS.PROJECT_ID.eq(projectId)));
    }

    public Optional<String> sortKeyOf(UUID portfolioId, UUID projectId) {
        return db.selectFrom(PORTFOLIO_PROJECTS)
                .and(PORTFOLIO_PROJECTS.PORTFOLIO_ID.eq(portfolioId))
                .and(PORTFOLIO_PROJECTS.PROJECT_ID.eq(projectId))
                .fetchOptional(PORTFOLIO_PROJECTS.SORT_KEY);
    }

    public Optional<String> maxSortKey(UUID portfolioId) {
        return Optional.ofNullable(db.dsl()
                .select(DSL.max(PORTFOLIO_PROJECTS.SORT_KEY))
                .from(PORTFOLIO_PROJECTS)
                .where(db.orgFilter(PORTFOLIO_PROJECTS))
                .and(PORTFOLIO_PROJECTS.PORTFOLIO_ID.eq(portfolioId))
                .fetchOne(0, String.class));
    }

    /** Member rows of a portfolio in display order (sort_key, then project id as a stable tiebreak). */
    public List<PortfolioProjectRef> listOrdered(UUID portfolioId) {
        return db.selectFrom(PORTFOLIO_PROJECTS)
                .and(PORTFOLIO_PROJECTS.PORTFOLIO_ID.eq(portfolioId))
                .orderBy(PORTFOLIO_PROJECTS.SORT_KEY.asc(), PORTFOLIO_PROJECTS.PROJECT_ID.asc())
                .fetch(r -> new PortfolioProjectRef(r.getProjectId(), r.getSortKey()));
    }

    public int updateSortKey(UUID portfolioId, UUID projectId, String sortKey) {
        return db.dsl()
                .update(PORTFOLIO_PROJECTS)
                .set(PORTFOLIO_PROJECTS.SORT_KEY, sortKey)
                .where(db.orgFilter(PORTFOLIO_PROJECTS))
                .and(PORTFOLIO_PROJECTS.PORTFOLIO_ID.eq(portfolioId))
                .and(PORTFOLIO_PROJECTS.PROJECT_ID.eq(projectId))
                .execute();
    }

    public int delete(UUID portfolioId, UUID projectId) {
        return db.dsl()
                .deleteFrom(PORTFOLIO_PROJECTS)
                .where(db.orgFilter(PORTFOLIO_PROJECTS))
                .and(PORTFOLIO_PROJECTS.PORTFOLIO_ID.eq(portfolioId))
                .and(PORTFOLIO_PROJECTS.PROJECT_ID.eq(projectId))
                .execute();
    }

    public int deleteByPortfolio(UUID portfolioId) {
        return db.dsl()
                .deleteFrom(PORTFOLIO_PROJECTS)
                .where(db.orgFilter(PORTFOLIO_PROJECTS))
                .and(PORTFOLIO_PROJECTS.PORTFOLIO_ID.eq(portfolioId))
                .execute();
    }

    /** Remove a project from every portfolio it belongs to (project-delete cascade; never deletes the project). */
    public int deleteByProject(UUID projectId) {
        return db.dsl()
                .deleteFrom(PORTFOLIO_PROJECTS)
                .where(db.orgFilter(PORTFOLIO_PROJECTS))
                .and(PORTFOLIO_PROJECTS.PROJECT_ID.eq(projectId))
                .execute();
    }
}
