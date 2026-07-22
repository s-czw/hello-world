package app.cairn.api.portfolios;

import static app.cairn.api.jooq.Tables.PORTFOLIOS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.PortfoliosRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code portfolios}; every query goes through {@link OrgScopedDsl}. */
@Repository
public class PortfolioRepository {

    private final OrgScopedDsl db;

    public PortfolioRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static final RecordMapper<org.jooq.Record, Portfolio> TO_PORTFOLIO = r -> new Portfolio(
            r.get(PORTFOLIOS.ID),
            r.get(PORTFOLIOS.OWNER_ID),
            r.get(PORTFOLIOS.NAME),
            r.get(PORTFOLIOS.DESCRIPTION),
            r.get(PORTFOLIOS.COLOR),
            r.get(PORTFOLIOS.SORT_KEY),
            r.get(PORTFOLIOS.CREATED_AT));

    public UUID insert(UUID ownerId, String name, String description, String color, String sortKey) {
        PortfoliosRecord rec = db.newRecord(PORTFOLIOS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setOwnerId(ownerId);
        rec.setName(name);
        rec.setDescription(description);
        rec.setColor(color);
        rec.setSortKey(sortKey);
        rec.insert();
        return id;
    }

    public Optional<Portfolio> findById(UUID id) {
        return db.selectFrom(PORTFOLIOS).and(PORTFOLIOS.ID.eq(id)).fetchOptional(TO_PORTFOLIO);
    }

    public boolean exists(UUID id) {
        return db.dsl().fetchExists(db.selectFrom(PORTFOLIOS).and(PORTFOLIOS.ID.eq(id)));
    }

    /** One keyset page by id asc (created order); ids are UUIDv7 so this is stable created-order. */
    public List<Portfolio> page(UUID afterId, int limit) {
        Condition keyset = afterId == null ? DSL.noCondition() : PORTFOLIOS.ID.gt(afterId);
        return db.selectFrom(PORTFOLIOS)
                .and(keyset)
                .orderBy(PORTFOLIOS.ID.asc())
                .limit(limit)
                .fetch(TO_PORTFOLIO);
    }

    public Optional<String> maxSortKey() {
        return Optional.ofNullable(db.dsl()
                .select(DSL.max(PORTFOLIOS.SORT_KEY))
                .from(PORTFOLIOS)
                .where(db.orgFilter(PORTFOLIOS))
                .fetchOne(0, String.class));
    }

    public int update(UUID id, PortfolioUpdate u) {
        Map<Field<?>, Object> changes = new LinkedHashMap<>();
        if (u.nameSet()) {
            changes.put(PORTFOLIOS.NAME, u.name());
        }
        if (u.descriptionSet()) {
            changes.put(PORTFOLIOS.DESCRIPTION, u.description());
        }
        if (u.colorSet()) {
            changes.put(PORTFOLIOS.COLOR, u.color());
        }
        if (u.ownerSet()) {
            changes.put(PORTFOLIOS.OWNER_ID, u.ownerId());
        }
        if (changes.isEmpty()) {
            return exists(id) ? 1 : 0;
        }
        return db.dsl()
                .update(PORTFOLIOS)
                .set(changes)
                .where(db.orgFilter(PORTFOLIOS))
                .and(PORTFOLIOS.ID.eq(id))
                .execute();
    }

    public int delete(UUID id) {
        return db.dsl()
                .deleteFrom(PORTFOLIOS)
                .where(db.orgFilter(PORTFOLIOS))
                .and(PORTFOLIOS.ID.eq(id))
                .execute();
    }
}
