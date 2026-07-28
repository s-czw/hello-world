package app.cairn.api.attachments.web;

import app.cairn.api.attachments.Attachment;
import app.cairn.api.attachments.AttachmentDownload;
import app.cairn.api.attachments.AttachmentService;
import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Attachments (C4): multipart upload under a task (optionally tied to a comment), an authz'd streaming
 * download (attachment disposition + {@code X-Content-Type-Options: nosniff}), and delete.
 */
@RestController
@RequestMapping("/api/v1")
public class AttachmentController {

    private final AttachmentService attachments;
    private final CurrentUser currentUser;

    public AttachmentController(AttachmentService attachments, CurrentUser currentUser) {
        this.attachments = attachments;
        this.currentUser = currentUser;
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public ApiResponse<List<AttachmentResponse>> list(@PathVariable UUID taskId) {
        currentUser.require();
        return ApiResponse.of(
                attachments.listByTask(taskId).stream().map(AttachmentResponse::from).toList());
    }

    @PostMapping("/tasks/{taskId}/attachments")
    public ResponseEntity<ApiResponse<AttachmentResponse>> upload(
            @PathVariable UUID taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "commentId", required = false) UUID commentId) {
        AuthPrincipal caller = currentUser.require();
        Attachment a = attachments.upload(caller, taskId, commentId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(AttachmentResponse.from(a)));
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        currentUser.require();
        AttachmentDownload dl = attachments.load(id);
        Attachment a = dl.meta();

        MediaType mediaType = a.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(a.contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(a.fileName())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .contentLength(a.sizeBytes())
                .body(dl.resource());
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        AuthPrincipal caller = currentUser.require();
        attachments.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}
