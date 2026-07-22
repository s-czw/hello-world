package app.cairn.api.tasks;

import static app.cairn.api.jooq.Tables.SECTIONS;
import static app.cairn.api.jooq.Tables.TASKS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.TasksRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code tasks}. Ordering is by fractional {@code sort_key}, ties by (created_at, id). */
@Repository
public class TaskRepository {

    private final OrgScopedDsl db;

    public TaskRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static Task toTask(Record r) {
        return new Task(
                r.get(TASKS.ID),
                r.get(TASKS.PROJECT_ID),
                r.get(TASKS.SECTION_ID),
                r.get(TASKS.ASSIGNEE_ID),
                r.get(TASKS.TITLE),
                r.get(TASKS.DESCRIPTION),
                r.get(TASKS.PRIORITY),
                r.get(TASKS.DUE_DATE),
                Boolean.TRUE.equals(r.get(TASKS.COMPLETED)),
                r.get(TASKS.COMPLETED_AT),
                r.get(TASKS.CREATED_BY),
                r.get(TASKS.SORT_KEY),
                r.get(TASKS.CREATED_AT),
                r.get(TASKS.UPDATED_AT));
    }

    private static Condition sectionEq(UUID sectionId) {
        return sectionId == null ? TASKS.SECTION_ID.isNull() : TASKS.SECTION_ID.eq(sectionId);
    }

