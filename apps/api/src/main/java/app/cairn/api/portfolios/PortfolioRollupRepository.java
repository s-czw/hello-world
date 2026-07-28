package app.cairn.api.portfolios;

import static app.cairn.api.jooq.Tables.PORTFOLIO_PROJECTS;
import static app.cairn.api.jooq.Tables.PROJECTS;
import static app.cairn.api.jooq.Tables.TASKS;
import static app.cairn.api.jooq.Tables.USERS;

import app.cairn.api.core.db.OrgScopedDsl;
import java.util.List;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * The portfolio roll-up read model. A single grouped aggregate query joins each member project to its
 * owner and its task counts, so the roll-up costs one round trip regardless of how many projects or
 * tasks it spans (no N+1). Task counts come from a {@code LEFT JOIN} + {@code COUNT(... ) FILTER}, so a
 * project with no tasks still returns a row with zero counts. Ordered by the portfolio's own sort_key.
 *
 * <p>Uses the {@code db.dsl()} escape hatch for the multi-table join (the established pattern for joins
 * in this codebase), but every org-scoped table is explicitly constrained to the current org id, so the
 * org-filter seam still holds.
 */
@Repository
public class PortfolioRollupRepository {

    private final OrgScopedDsl db;

    public PortfolioRollupRepository(OrgScopedDsl db) {
        this.db = db;
    }

    public List<RollupProjectRow> rollup(UUID portfolioId) {
        UUID org = db.orgId();
        Field<Integer> tasksTotal = DSL.count(TASKS.ID).as("tasks_total");
        Field<Integer> tasksComplete =
                DSL.count(TASKS.ID).filterWhere(TASKS.COMPLETED.isTrue()).as("tasks_complete");

        return db.dsl()
                .select(
                        PROJECTS.ID,
                        PROJECTS.NAME,
                        PROJECTS.COLOR,
                        PROJECTS.OWNER_ID,
                        USERS.NAME,
                        PROJECTS.START_DATE,
                        PROJECTS.END_DATE,
                        PROJECTS.CURRENT_STATUS,
                        PROJECTS.STATUS_UPDATED_AT,
                        tasksTotal,
                        tasksComplete)
                .from(PORTFOLIO_PROJECTS)
                .join(PROJECTS)
                .on(PROJECTS.ID.eq(PORTFOLIO_PROJECTS.PROJECT_ID).and(PROJECTS.ORGANIZATION_ID.eq(org)))
                .leftJoin(USERS)
                .on(USERS.ID.eq(PROJECTS.OWNER_ID))
                .leftJoin(TASKS)
                .on(TASKS.PROJECT_ID.eq(PROJECTS.ID).and(TASKS.ORGANIZATION_ID.eq(org)))
                .where(PORTFOLIO_PROJECTS.PORTFOLIO_ID.eq(portfolioId))
                .and(PORTFOLIO_PROJECTS.ORGANIZATION_ID.eq(org))
                .groupBy(
                        PROJECTS.ID,
                        PROJECTS.NAME,
                        PROJECTS.COLOR,
                        PROJECTS.OWNER_ID,
                        USERS.NAME,
                        PROJECTS.START_DATE,
                        PROJECTS.END_DATE,
                        PROJECTS.CURRENT_STATUS,
                        PROJECTS.STATUS_UPDATED_AT,
                        PORTFOLIO_PROJECTS.SORT_KEY)
                .orderBy(PORTFOLIO_PROJECTS.SORT_KEY.asc(), PROJECTS.ID.asc())
                .fetch(r -> new RollupProjectRow(
                        r.get(PROJECTS.ID),
                        r.get(PROJECTS.NAME),
                        r.get(PROJECTS.COLOR),
                        r.get(PROJECTS.OWNER_ID),
                        r.get(USERS.NAME),
                        r.get(PROJECTS.START_DATE),
                        r.get(PROJECTS.END_DATE),
                        r.get(PROJECTS.CURRENT_STATUS),
                        r.get(PROJECTS.STATUS_UPDATED_AT),
                        r.get(tasksTotal),
                        r.get(tasksComplete)));
    }
}
