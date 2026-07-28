package app.cairn.api.portfolios;

import java.util.UUID;

/** A member project's id + its fractional {@code sort_key} within a portfolio (for move/rebalance). */
public record PortfolioProjectRef(UUID projectId, String sortKey) {}
