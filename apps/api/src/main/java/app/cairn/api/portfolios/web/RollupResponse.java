package app.cairn.api.portfolios.web;

import app.cairn.api.portfolios.PortfolioRollup;
import app.cairn.api.portfolios.RollupSummary;
import app.cairn.api.portfolios.Staleness;
import java.util.List;

/**
 * The roll-up as returned to clients: the portfolio, the header summary counts, and one row per member
 * project (in portfolio order). Each row carries a server-computed {@code stale} flag (using the same
 * {@code now} the summary used) so the web layer can grey stale/no-update status chips consistently.
 */
public record RollupResponse(
        PortfolioResponse portfolio, RollupSummary summary, List<RollupProjectResponse> projects) {

    public static RollupResponse from(PortfolioRollup r) {
        List<RollupProjectResponse> rows = r.projects().stream()
                .map(row -> new RollupProjectResponse(
                        row.projectId(),
                        row.name(),
                        row.color(),
                        row.ownerId(),
                        row.ownerName(),
                        row.startDate(),
                        row.endDate(),
                        row.currentStatus(),
                        row.statusUpdatedAt(),
                        Staleness.isStale(row.statusUpdatedAt(), r.now()),
                        row.tasksTotal(),
                        row.tasksComplete()))
                .toList();
        return new RollupResponse(PortfolioResponse.from(r.portfolio()), r.summary(), rows);
    }
}
