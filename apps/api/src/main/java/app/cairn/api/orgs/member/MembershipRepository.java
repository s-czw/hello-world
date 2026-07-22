package app.cairn.api.orgs.member;

import static app.cairn.api.jooq.Tables.MEMBERSHIPS;
import static app.cairn.api.jooq.Tables.USERS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.MembershipsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.Record8;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Org-scoped access to {@code memberships}, joined to the global {@code users} table. Every query is
 * constrained by {@link OrgScopedDsl#orgFilter} so the org-filter seam (D-028) always applies.
 */
@Repository
public class MembershipRepository {

    private final OrgScopedDsl db;

    public MembershipRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static final RecordMapper<
                    Record8<UUID, UUID, String, String, String, String, Boolean, java.time.OffsetDateTime>,
                    MemberAccount>
            TO_ACCOUNT = r -> new MemberAccount(
                    r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(), r.value7(), r.value8());

    private org.jooq.SelectJoinStep<
                    Record8<UUID, UUID, String, String, String, String, Boolean, java.time.OffsetDateTime>>
            baseSelect() {
        return db.dsl()
                .select(
                        USERS.ID,
                        MEMBERSHIPS.ORGANIZATION_ID,
                        USERS.EMAIL,
                        USERS.NAME,
                        USERS.PASSWORD_HASH,
                        MEMBERSHIPS.ROLE,
                        MEMBERSHIPS.ACTIVE,
                        USERS.CREATED_AT)
                .from(MEMBERSHIPS)
                .join(USERS)
                .on(USERS.ID.eq(MEMBERSHIPS.USER_ID));
    }

    public Optional<MemberAccount> findByEmail(String email) {
        return baseSelect()
                .where(db.orgFilter(MEMBERSHIPS))
                .and(USERS.EMAIL.equalIgnoreCase(email))
                .fetchOptional(TO_ACCOUNT);
    }

    public Optional<MemberAccount> findByUserId(UUID userId) {
        return baseSelect()
                .where(db.orgFilter(MEMBERSHIPS))
                .and(MEMBERSHIPS.USER_ID.eq(userId))
                .fetchOptional(TO_ACCOUNT);
    }

    /** One keyset page of members ordered by user id ascending (UUIDv7 = created order). */
    public List<MemberAccount> page(UUID afterUserId, int limit) {
        Condition keyset = afterUserId == null ? DSL.noCondition() : USERS.ID.gt(afterUserId);
        return baseSelect()
                .where(db.orgFilter(MEMBERSHIPS))
                .and(keyset)
                .orderBy(USERS.ID.asc())
                .limit(limit)
                .fetch(TO_ACCOUNT);
    }

    public int countActiveAdmins() {
        return db.dsl()
                .fetchCount(
                        MEMBERSHIPS,
                        db.orgFilter(MEMBERSHIPS)
                                .and(MEMBERSHIPS.ROLE.eq(Role.ADMIN))
                                .and(MEMBERSHIPS.ACTIVE.isTrue()));
    }

    /** Insert a membership via the org helper (organization_id is stamped by {@link OrgScopedDsl}). */
    public void insert(UUID userId, String role, boolean active) {
        MembershipsRecord rec = db.newRecord(MEMBERSHIPS);
        rec.setId(Uuid7.generate());
        rec.setUserId(userId);
        rec.setRole(role);
        rec.setActive(active);
        rec.insert();
    }

    /** Update role/active for a member in the current org; returns rows affected (0 = not in org). */
    public int updateRoleAndActive(UUID userId, String role, boolean active) {
        return db.dsl()
                .update(MEMBERSHIPS)
                .set(MEMBERSHIPS.ROLE, role)
                .set(MEMBERSHIPS.ACTIVE, active)
                .where(db.orgFilter(MEMBERSHIPS))
                .and(MEMBERSHIPS.USER_ID.eq(userId))
                .execute();
    }
}
