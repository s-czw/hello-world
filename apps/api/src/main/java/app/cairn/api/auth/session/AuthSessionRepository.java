package app.cairn.api.auth.session;

import static app.cairn.api.jooq.Tables.AUTH_SESSIONS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.AuthSessionsRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Org-scoped persistence of refresh tokens ({@code auth_sessions}). Only the SHA-256 hash of the
 * opaque refresh token is stored; rotation revokes the old row and inserts a new one. All access goes
 * through {@link OrgScopedDsl}.
 */
@Repository
public class AuthSessionRepository {

    private final OrgScopedDsl db;

    public AuthSessionRepository(OrgScopedDsl db) {
        this.db = db;
    }

    public UUID create(UUID userId, String refreshTokenHash, OffsetDateTime expiresAt) {
        AuthSessionsRecord rec = db.newRecord(AUTH_SESSIONS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setUserId(userId);
        rec.setRefreshTokenHash(refreshTokenHash);
        rec.setExpiresAt(expiresAt);
        rec.insert();
        return id;
    }

    /** A live (not revoked, not expired) session for the given refresh-token hash. */
    public Optional<SessionRow> findLiveByTokenHash(String refreshTokenHash, OffsetDateTime now) {
        return db.selectFrom(AUTH_SESSIONS)
                .and(AUTH_SESSIONS.REFRESH_TOKEN_HASH.eq(refreshTokenHash))
                .and(AUTH_SESSIONS.REVOKED_AT.isNull())
                .and(AUTH_SESSIONS.EXPIRES_AT.gt(now))
                .fetchOptional(r -> new SessionRow(r.getId(), r.getUserId(), r.getExpiresAt()));
    }

    /** Revoke by refresh-token hash (idempotent). Returns rows affected. */
    public int revokeByTokenHash(String refreshTokenHash, OffsetDateTime at) {
        return db.dsl()
                .update(AUTH_SESSIONS)
                .set(AUTH_SESSIONS.REVOKED_AT, at)
                .where(db.orgFilter(AUTH_SESSIONS))
                .and(AUTH_SESSIONS.REFRESH_TOKEN_HASH.eq(refreshTokenHash))
                .and(AUTH_SESSIONS.REVOKED_AT.isNull())
                .execute();
    }

    public void revokeById(UUID id, OffsetDateTime at) {
        db.dsl()
                .update(AUTH_SESSIONS)
                .set(AUTH_SESSIONS.REVOKED_AT, at)
                .where(db.orgFilter(AUTH_SESSIONS))
                .and(AUTH_SESSIONS.ID.eq(id))
                .execute();
    }
}
