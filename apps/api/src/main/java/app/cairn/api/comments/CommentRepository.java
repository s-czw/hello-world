package app.cairn.api.comments;

import static app.cairn.api.jooq.Tables.COMMENTS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.jooq.tables.records.CommentsRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code comments}; every query goes through {@link OrgScopedDsl}. */
@Repository
public class CommentRepository {

    private final OrgScopedDsl db;

    public CommentRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static Comment toComment(Record r) {
        return new Comment(
                r.get(COMMENTS.ID),
                r.get(COMMENTS.TASK_ID),
                r.get(COMMENTS.AUTHOR_ID),
                r.get(COMMENTS.BODY),
                r.get(COMMENTS.CREATED_AT),
                r.get(COMMENTS.EDITED_AT));
    }

    public UUID insert(UUID taskId, UUID authorId, String body) {
        CommentsRecord rec = db.newRecord(COMMENTS);
        UUID id = Uuid7.generate();
        rec.setId(id);
        rec.setTaskId(taskId);
        rec.setAuthorId(authorId);
        rec.setBody(body);
        rec.insert();
        return id;
    }

    public Optional<Comment> findById(UUID id) {
        return db.selectFrom(COMMENTS).and(COMMENTS.ID.eq(id)).fetchOptional(CommentRepository::toComment);
    }

    /** A task's comments, oldest first, keyset-paginated by id (UUIDv7 ≈ created order). */
    public List<Comment> pageByTask(UUID taskId, UUID afterId, int limit) {
        Condition where = db.orgFilter(COMMENTS).and(COMMENTS.TASK_ID.eq(taskId));
        if (afterId != null) {
            where = where.and(COMMENTS.ID.gt(afterId));
        }
        return db.dsl()
                .selectFrom(COMMENTS)
                .where(where)
                .orderBy(COMMENTS.CREATED_AT.asc(), COMMENTS.ID.asc())
                .limit(limit)
                .fetch(CommentRepository::toComment);
    }

    /** All of a task's comments, oldest first (for the merged activity stream). */
    public List<Comment> listByTask(UUID taskId) {
        return db.selectFrom(COMMENTS)
                .and(COMMENTS.TASK_ID.eq(taskId))
                .orderBy(COMMENTS.CREATED_AT.asc(), COMMENTS.ID.asc())
                .fetch(CommentRepository::toComment);
    }

    public int updateBody(UUID id, String body, OffsetDateTime editedAt) {
        return db.dsl()
                .update(COMMENTS)
                .set(COMMENTS.BODY, body)
                .set(COMMENTS.EDITED_AT, editedAt)
                .where(db.orgFilter(COMMENTS))
                .and(COMMENTS.ID.eq(id))
                .execute();
    }

    public int delete(UUID id) {
        return db.dsl().deleteFrom(COMMENTS).where(db.orgFilter(COMMENTS)).and(COMMENTS.ID.eq(id)).execute();
    }

    public int deleteByTask(UUID taskId) {
        return db.dsl()
                .deleteFrom(COMMENTS)
                .where(db.orgFilter(COMMENTS))
                .and(COMMENTS.TASK_ID.eq(taskId))
                .execute();
    }
}
