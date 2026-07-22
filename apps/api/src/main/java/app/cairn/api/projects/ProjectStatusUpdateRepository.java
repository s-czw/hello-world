package app.cairn.api.projects;

import static app.cairn.api.jooq.Tables.PROJECT_STATUS_UPDATES;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.ProjectStatusUpdatesRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code project_status_updates}; append-only history, newest first. */
@Repository
public class ProjectStatusUpdateRepository {

    private final OrgScopedDsl db;

    public ProjectStatusUpdateRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static final RecordMapper<org.jooq.Record, ProjectStatusUpdate> TO_UPDATE = r -> new ProjectStatusUpdate(
            r.get(PROJECT_STATUS_UPDATES.ID),
            r.get(PROJECT_STATUS_UPDATES.PROJECT_ID),
            r.get(PROJECT_STATUS_UPDATES.AUTHOR_ID),
            r.get(PROJECT_STATUS_UPDATES.STATUS),
            r.get(PROJECT_STATUS_UPDATES.TITLE),
            r.get(PROJECT_STATUS_UPDATES.BODY),
            r.get(PROJECT_STATUS_UPDATES.CREATED_AT));

    public UUID insert(UUID projectId, UUID authorId, String status, String title, String body) {
        ProjectStatusUpdatesRecord rec = db.newRecord(PROJECT_STATUS_UPDATES);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setProjectId(projectId);
        rec.setAuthorId(authorId);
        rec.setStatus(status);
        rec.setTitle(title);
        rec.setBody(body);
        rec.insert();
        return id;
    }

    public Optional<ProjectStatusUpdate> findById(UUID id) {
        return db.selectFrom(PROJECT_STATUS_UPDATES)
                .and(PROJECT_STATUS_UPDATES.ID.eq(id))
                .fetchOptional(TO_UPDATE);
    }

    /**
     * One keyset page of a project's history, newest first. The cursor is the UUIDv7 id (time-ordered),
     * so paging by {@code id < :after} descending matches the {@code (created_at desc)} display order.
     */
    public List<ProjectStatusUpdate> pageByProject(UUID projectId, UUID afterId, int limit) {
        Condition keyset = afterId == null ? DSL.noCondition() : PROJECT_STATUS_UPDATES.ID.lt(afterId);
        return db.selectFrom(PROJECT_STATUS_UPDATES)
                .and(PROJECT_STATUS_UPDATES.PROJECT_ID.eq(projectId))
                .and(keyset)
                .orderBy(PROJECT_STATUS_UPDATES.CREATED_AT.desc(), PROJECT_STATUS_UPDATES.ID.desc())
                .limit(limit)
                .fetch(TO_UPDATE);
    }

    /** Delete a project's status-update history (project-delete cascade). */
    public int deleteByProject(UUID projectId) {
        return db.dsl()
                .deleteFrom(PROJECT_STATUS_UPDATES)
                .where(db.orgFilter(PROJECT_STATUS_UPDATES))
                .and(PROJECT_STATUS_UPDATES.PROJECT_ID.eq(projectId))
                .execute();
    }
}
