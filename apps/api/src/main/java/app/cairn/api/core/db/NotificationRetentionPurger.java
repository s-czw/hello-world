package app.cairn.api.core.db;

import static app.cairn.api.jooq.Tables.NOTIFICATIONS;

import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * Cross-org retention purge for {@code notifications} (F1). Lives in {@code core.db} — one of the few
 * places allowed to hold the raw jOOQ {@link DSLContext} (the ArchUnit seam rule) — because the purge
 * runs off-request on a scheduler thread where there is no {@code OrgContext}, and it is a global
 * maintenance sweep by age (all tenants), not a tenant query. The cutoff is computed by the caller
 * ({@code NotificationRetention}); this class only executes the delete.
 */
@Component
public class NotificationRetentionPurger {

    private final DSLContext dsl;

    public NotificationRetentionPurger(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Delete every notification created strictly before {@code cutoff}. Returns the number removed. */
    public int purgeOlderThan(OffsetDateTime cutoff) {
        return dsl.deleteFrom(NOTIFICATIONS).where(NOTIFICATIONS.CREATED_AT.lt(cutoff)).execute();
    }
}
