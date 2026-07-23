package app.cairn.api.attachments;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Attachment metadata; the bytes live on the {@link StorageProvider} at {@code storageKey}. */
public record Attachment(
        UUID id,
        UUID taskId,
        UUID commentId,
        UUID uploadedBy,
        String fileName,
        String storageKey,
        String contentType,
        long sizeBytes,
        OffsetDateTime createdAt) {}
