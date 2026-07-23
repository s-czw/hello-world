package app.cairn.api.attachments.web;

import app.cairn.api.attachments.Attachment;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Attachment metadata for clients. {@code downloadUrl} is the API route that streams the bytes under
 * authz — attachments are never served from a direct/static URL.
 */
public record AttachmentResponse(
        UUID id,
        UUID taskId,
        UUID commentId,
        UUID uploadedBy,
        String fileName,
        String contentType,
        long sizeBytes,
        String downloadUrl,
        OffsetDateTime createdAt) {

    public static AttachmentResponse from(Attachment a) {
        return new AttachmentResponse(
                a.id(),
                a.taskId(),
                a.commentId(),
                a.uploadedBy(),
                a.fileName(),
                a.contentType(),
                a.sizeBytes(),
                "/api/v1/attachments/" + a.id(),
                a.createdAt());
    }
}
