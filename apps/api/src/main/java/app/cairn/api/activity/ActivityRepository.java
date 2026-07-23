package app.cairn.api.activity;

import static app.cairn.api.jooq.Tables.ACTIVITY_LOG;

import app.cairn.api.core.db.OrgScopedDsl;
import java.util.List;
import java.util.UUID;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code activity_log}; every query goes through {@link OrgScopedDsl}. */
@Repository
public class ActivityRepository {

    public static final String RESOURCE_TASK = "task";

    private final OrgScopedDsl db;

    public ActivityRepository(OrgScopedDsl db) {
        this.db = db;
    }

    /** Append a system event. {@code diffJson} may be null. id is a DB-assigned bigserial. */
    public void insert(UUID actorId, String action, String resourceType, UUID resourceId, String diffJson) {
        var rec = db.newRecord(ACTIVITY_LOG);
        rec.setActorId(actorId);
        rec.setAction(action);
        rec.setResourceType(resourceType);
        rec.setResourceId(resourceId);
        rec.setDiff(diffJson == null ? null : JSONB.valueOf(diffJson));
        rec.insert();
    }

    /** Activity for a task, oldest first (for the merged stream). */
    public List<ActivityEntry> listByTask(UUID taskId) {
        return db.selectFrom(ACTIVITY_LOG)
                .and(ACTIVITY_LOG.RESOURCE_TYPE.eq(RESOURCE_TASK))
                .and(ACTIVITY_LOG.RESOURCE_ID.eq(taskId))
                .orderBy(ACTIVITY_LOG.CREATED_AT.asc(), ACTIVITY_LOG.ID.asc())
                .fetch(r -> new ActivityEntry(
                        r.get(ACTIVITY_LOG.ID),
                        r.get(ACTIVITY_LOG.ACTOR_ID),
                        r.get(ACTIVITY_LOG.ACTION),
                        r.get(ACTIVITY_LOG.RESOURCE_TYPE),
                        r.get(ACTIVITY_LOG.RESOURCE_ID),
                        r.get(ACTIVITY_LOG.DIFF) == null ? null : r.get(ACTIVITY_LOG.DIFF).data(),
                        r.get(ACTIVITY_LOG.CREATED_AT)));
    }

    public int deleteByTask(UUID taskId) {
        return db.dsl()
                .deleteFrom(ACTIVITY_LOG)
                .where(db.orgFilter(ACTIVITY_LOG))
                .and(ACTIVITY_LOG.RESOURCE_TYPE.eq(RESOURCE_TASK))
                .and(ACTIVITY_LOG.RESOURCE_ID.eq(taskId))
                .execute();
    }
}
