package app.cairn.api.portfolios;

import java.time.OffsetDateTime;

/**
 * A project's status is "stale" (treated as "no recent update" in the roll-up) when it has never had a
 * status update or its last update is older than {@value #STALE_AFTER_DAYS} days (M2 contract).
 */
public final class Staleness {

    public static final int STALE_AFTER_DAYS = 7;

    private Staleness() {}

    public static boolean isStale(OffsetDateTime statusUpdatedAt, OffsetDateTime now) {
        if (statusUpdatedAt == null) {
            return true;
        }
        return statusUpdatedAt.isBefore(now.minusDays(STALE_AFTER_DAYS));
    }
}
