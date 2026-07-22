package app.cairn.api.core.org;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped holder for the single organization id (D-028).
 *
 * <p>M1 is single-org: the id is resolved once per request (from the authenticated session, and
 * otherwise from the sole {@code organizations} row) and stashed here. Every tenant-scoped query
 * reads it through {@link app.cairn.api.core.db.OrgScopedDsl}, which applies the
 * {@code organization_id} filter — the seam RLS will reinforce at commercialization.
 */
@Component
@RequestScope
public class OrgContext {

    private UUID orgId;

    public void set(UUID orgId) {
        this.orgId = orgId;
    }

    public Optional<UUID> current() {
        return Optional.ofNullable(orgId);
    }

    /** The resolved org id, or throws if the request never resolved one. */
    public UUID required() {
        if (orgId == null) {
            throw new IllegalStateException("Organization context not resolved for this request");
        }
        return orgId;
    }
}
