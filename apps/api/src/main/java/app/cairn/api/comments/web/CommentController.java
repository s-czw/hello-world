package app.cairn.api.comments.web;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.auth.CurrentUser;
import app.cairn.api.comments.Comment;
import app.cairn.api.comments.CommentService;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Task comments (C3): chronological list + create with @mentions, and edit/delete of one's own comment. */
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService comments;
    private final CurrentUser currentUser;

    public CommentController(CommentService comments, CurrentUser currentUser) {
        this.comments = comments;
        this.currentUser = currentUser;
    }

    @GetMapping("/tasks/{taskId}/comments")
    public ApiResponse<List<CommentResponse>> list(
            @PathVariable UUID taskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        currentUser.require();
        int pageSize = Cursor.clampLimit(limit);
        UUID after = Cursor.decode(cursor);

        List<Comment> rows = comments.list(taskId, after, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            nextCursor = Cursor.encode(rows.get(rows.size() - 1).id());
        }
        return ApiResponse.page(rows.stream().map(CommentResponse::from).toList(), nextCursor);
    }

    @PostMapping("/tasks/{taskId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @PathVariable UUID taskId, @Valid @RequestBody CreateCommentRequest req) {
        AuthPrincipal caller = currentUser.require();
        Comment created = comments.create(caller, taskId, req.body());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(CommentResponse.from(created)));
    }

    @PatchMapping("/comments/{id}")
    public ApiResponse<CommentResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateCommentRequest req) {
        AuthPrincipal caller = currentUser.require();
        return ApiResponse.of(CommentResponse.from(comments.update(caller, id, req.body())));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        AuthPrincipal caller = currentUser.require();
        comments.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}
