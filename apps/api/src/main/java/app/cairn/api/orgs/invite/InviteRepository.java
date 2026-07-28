package app.cairn.api.orgs.invite;

import static app.cairn.api.jooq.Tables.INVITES;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.InvitesRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code invites}; every query goes through {@link OrgScopedDsl}. */
@Repository
public class InviteRepository {

    private final OrgScopedDsl db;

    public InviteRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static final RecordMapper<org.jooq.Record, InviteRow> TO_ROW = r -> new InviteRow(
            r.get(INVITES.ID),
            r.get(INVITES.EMAIL),
            r.get(INVITES.INVITED_BY),
            r.get(INVITES.EXPIRES_AT),
            r.get(INVITES.REVOKED_AT),
            r.get(INVITES.ACCEPTED_AT),
            r.get(INVITES.CREATED_AT));

    public UUID insert(String email, String tokenHash, UUID invitedBy, OffsetDateTime expiresAt) {
        InvitesRecord rec = db.newRecord(INVITES);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setEmail(email);
        rec.setTokenHash(tokenHash);
        rec.setInvitedBy(invitedBy);
        rec.setExpiresAt(expiresAt);
        rec.insert();
        return id;
    }

    /** Pending invites (not revoked, not accepted, not expired), keyset by id ascending. */
    public List<InviteRow> pagePending(UUID afterId, int limit) {
        Condition keyset = afterId == null ? DSL.noCondition() : INVITES.ID.gt(afterId);
        return db.selectFrom(INVITES)
                .and(INVITES.REVOKED_AT.isNull())
                .and(INVITES.ACCEPTED_AT.isNull())
                .and(INVITES.EXPIRES_AT.gt(OffsetDateTime.now()))
                .and(keyset)
                .orderBy(INVITES.ID.asc())
                .limit(limit)
                .fetch(TO_ROW);
    }

    public Optional<InviteRow> findById(UUID id) {
        return db.selectFrom(INVITES).and(INVITES.ID.eq(id)).fetchOptional(TO_ROW);
    }

    public Optional<InviteRow> findByTokenHash(String tokenHash) {
        return db.selectFrom(INVITES).and(INVITES.TOKEN_HASH.eq(tokenHash)).fetchOptional(TO_ROW);
    }

    /** Revoke a still-pending invite; returns rows affected (0 = already consumed/revoked/absent). */
    public int revoke(UUID id, OffsetDateTime at) {
        return db.dsl()
                .update(INVITES)
                .set(INVITES.REVOKED_AT, at)
                .where(db.orgFilter(INVITES))
                .and(INVITES.ID.eq(id))
                .and(INVITES.REVOKED_AT.isNull())
                .and(INVITES.ACCEPTED_AT.isNull())
                .execute();
    }

    public void markAccepted(UUID id, OffsetDateTime at) {
        db.dsl()
                .update(INVITES)
                .set(INVITES.ACCEPTED_AT, at)
                .where(db.orgFilter(INVITES))
                .and(INVITES.ID.eq(id))
                .execute();
    }
}
