package app.cairn.api.notifications;

import app.cairn.api.core.db.NotificationRetentionPurger;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled 90-day retention purge for in-app notifications (F1). Runs in-process (no worker container)
 * on the cron in {@code cairn.notifications.purge-cron}; the retention window
 * ({@code cairn.notifications.retention-days}) and an on/off switch ({@code cairn.notifications.purge-enabled})
 * are configurable. The cutoff math is the pure, unit-tested {@link NotificationRetention}; the delete is
 * the raw-DSL {@link NotificationRetentionPurger} (a global maintenance sweep, not a tenant query).
 */
@Component
public class NotificationPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationPurgeJob.class);

    private final NotificationRetentionPurger purger;
    private final int retentionDays;
    private final boolean enabled;

    public NotificationPurgeJob(
            NotificationRetentionPurger purger,
            @Value("${cairn.notifications.retention-days:90}") int retentionDays,
            @Value("${cairn.notifications.purge-enabled:true}") boolean enabled) {
        this.purger = purger;
        this.retentionDays = retentionDays;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${cairn.notifications.purge-cron:0 30 3 * * *}")
    public void purge() {
        if (!enabled) {
            return;
        }
        OffsetDateTime cutoff = NotificationRetention.cutoff(OffsetDateTime.now(), retentionDays);
        int removed = purger.purgeOlderThan(cutoff);
        if (removed > 0) {
            log.info("Purged {} notification(s) older than {} ({}-day retention)", removed, cutoff, retentionDays);
        }
    }
}
