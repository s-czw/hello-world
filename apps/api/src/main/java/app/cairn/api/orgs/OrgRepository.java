package app.cairn.api.orgs;

import static app.cairn.api.jooq.Tables.ORGANIZATIONS;
import static app.cairn.api.jooq.Tables.TEAMS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.TeamsRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * The tenant-root {@code organizations} table (no {@code organization_id} to filter by — this row is
 * the tenant), plus the bootstrap "General" team insert. Uses the {@link OrgScopedDsl} escape hatch
 * for the root table; the team insert goes through the org helper so it is stamped.
 */
@Repository
public class OrgRepository {

    private final OrgScopedDsl db;

    public OrgRepository(OrgScopedDsl db) {
        this.db = db;
    }

    /** Insert the single organization, minting a UUIDv7 id, and return it. */
    public UUID insertOrganization(String name) {
        UUID id = Uuid7.generate();
        db.dsl().insertInto(ORGANIZATIONS)
                .set(ORGANIZATIONS.ID, id)
                .set(ORGANIZATIONS.NAME, name)
                .execute();
        return id;
    }

    public Optional<String> nameById(UUID id) {
        return db.dsl()
                .select(ORGANIZATIONS.NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ID.eq(id))
                .fetchOptional(ORGANIZATIONS.NAME);
    }

    /** Create the default "General" team for a freshly bootstrapped org; returns its id. */
    public UUID insertTeam(String name) {
        TeamsRecord rec = db.newRecord(TEAMS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setName(name);
        rec.insert();
        return id;
    }
}
