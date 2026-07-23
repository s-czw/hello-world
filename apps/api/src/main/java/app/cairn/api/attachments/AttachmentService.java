package app.cairn.api.attachments;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.comments.Comment;
import app.cairn.api.comments.CommentService;
import app.cairn.api.core.error.ApiException;
import app.cairn.api.core.error.ForbiddenException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.core.id.Uuid7;
import app.cairn.api.tasks.TaskService;
import app.cairn.api.tasks.event.TaskEvents;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Attachments (C4). Uploads are validated (size, extension allowlist, magic-byte sniff — HTML/SVG and
 * executables are rejected), stored via the {@link StorageProvider}, and recorded org-scoped. Downloads
 * stream <em>through</em> the API under authz (never a direct URL); deletes are uploader-or-admin. Bytes
 * are cleaned up when a task is deleted (via {@link TaskEvents.TaskDeleting}).
 */
@Service
public class AttachmentService {

    private static final int SNIFF_BYTES = 512;

    private final AttachmentRepository attachments;
    private final StorageProvider storage;
    private final TaskService tasks;
    private final CommentService comments;
    private final long maxBytes;

    public AttachmentService(
            AttachmentRepository attachments,
            StorageProvider storage,
            TaskService tasks,
            CommentService comments,
            @Value("${cairn.attachments.max-size-bytes:26214400}") long maxBytes) {
        this.attachments = attachments;
        this.storage = storage;
        this.tasks = tasks;
        this.comments = comments;
        this.maxBytes = maxBytes;
    }

    public List<Attachment> listByTask(UUID taskId) {
        tasks.get(taskId);
        return attachments.listByTask(taskId);
    }

    @Transactional
    public Attachment upload(AuthPrincipal caller, UUID taskId, UUID commentId, MultipartFile file) {
        tasks.get(taskId); // 404 if the task is not in this org
        if (commentId != null) {
            Comment c = comments.get(commentId);
            if (!c.taskId().equals(taskId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Comment does not belong to this task");
            }
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A non-empty file is required");
        }
        if (file.getSize() > maxBytes) {
            throw tooLarge();
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        if (bytes.length > maxBytes) {
            throw tooLarge();
        }

        String fileName = sanitizeFilename(file.getOriginalFilename());
        byte[] head = Arrays.copyOf(bytes, Math.min(bytes.length, SNIFF_BYTES));
        ContentTypeSniffer.Result sniff = ContentTypeSniffer.check(fileName, head);
        if (!sniff.allowed()) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, sniff.reason());
        }

        UUID id = Uuid7.generate();
        String storageKey = caller.orgId() + "/" + id + "/" + fileName;
        try {
            storage.store(storageKey, bytes);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store the attachment");
        }
        attachments.insert(
                id, taskId, commentId, caller.userId(), fileName, storageKey, sniff.contentType(), bytes.length);
        return attachments.findById(id).orElseThrow();
    }

    /** Load an attachment for download; org-scoped lookup enforces access (404-for-inaccessible). */
    public AttachmentDownload load(UUID id) {
        Attachment a = attachments.findById(id).orElseThrow(() -> NotFoundException.of("Attachment"));
        var resource = storage.load(a.storageKey());
        if (!resource.exists() || !resource.isReadable()) {
            throw NotFoundException.of("Attachment");
        }
        return new AttachmentDownload(a, resource);
    }

    @Transactional
    public void delete(AuthPrincipal caller, UUID id) {
        Attachment a = attachments.findById(id).orElseThrow(() -> NotFoundException.of("Attachment"));
        boolean allowed = caller.isAdmin() || caller.userId().equals(a.uploadedBy());
        if (!allowed) {
            throw new ForbiddenException("Only the uploader or an admin may delete this attachment");
        }
        attachments.delete(id);
        storage.delete(a.storageKey());
    }

    /** Remove a deleted task's attachment rows and bytes (sync, same tx). */
    @EventListener
    void onTaskDeleting(TaskEvents.TaskDeleting e) {
        List<Attachment> list = attachments.listByTask(e.taskId());
        attachments.deleteByTask(e.taskId());
        for (Attachment a : list) {
            storage.delete(a.storageKey());
        }
    }

    private ApiException tooLarge() {
        long mb = maxBytes / (1024 * 1024);
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum upload size of " + mb + " MB");
    }

    /** Keep only the basename and safe characters; preserve the extension for sniffing/serving. */
    static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        String name = original.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        return name.isBlank() ? "file" : name;
    }
}
