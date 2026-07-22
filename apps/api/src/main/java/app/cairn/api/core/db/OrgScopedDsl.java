package app.cairn.api.core.db;

import app.cairn.api.core.org.OrgContext;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.Table;
import org.jooq.TableRecord;
import org.jooq.UpdatableRecord;
import org.springframework.stereotype.Component;

/**
 * The org-filter seam (D-028, CLAUDE.md invariant).
 *
 * <p>This is the <em>only</em> class outside {@code core.db} that should ever touch the raw jOOQ
 * {@link DSLContext} — an ArchUnit rule enforces it. Every tenant-scoped read/write flows through
 * these helpers, which constrain by {@code organization_id} using the current {@link OrgContext},
 * so no query can silently bypass the org filter.
 */
@Component
public class OrgScopedDsl {

    private final DSLContext dsl;
    private final OrgContext org;

    public OrgScopedDsl(DSLContext dsl, OrgContext org) {
        this.dsl = dsl;
        this.org = org;
    }

    /** The current request's organization id. */
    public UUID orgId() {
        return org.required();
    }

    @SuppressWarnings("unchecked")
    private Field<UUID> orgField(Table<?> table) {
        Field<?> field = table.field("organization_id");
        if (field == null) {
            throw new IllegalArgumentException(
                    "Table " + table.getName() + " has no organization_id column; it is not org-scoped");
        }
        return (Field<UUID>) field;
    }

    /** {@code organization_id = :currentOrg} for the given table. */
    public Condition orgFilter(Table<?> table) {
        return orgField(table).eq(orgId());
    }

    /** {@code SELECT * FROM table WHERE organization_id = :currentOrg}. */
    public <R extends Record> SelectConditionStep<R> selectFrom(Table<R> table) {
        return dsl.selectFrom(table).where(orgFilter(table));
    }

    /** Stamp the current org id onto a record before insert, then return it for chaining. */
    public <R extends TableRecord<R>> R stampOrg(R record) {
        record.set(orgField(record.getTable()), orgId());
        return record;
    }

    /** Attach a freshly-built record to this DSL so {@code store()/update()/delete()} work. */
    public <R extends UpdatableRecord<R>> R attach(R record) {
        dsl.attach(record);
        return record;
    }

    /** A new detached record for the given table, pre-stamped with the current org id. */
    public <R extends TableRecord<R>> R newRecord(Table<R> table) {
        return stampOrg(dsl.newRecord(table));
    }

    /**
     * Escape hatch for statements that genuinely need the raw context (e.g. transaction blocks in
     * {@code core.db}). Intentionally package-private-ish: callers still live in {@code core.db}.
     */
    public DSLContext dsl() {
        return dsl;
    }
}
