package app.cairn.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Pure retention-cutoff logic for the notifications purge (F1) — tested against a fixed clock. */
class NotificationRetentionTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 7, 23, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void cutoffIsNowMinusRetentionDays() {
        assertThat(NotificationRetention.cutoff(NOW, 90)).isEqualTo(NOW.minusDays(90));
        assertThat(NotificationRetention.cutoff(NOW, 30)).isEqualTo(NOW.minusDays(30));
        assertThat(NotificationRetention.cutoff(NOW, 0)).isEqualTo(NOW);
    }

    @Test
    void configurableWindowChangesTheCutoff() {
        // A shorter retention window purges more (a more recent cutoff).
        assertThat(NotificationRetention.cutoff(NOW, 7)).isAfter(NotificationRetention.cutoff(NOW, 90));
    }

    @Test
    void purgesStrictlyOlderThanCutoffOnly() {
        int days = 90;
        OffsetDateTime cutoff = NotificationRetention.cutoff(NOW, days);

        // Just older than the cutoff → purge.
        assertThat(NotificationRetention.shouldPurge(cutoff.minusSeconds(1), NOW, days)).isTrue();
        // Ancient → purge.
        assertThat(NotificationRetention.shouldPurge(NOW.minusDays(365), NOW, days)).isTrue();

        // Exactly at the cutoff → retain (strict "before").
        assertThat(NotificationRetention.shouldPurge(cutoff, NOW, days)).isFalse();
        // Just newer than the cutoff → retain.
        assertThat(NotificationRetention.shouldPurge(cutoff.plusSeconds(1), NOW, days)).isFalse();
        // Brand new → retain.
        assertThat(NotificationRetention.shouldPurge(NOW, NOW, days)).isFalse();
    }
}
