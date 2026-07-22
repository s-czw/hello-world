package app.cairn.api.portfolios;

import app.cairn.api.projects.ProjectStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The roll-up header counts: how many member projects sit at each status, plus a "no recent update"
 * bucket for projects that have never posted a status or whose last one is stale (&gt;7d). A stale
 * project is counted only in {@code noUpdate}, never in its (now out-of-date) status bucket.
 */
public record RollupSummary(int total, int onTrack, int atRisk, int offTrack, int onHold, int noUpdate) {

    public static RollupSummary of(List<RollupProjectRow> rows, OffsetDateTime now) {
        int onTrack = 0;
        int atRisk = 0;
        int offTrack = 0;
        int onHold = 0;
        int noUpdate = 0;
        for (RollupProjectRow r : rows) {
            if (r.currentStatus() == null || Staleness.isStale(r.statusUpdatedAt(), now)) {
                noUpdate++;
                continue;
            }
            switch (r.currentStatus()) {
                case ProjectStatus.ON_TRACK -> onTrack++;
                case ProjectStatus.AT_RISK -> atRisk++;
                case ProjectStatus.OFF_TRACK -> offTrack++;
                case ProjectStatus.ON_HOLD -> onHold++;
                default -> noUpdate++;
            }
        }
        return new RollupSummary(rows.size(), onTrack, atRisk, offTrack, onHold, noUpdate);
    }
}
