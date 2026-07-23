package app.cairn.api.notifications;

import static app.cairn.api.jooq.Tables.NOTIFICATIONS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.NotificationsRecord;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Org-scoped access to {@code notifications}; every query goes through {@link OrgScopedDsl}, so the
 * {@code organization_id} filter is always applied (D-028 seam). Rows are ordered newest-first by id —
 * ids are UUIDv7 (time-ordered), so an id keyset is a stable reverse-chronological cursor.
 *
 * <p>The retention purge is <em>not</em> here: it runs off-request on a scheduler thread with no
 * {@code OrgContext}, so it lives in {@code core.db} ({@code NotificationRetentionPurger}) as a raw-DSL
 * maintenance job rather than a tenant query.
 */
@Repository
public class NotificationRepository {

    private final OrgScopedDsl db;

    public NotificationRepository(OrgScopedDsl db) {
        this.db = db;
    }

    /** Insert a notification for a recipient. {@code payloadJson} may be null. Returns the new id. */
    public UUID insert(
            UUID recipientId,
            String type,
            UUID actorId,
            String payloadJson,
            String resourceType,
            UUID resourceId) {
        NotificationsRecord rec = db.newRecord(NOTIFICATIONS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setRecipientId(recipientId);
        rec.setType(type);
        rec.setActorId(actorId);
        rec.setPayload(payloadJson == null ? null : JSONB.valueOf(payloadJson));
        rec.setResourceType(resourceType);
        rec.setResourceId(resourceId);
        rec.setRead(false);
        rec.insert();
        return id;
    }

    /** A recipient's notifications, newest first, keyset-paginated by id (older than {@code afterId}). */
    public List<Notification> pageByRecipient(UUID recipientId, UUID afterId, int limit) {
        Condition keyset = afterId == null ? DSL.noCondition() : NOTIFICATIONS.ID.lt(afterId);
        return db.selectFrom(NOTIFICATIONS)
                .and(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
                .and(keyset)
                .orderBy(NOTIFICATIONS.ID.desc())
                .limit(limit)
                .fetch(NotificationRepository::toNotification);
    }

    /** Count of a recipient's unread notifications. */
    public int unreadCount(UUID recipientId) {
        return db.dsl()
                .fetchCount(db.selectFrom(NOTIFICATIONS)
                        .and(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
                        .and(NOTIFICATIONS.READ.isFalse()));
    }

    /** Mark one notification read; only the owning recipient may. Returns true if a row matched. */
    public boolean markRead(UUID id, UUID recipientId) {
        return db.dsl()
                        .update(NOTIFICATIONS)
                        .set(NOTIFICATIONS.READ, true)
                        .where(db.orgFilter(NOTIFICATIONS))
                        .and(NOTIFICATIONS.ID.eq(id))
                        .and(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
                        .execute()
                > 0;
    }

    /** Mark all of a recipient's unread notifications read. Returns the number updated. */
    public int markAllRead(UUID recipientId) {
        return db.dsl()
                .update(NOTIFICATIONS)
                .set(NOTIFICATIONS.READ, true)
                .where(db.orgFilter(NOTIFICATIONS))
                .and(NOTIFICATIONS.RECIPIENT_ID.eq(recipientId))
                .and(NOTIFICATIONS.READ.isFalse())
                .execute();
    }

    private static Notification toNotification(NotificationsRecord r) {
        return new Notification(
                r.getId(),
                r.getRecipientId(),
                r.getType(),
                r.getActorId(),
                r.getPayload() == null ? null : r.getPayload().data(),
                r.getResourceType(),
                r.getResourceId(),
                Boolean.TRUE.equals(r.getRead()),
                r.getCreatedAt());
    }
}
