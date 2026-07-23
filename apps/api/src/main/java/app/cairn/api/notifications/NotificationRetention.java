package app.cairn.api.notifications;

import java.time.OffsetDateTime;

/**
 * Pure retention-cutoff logic for the notifications purge (F1). Kept separate from the scheduled job and
 * the DB so it can be unit-tested against a fixed clock without waiting for a real schedule to fire.
 *
 * <p>The cutoff is {@code now - retentionDays}; a notification is purged when it was created
 * <em>strictly before</em> the cutoff (a notification exactly at the cutoff instant is retained).
 */
public final class NotificationRetention {

    private NotificationRetention() {}

    /** The timestamp before which notifications are purged, given the current time and retention window. */
    public static OffsetDateTime cutoff(OffsetDateTime now, int retentionDays) {
        return now.minusDays(retentionDays);
    }

    /** Whether a notification created at {@code createdAt} should be purged as of {@code now}. */
    public static boolean shouldPurge(OffsetDateTime createdAt, OffsetDateTime now, int retentionDays) {
        return createdAt.isBefore(cutoff(now, retentionDays));
    }
}
