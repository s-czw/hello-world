package app.cairn.api.attachments;

import static app.cairn.api.jooq.Tables.ATTACHMENTS;

import app.cairn.api.core.db.OrgScopedDsl;
import app.cairn.api.jooq.tables.records.AttachmentsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** Org-scoped access to {@code attachments}; every query goes through {@link OrgScopedDsl}. */
@Repository
public class AttachmentRepository {

    private final OrgScopedDsl db;

    public AttachmentRepository(OrgScopedDsl db) {
        this.db = db;
    }

    private static Attachment toAttachment(Record r) {
        return new Attachment(
                r.get(ATTACHMENTS.ID),
                r.get(ATTACHMENTS.TASK_ID),
                r.get(ATTACHMENTS.COMMENT_ID),
                r.get(ATTACHMENTS.UPLOADED_BY),
                r.get(ATTACHMENTS.FILE_NAME),
                r.get(ATTACHMENTS.STORAGE_KEY),
                r.get(ATTACHMENTS.CONTENT_TYPE),
                r.get(ATTACHMENTS.SIZE_BYTES),
                r.get(ATTACHMENTS.CREATED_AT));
    }

    public void insert(
            UUID id,
            UUID taskId,
            UUID commentId,
            UUID uploadedBy,
            String fileName,
            String storageKey,
            String contentType,
            long sizeBytes) {
        AttachmentsRecord rec = db.newRecord(ATTACHMENTS);
        rec.setId(id);
        rec.setTaskId(taskId);
        rec.setCommentId(commentId);
        rec.setUploadedBy(uploadedBy);
        rec.setFileName(fileName);
        rec.setStorageKey(storageKey);
        rec.setContentType(contentType);
        rec.setSizeBytes(sizeBytes);
        rec.insert();
    }

    public Optional<Attachment> findById(UUID id) {
        return db.selectFrom(ATTACHMENTS).and(ATTACHMENTS.ID.eq(id)).fetchOptional(AttachmentRepository::toAttachment);
    }

    /** A task's attachments, oldest first. */
    public List<Attachment> listByTask(UUID taskId) {
        return db.selectFrom(ATTACHMENTS)
                .and(ATTACHMENTS.TASK_ID.eq(taskId))
                .orderBy(ATTACHMENTS.CREATED_AT.asc(), ATTACHMENTS.ID.asc())
                .fetch(AttachmentRepository::toAttachment);
    }

    public int delete(UUID id) {
        return db.dsl().deleteFrom(ATTACHMENTS).where(db.orgFilter(ATTACHMENTS)).and(ATTACHMENTS.ID.eq(id)).execute();
    }

    public int deleteByTask(UUID taskId) {
        return db.dsl()
                .deleteFrom(ATTACHMENTS)
                .where(db.orgFilter(ATTACHMENTS))
                .and(ATTACHMENTS.TASK_ID.eq(taskId))
                .execute();
    }
}
