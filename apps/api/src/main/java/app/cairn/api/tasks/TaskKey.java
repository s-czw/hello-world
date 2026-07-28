package app.cairn.api.tasks;

import java.util.UUID;

/** An (id, sort_key) pair used by the rebalance path to place a moved task among its siblings. */
public record TaskKey(UUID id, String sortKey) {}
