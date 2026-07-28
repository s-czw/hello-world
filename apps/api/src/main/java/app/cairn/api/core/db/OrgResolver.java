package app.cairn.api.core.db;

import static app.cairn.api.jooq.Tables.ORGANIZATIONS;

import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * Resolves the single organization id for the request (D-028, M1 single-org).
 *
 * <p>Lives in {@code core.db} because it is one of the few places allowed to hold the raw jOOQ
 * {@link DSLContext} (the ArchUnit seam rule). It reads the sole {@code organizations} row without an
 * org filter — that row <em>is</em> the tenant root, so there is nothing to filter by. Before bootstrap
 * there is no org and this returns {@link Optional#empty()}.
 */
@Component
public class OrgResolver {

    private final DSLContext dsl;

    public OrgResolver(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** The single org's id, or empty when the instance has not been bootstrapped yet. */
    public Optional<UUID> soleOrgId() {
        return dsl.select(ORGANIZATIONS.ID)
                .from(ORGANIZATIONS)
                .orderBy(ORGANIZATIONS.CREATED_AT.asc())
                .limit(1)
                .fetchOptional(ORGANIZATIONS.ID);
    }

    /** True when no organization exists yet (first-run / bootstrap needed). */
    public boolean needsBootstrap() {
        return dsl.fetchCount(ORGANIZATIONS) == 0;
    }
}
