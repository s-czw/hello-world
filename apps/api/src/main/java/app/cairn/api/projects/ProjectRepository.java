package app.cairn.api.projects;

import static app.cairn.api.jooq.Tables.PROJECTS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.ProjectsRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code projects}; every query goes through {@link OrgScopedDsl}. */
@Repository
public class ProjectRepository {

    private final OrgScopedDsl db;

    public ProjectRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static final RecordMapper<org.jooq.Record, Project> TO_PROJECT = r -> new Project(
            r.get(PROJECTS.ID),
            r.get(PROJECTS.TEAM_ID),
            r.get(PROJECTS.OWNER_ID),
            r.get(PROJECTS.NAME),
            r.get(PROJECTS.DESCRIPTION),
            r.get(PROJECTS.COLOR),
            r.get(PROJECTS.DEFAULT_VIEW),
            Boolean.TRUE.equals(r.get(PROJECTS.ARCHIVED)),
            r.get(PROJECTS.START_DATE),
            r.get(PROJECTS.END_DATE),
            r.get(PROJECTS.CURRENT_STATUS),
            r.get(PROJECTS.STATUS_UPDATED_AT),
            r.get(PROJECTS.CREATED_AT));

    public UUID insert(
            UUID teamId,
            UUID ownerId,
            String name,
            String description,
            String color,
            String defaultView,
            LocalDate startDate,
            LocalDate endDate) {
        ProjectsRecord rec = db.newRecord(PROJECTS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setTeamId(teamId);
        rec.setOwnerId(ownerId);
        rec.setName(name);
        rec.setDescription(description);
        rec.setColor(color);
        rec.setDefaultView(defaultView);
        rec.setArchived(false);
        rec.setStartDate(startDate);
        rec.setEndDate(endDate);
        rec.insert();
        return id;
    }

    public Optional<Project> findById(UUID id) {
        return db.selectFrom(PROJECTS).and(PROJECTS.ID.eq(id)).fetchOptional(TO_PROJECT);
    }

    public boolean exists(UUID id) {
        return db.dsl().fetchExists(db.selectFrom(PROJECTS).and(PROJECTS.ID.eq(id)));
    }

    /** One keyset page (by id asc = created order); archived projects excluded unless requested. */
    public List<Project> page(UUID afterId, int limit, boolean includeArchived) {
        Condition keyset = afterId == null ? DSL.noCondition() : PROJECTS.ID.gt(afterId);
        Condition archived = includeArchived ? DSL.noCondition() : PROJECTS.ARCHIVED.isFalse();
        return db.selectFrom(PROJECTS)
                .and(keyset)
                .and(archived)
                .orderBy(PROJECTS.ID.asc())
                .limit(limit)
                .fetch(TO_PROJECT);
    }

    public boolean existsByTeam(UUID teamId) {
        return db.dsl().fetchExists(db.selectFrom(PROJECTS).and(PROJECTS.TEAM_ID.eq(teamId)));
    }

    public Map<UUID, ProjectMeta> metaByIds(Collection<UUID> ids) {
        Map<UUID, ProjectMeta> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        db.selectFrom(PROJECTS)
                .and(PROJECTS.ID.in(ids))
                .fetch()
                .forEach(r -> out.put(r.getId(), new ProjectMeta(r.getId(), r.getName(), r.getColor())));
        return out;
    }

    public int update(UUID id, ProjectUpdate u) {
        Map<org.jooq.Field<?>, Object> changes = new LinkedHashMap<>();
        if (u.nameSet()) {
            changes.put(PROJECTS.NAME, u.name());
        }
        if (u.descriptionSet()) {
            changes.put(PROJECTS.DESCRIPTION, u.description());
        }
        if (u.colorSet()) {
            changes.put(PROJECTS.COLOR, u.color());
        }
        if (u.defaultViewSet()) {
            changes.put(PROJECTS.DEFAULT_VIEW, u.defaultView());
        }
        if (u.archivedSet()) {
            changes.put(PROJECTS.ARCHIVED, u.archived());
        }
        if (u.ownerSet()) {
            changes.put(PROJECTS.OWNER_ID, u.ownerId());
        }
        if (u.teamSet()) {
            changes.put(PROJECTS.TEAM_ID, u.teamId());
        }
        if (u.startDateSet()) {
            changes.put(PROJECTS.START_DATE, u.startDate());
        }
        if (u.endDateSet()) {
            changes.put(PROJECTS.END_DATE, u.endDate());
        }
        if (changes.isEmpty()) {
            return exists(id) ? 1 : 0;
        }
        return db.dsl()
                .update(PROJECTS)
                .set(changes)
                .where(db.orgFilter(PROJECTS))
                .and(PROJECTS.ID.eq(id))
                .execute();
    }

    /** Denormalize the project's current status + timestamp (written in the status-update tx, D3). */
    public int updateStatus(UUID id, String status, java.time.OffsetDateTime statusUpdatedAt) {
        return db.dsl()
                .update(PROJECTS)
                .set(PROJECTS.CURRENT_STATUS, status)
                .set(PROJECTS.STATUS_UPDATED_AT, statusUpdatedAt)
                .where(db.orgFilter(PROJECTS))
                .and(PROJECTS.ID.eq(id))
                .execute();
    }

    public int delete(UUID id) {
        return db.dsl()
                .deleteFrom(PROJECTS)
                .where(db.orgFilter(PROJECTS))
                .and(PROJECTS.ID.eq(id))
                .execute();
    }
}
