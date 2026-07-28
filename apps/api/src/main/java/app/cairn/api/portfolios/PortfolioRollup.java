package app.cairn.api.portfolios;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The assembled roll-up for one portfolio: the portfolio itself, its member-project rows (in portfolio
 * order), the header summary counts, and the {@code now} instant used to compute staleness (so the web
 * layer greys stale chips against the same clock the summary used).
 */
public record PortfolioRollup(
        Portfolio portfolio, List<RollupProjectRow> projects, RollupSummary summary, OffsetDateTime now) {}