    public UUID insert(
            UUID projectId,
            UUID sectionId,
            UUID assigneeId,
            String title,
            String description,
            String priority,
            LocalDate dueDate,
            UUID createdBy,
            String sortKey) {
        TasksRecord rec = db.newRecord(TASKS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setProjectId(projectId);
        rec.setSectionId(sectionId);
        rec.setAssigneeId(assigneeId);
        rec.setTitle(title);
        rec.setDescription(description);
        rec.setPriority(priority);
        rec.setDueDate(dueDate);
        rec.setCompleted(false);
        rec.setCreatedBy(createdBy);
        rec.setSortKey(sortKey);
        rec.insert();
        return id;
    }

    public Optional<Task> findById(UUID id) {
        return db.selectFrom(TASKS).and(TASKS.ID.eq(id)).fetchOptional(TaskRepository::toTask);
    }

    // --- grouped project list (ordered by section sort_key, then task sort_key, then created_at, id) ---

    public List<TaskWithSectionKey> pageByProject(UUID projectId, TaskCursor after, int limit) {
        Field<String> secExpr = DSL.coalesce(SECTIONS.SORT_KEY, DSL.val(""));
        Field<String> secSel = secExpr.as("section_sort_key");

        Condition where = db.orgFilter(TASKS).and(TASKS.PROJECT_ID.eq(projectId));
        if (after != null) {
            where = where.and(DSL.row(secExpr, TASKS.SORT_KEY, TASKS.CREATED_AT, TASKS.ID)
                    .gt(DSL.row(
                            DSL.val(after.sectionSortKey()),
                            DSL.val(after.taskSortKey()),
                            DSL.val(after.createdAt()),
                            DSL.val(after.id()))));
        }

        return db.dsl()
                .select(TASKS.fields())
                .select(secSel)
                .from(TASKS)
                .leftJoin(SECTIONS)
                .on(SECTIONS.ID.eq(TASKS.SECTION_ID).and(SECTIONS.ORGANIZATION_ID.eq(db.orgId())))
                .where(where)
                .orderBy(secExpr.asc(), TASKS.SORT_KEY.asc(), TASKS.CREATED_AT.asc(), TASKS.ID.asc())
                .limit(limit)
                .fetch(r -> new TaskWithSectionKey(toTask(r), r.get(secSel)));
    }

    // --- my open tasks (across all projects), due nulls last ------------------

    public List<Task> myOpenTasks(UUID userId) {
        return db.selectFrom(TASKS)
                .and(TASKS.ASSIGNEE_ID.eq(userId))
                .and(TASKS.COMPLETED.isFalse())
                .orderBy(TASKS.DUE_DATE.asc().nullsLast(), TASKS.CREATED_AT.asc(), TASKS.ID.asc())
                .fetch(TaskRepository::toTask);
    }

    // --- neighbour lookups for move (sort_key comparison; ties resolved by the final ORDER BY) ---

    public Optional<String> sortKeyIfInSection(UUID taskId, UUID projectId, UUID sectionId) {
        return db.selectFrom(TASKS)
                .and(TASKS.ID.eq(taskId))
                .and(TASKS.PROJECT_ID.eq(projectId))
                .and(sectionEq(sectionId))
                .fetchOptional(TASKS.SORT_KEY);
    }

    public Optional<String> successorKey(UUID projectId, UUID sectionId, String afterKey, UUID excludeId) {
        return keyAggregate(projectId, sectionId, excludeId, DSL.min(TASKS.SORT_KEY), TASKS.SORT_KEY.gt(afterKey));
    }

    public Optional<String> predecessorKey(UUID projectId, UUID sectionId, String beforeKey, UUID excludeId) {
        return keyAggregate(projectId, sectionId, excludeId, DSL.max(TASKS.SORT_KEY), TASKS.SORT_KEY.lt(beforeKey));
    }

    public Optional<String> maxSortKey(UUID projectId, UUID sectionId, UUID excludeId) {
        return keyAggregate(projectId, sectionId, excludeId, DSL.max(TASKS.SORT_KEY), DSL.noCondition());
    }

    private Optional<String> keyAggregate(
            UUID projectId, UUID sectionId, UUID excludeId, Field<String> agg, Condition extra) {
        Condition where = db.orgFilter(TASKS)
                .and(TASKS.PROJECT_ID.eq(projectId))
                .and(sectionEq(sectionId))
                .and(excludeId == null ? DSL.noCondition() : TASKS.ID.ne(excludeId))
                .and(extra);
        return Optional.ofNullable(db.dsl().select(agg).from(TASKS).where(where).fetchOne(0, String.class));
    }

    public Optional<String> maxSortKeyForSection(UUID sectionId) {
        Condition where = db.orgFilter(TASKS).and(sectionEq(sectionId));
        return Optional.ofNullable(
                db.dsl().select(DSL.max(TASKS.SORT_KEY)).from(TASKS).where(where).fetchOne(0, String.class));
    }

    public List<UUID> orderedTaskIdsInSection(UUID projectId, UUID sectionId, UUID excludeId) {
        return db.selectFrom(TASKS)
                .and(TASKS.PROJECT_ID.eq(projectId))
                .and(sectionEq(sectionId))
                .and(excludeId == null ? DSL.noCondition() : TASKS.ID.ne(excludeId))
                .orderBy(TASKS.SORT_KEY.asc(), TASKS.CREATED_AT.asc(), TASKS.ID.asc())
                .fetch(TASKS.ID);
    }

    public List<TaskKey> orderedIdKeysInSection(UUID projectId, UUID sectionId, UUID excludeId) {
        return db.selectFrom(TASKS)
                .and(TASKS.PROJECT_ID.eq(projectId))
                .and(sectionEq(sectionId))
                .and(excludeId == null ? DSL.noCondition() : TASKS.ID.ne(excludeId))
                .orderBy(TASKS.SORT_KEY.asc(), TASKS.CREATED_AT.asc(), TASKS.ID.asc())
                .fetch(r -> new TaskKey(r.get(TASKS.ID), r.get(TASKS.SORT_KEY)));
    }

    public List<UUID> orderedTaskIdsForSection(UUID sectionId) {
        return db.selectFrom(TASKS)
                .and(sectionEq(sectionId))
                .orderBy(TASKS.SORT_KEY.asc(), TASKS.CREATED_AT.asc(), TASKS.ID.asc())
                .fetch(TASKS.ID);
    }

    public boolean sectionHasTasks(UUID sectionId) {
        return db.dsl().fetchExists(db.selectFrom(TASKS).and(sectionEq(sectionId)));
    }

    // --- mutations ------------------------------------------------------------

    public int update(UUID id, TaskUpdate u) {
        Map<Field<?>, Object> changes = new LinkedHashMap<>();
        if (u.titleSet()) {
            changes.put(TASKS.TITLE, u.title());
        }
        if (u.descriptionSet()) {
            changes.put(TASKS.DESCRIPTION, u.description());
        }
        if (u.assigneeSet()) {
            changes.put(TASKS.ASSIGNEE_ID, u.assigneeId());
        }
        if (u.dueSet()) {
            changes.put(TASKS.DUE_DATE, u.dueDate());
        }
        if (u.prioritySet()) {
            changes.put(TASKS.PRIORITY, u.priority());
        }
        if (u.completedSet()) {
            changes.put(TASKS.COMPLETED, u.completed());
        }
        if (u.completedAtSet()) {
            changes.put(TASKS.COMPLETED_AT, u.completedAt());
        }
        changes.put(TASKS.UPDATED_AT, OffsetDateTime.now());
        return db.dsl()
                .update(TASKS)
                .set(changes)
                .where(db.orgFilter(TASKS))
                .and(TASKS.ID.eq(id))
                .execute();
    }

    /** Set section + sort key together (the move operation). */
    public int moveTo(UUID id, UUID sectionId, String sortKey) {
        return db.dsl()
                .update(TASKS)
                .set(TASKS.SECTION_ID, sectionId)
                .set(TASKS.SORT_KEY, sortKey)
                .set(TASKS.UPDATED_AT, OffsetDateTime.now())
                .where(db.orgFilter(TASKS))
                .and(TASKS.ID.eq(id))
                .execute();
    }

    public void assignKeys(List<UUID> ids, List<String> keys) {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < ids.size(); i++) {
            db.dsl()
                    .update(TASKS)
                    .set(TASKS.SORT_KEY, keys.get(i))
                    .set(TASKS.UPDATED_AT, now)
                    .where(db.orgFilter(TASKS))
                    .and(TASKS.ID.eq(ids.get(i)))
                    .execute();
        }
    }

    public int delete(UUID id) {
        return db.dsl().deleteFrom(TASKS).where(db.orgFilter(TASKS)).and(TASKS.ID.eq(id)).execute();
    }

    public int deleteByProject(UUID projectId) {
        return db.dsl()
                .deleteFrom(TASKS)
                .where(db.orgFilter(TASKS))
                .and(TASKS.PROJECT_ID.eq(projectId))
                .execute();
    }
}
