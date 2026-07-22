package app.cairn.api.portfolios;

import static org.assertj.core.api.Assertions.assertThat;

import app.cairn.api.projects.ProjectStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit coverage of roll-up shaping (header counts) and the staleness rule. */
class RollupSummaryTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 7, 22, 12, 0, 0, 0, ZoneOffset.UTC);

    private static RollupProjectRow row(String status, OffsetDateTime statusUpdatedAt) {
        return new RollupProjectRow(
                UUID.randomUUID(), "P", null, null, null, LocalDate.now(), LocalDate.now(),
                status, statusUpdatedAt, 0, 0);
    }

    // --- staleness ------------------------------------------------------------

    @Test
    void nullStatusUpdateIsStale() {
        assertThat(Staleness.isStale(null, NOW)).isTrue();
    }

    @Test
    void recentUpdateIsNotStale() {
        assertThat(Staleness.isStale(NOW.minusDays(6), NOW)).isFalse();
        assertThat(Staleness.isStale(NOW, NOW)).isFalse();
    }

    @Test
    void exactlySevenDaysIsNotStaleButOlderIs() {
        // "older than 7 days" is strict: exactly 7d ago still counts as a recent update.
        assertThat(Staleness.isStale(NOW.minusDays(7), NOW)).isFalse();
        assertThat(Staleness.isStale(NOW.minusDays(7).minusSeconds(1), NOW)).isTrue();
        assertThat(Staleness.isStale(NOW.minusDays(8), NOW)).isTrue();
    }

    // --- summary shaping ------------------------------------------------------

    @Test
    void countsByStatusWithStaleAndNullFoldedIntoNoUpdate() {
        List<RollupProjectRow> rows = List.of(
                row(ProjectStatus.ON_TRACK, NOW.minusDays(1)),
                row(ProjectStatus.AT_RISK, NOW.minusDays(2)),
                row(ProjectStatus.OFF_TRACK, NOW.minusHours(3)),
                row(ProjectStatus.ON_HOLD, NOW),
                row(ProjectStatus.ON_TRACK, NOW.minusDays(10)), // fresh status value but STALE → noUpdate
                row(null, null)); // never updated → noUpdate

        RollupSummary s = RollupSummary.of(rows, NOW);

        assertThat(s.total()).isEqualTo(6);
        assertThat(s.onTrack()).isEqualTo(1);
        assertThat(s.atRisk()).isEqualTo(1);
        assertThat(s.offTrack()).isEqualTo(1);
        assertThat(s.onHold()).isEqualTo(1);
        assertThat(s.noUpdate()).isEqualTo(2);
    }

    @Test
    void emptyPortfolioSummaryIsAllZero() {
        RollupSummary s = RollupSummary.of(List.of(), NOW);
        assertThat(s.total()).isZero();
        assertThat(s.onTrack()).isZero();
        assertThat(s.atRisk()).isZero();
        assertThat(s.offTrack()).isZero();
        assertThat(s.onHold()).isZero();
        assertThat(s.noUpdate()).isZero();
    }
}
