package app.cairn.api.teams;

import static app.cairn.api.jooq.Tables.TEAMS;
import static app.cairn.api.jooq.Tables.TEAM_MEMBERS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.TeamMembersRecord;
import app.cairn.api.jooq.tables.records.TeamsRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code teams} and their {@code team_members}; all through {@link OrgScopedDsl}. */
@Repository
public class TeamRepository {

    private final OrgScopedDsl db;

    public TeamRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static final RecordMapper<org.jooq.Record, Team> TO_TEAM = r ->
            new Team(r.get(TEAMS.ID), r.get(TEAMS.NAME), r.get(TEAMS.DESCRIPTION), r.get(TEAMS.CREATED_AT));

    public UUID insert(String name, String description) {
        TeamsRecord rec = db.newRecord(TEAMS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setName(name);
        rec.setDescription(description);
        rec.insert();
        return id;
    }

    public Optional<Team> findById(UUID id) {
        return db.selectFrom(TEAMS).and(TEAMS.ID.eq(id)).fetchOptional(TO_TEAM);
    }

    public boolean exists(UUID id) {
        return db.dsl().fetchExists(db.selectFrom(TEAMS).and(TEAMS.ID.eq(id)));
    }

    public List<Team> page(UUID afterId, int limit) {
        Condition keyset = afterId == null ? DSL.noCondition() : TEAMS.ID.gt(afterId);
        return db.selectFrom(TEAMS)
                .and(keyset)
                .orderBy(TEAMS.ID.asc())
                .limit(limit)
                .fetch(TO_TEAM);
    }

    /** Update name/description; null args leave the corresponding column unchanged. */
    public int update(UUID id, String name, String description) {
        Map<org.jooq.Field<?>, Object> changes = new LinkedHashMap<>();
        if (name != null) {
            changes.put(TEAMS.NAME, name);
        }
        if (description != null) {
            changes.put(TEAMS.DESCRIPTION, description);
        }
        if (changes.isEmpty()) {
            return exists(id) ? 1 : 0;
        }
        return db.dsl()
                .update(TEAMS)
                .set(changes)
                .where(db.orgFilter(TEAMS))
                .and(TEAMS.ID.eq(id))
                .execute();
    }

    public int delete(UUID id) {
        return db.dsl()
                .deleteFrom(TEAMS)
                .where(db.orgFilter(TEAMS))
                .and(TEAMS.ID.eq(id))
                .execute();
    }

    // --- members -------------------------------------------------------------

    public List<UUID> memberIds(UUID teamId) {
        return db.selectFrom(TEAM_MEMBERS)
                .and(TEAM_MEMBERS.TEAM_ID.eq(teamId))
                .orderBy(TEAM_MEMBERS.CREATED_AT.asc(), TEAM_MEMBERS.ID.asc())
                .fetch(TEAM_MEMBERS.USER_ID);
    }

    /** member ids grouped by team, for the listing path (single query). */
    public Map<UUID, List<UUID>> memberIdsByTeam(List<UUID> teamIds) {
        Map<UUID, List<UUID>> out = new LinkedHashMap<>();
        if (teamIds.isEmpty()) {
            return out;
        }
        db.selectFrom(TEAM_MEMBERS)
                .and(TEAM_MEMBERS.TEAM_ID.in(teamIds))
                .orderBy(TEAM_MEMBERS.CREATED_AT.asc(), TEAM_MEMBERS.ID.asc())
                .fetch()
                .forEach(r -> out.computeIfAbsent(r.getTeamId(), k -> new java.util.ArrayList<>())
                        .add(r.getUserId()));
        return out;
    }

    public void replaceMembers(UUID teamId, List<UUID> userIds) {
        db.dsl()
                .deleteFrom(TEAM_MEMBERS)
                .where(db.orgFilter(TEAM_MEMBERS))
                .and(TEAM_MEMBERS.TEAM_ID.eq(teamId))
                .execute();
        for (UUID userId : userIds) {
            TeamMembersRecord rec = db.newRecord(TEAM_MEMBERS);
            rec.setId(Uuid7.generate());
            rec.setTeamId(teamId);
            rec.setUserId(userId);
            rec.insert();
        }
    }
}
