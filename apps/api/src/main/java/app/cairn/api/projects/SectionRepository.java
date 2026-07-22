package app.cairn.api.projects;

import static app.cairn.api.jooq.Tables.SECTIONS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.SectionsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.RecordMapper;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code sections}; every query goes through {@link OrgScopedDsl}. */
@Repository
public class SectionRepository {

    private final OrgScopedDsl db;

    public SectionRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static final RecordMapper<org.jooq.Record, Section> TO_SECTION = r -> new Section(
            r.get(SECTIONS.ID),
            r.get(SECTIONS.PROJECT_ID),
            r.get(SECTIONS.NAME),
            r.get(SECTIONS.SORT_KEY),
            r.get(SECTIONS.CREATED_AT));

    public UUID insert(UUID projectId, String name, String sortKey) {
        SectionsRecord rec = db.newRecord(SECTIONS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setProjectId(projectId);
        rec.setName(name);
        rec.setSortKey(sortKey);
        rec.insert();
        return id;
    }

    public Optional<Section> findById(UUID id) {
        return db.selectFrom(SECTIONS).and(SECTIONS.ID.eq(id)).fetchOptional(TO_SECTION);
    }

    /** Sections of a project in display order (sort_key, then created order as tiebreak). */
    public List<Section> listByProject(UUID projectId) {
        return db.selectFrom(SECTIONS)
                .and(SECTIONS.PROJECT_ID.eq(projectId))
                .orderBy(SECTIONS.SORT_KEY.asc(), SECTIONS.CREATED_AT.asc(), SECTIONS.ID.asc())
                .fetch(TO_SECTION);
    }

    /** The greatest sort_key among a project's sections (for append), or empty when none. */
    public Optional<String> maxSortKey(UUID projectId) {
        return Optional.ofNullable(db.dsl()
                .select(org.jooq.impl.DSL.max(SECTIONS.SORT_KEY))
                .from(SECTIONS)
                .where(db.orgFilter(SECTIONS))
                .and(SECTIONS.PROJECT_ID.eq(projectId))
                .fetchOne(0, String.class));
    }

    public boolean existsInProject(UUID sectionId, UUID projectId) {
        return db.dsl()
                .fetchExists(db.selectFrom(SECTIONS)
                        .and(SECTIONS.ID.eq(sectionId))
                        .and(SECTIONS.PROJECT_ID.eq(projectId)));
    }

    public int updateName(UUID id, String name) {
        return db.dsl()
                .update(SECTIONS)
                .set(SECTIONS.NAME, name)
                .where(db.orgFilter(SECTIONS))
                .and(SECTIONS.ID.eq(id))
                .execute();
    }

    public int updateSortKey(UUID id, String sortKey) {
        return db.dsl()
                .update(SECTIONS)
                .set(SECTIONS.SORT_KEY, sortKey)
                .where(db.orgFilter(SECTIONS))
                .and(SECTIONS.ID.eq(id))
                .execute();
    }

    public int delete(UUID id) {
        return db.dsl()
                .deleteFrom(SECTIONS)
                .where(db.orgFilter(SECTIONS))
                .and(SECTIONS.ID.eq(id))
                .execute();
    }

    public int deleteByProject(UUID projectId) {
        return db.dsl()
                .deleteFrom(SECTIONS)
                .where(db.orgFilter(SECTIONS))
                .and(SECTIONS.PROJECT_ID.eq(projectId))
                .execute();
    }
}
