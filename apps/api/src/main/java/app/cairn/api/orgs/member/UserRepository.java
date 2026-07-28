package app.cairn.api.orgs.member;

import static app.cairn.api.jooq.Tables.USERS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * The global {@code users} table (users are not org-scoped; the org linkage is via {@code memberships}).
 * Accesses go through {@link OrgScopedDsl}; because this table carries no {@code organization_id}, it
 * uses the sanctioned raw-context escape hatch — never an org filter it does not have.
 */
@Repository
public class UserRepository {

    private final OrgScopedDsl db;

    public UserRepository(OrgScopedDsl db) {
        this.db = db;
    }

    /** Case-insensitive existence check against the {@code lower(email)} unique index. */
    public boolean emailExists(String email) {
        return db.dsl().fetchExists(
                db.dsl().selectOne().from(USERS).where(USERS.EMAIL.equalIgnoreCase(email)));
    }

    /** Insert a new user, minting a UUIDv7 id, and return it. */
    public UUID insert(String email, String name, String passwordHash) {
        UUID id = Uuid7.generate();
        db.dsl().insertInto(USERS)
                .set(USERS.ID, id)
                .set(USERS.EMAIL, email)
                .set(USERS.NAME, name)
                .set(USERS.PASSWORD_HASH, passwordHash)
                .execute();
        return id;
    }
}
