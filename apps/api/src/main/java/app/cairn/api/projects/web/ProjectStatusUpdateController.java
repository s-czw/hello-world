package app.cairn.api.projects.web;

import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import app.cairn.api.projects.ProjectStatusUpdate;
import app.cairn.api.projects.ProjectStatusUpdateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project status updates (D3). Posting an update denormalizes the project's current status; the history
 * is returned newest-first with cursor pagination.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/status-updates")
public class ProjectStatusUpdateController {

    private final ProjectStatusUpdateService updates;
    private final CurrentUser currentUser;

    public ProjectStatusUpdateController(ProjectStatusUpdateService updates, CurrentUser currentUser) {
        this.updates = updates;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<StatusUpdateResponse>> history(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        currentUser.require();
        int pageSize = Cursor.clampLimit(limit);
        UUID after = Cursor.decode(cursor);

        List<ProjectStatusUpdate> rows = updates.history(projectId, after, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            nextCursor = Cursor.encode(rows.get(rows.size() - 1).id());
        }
        return ApiResponse.page(rows.stream().map(StatusUpdateResponse::from).toList(), nextCursor);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StatusUpdateResponse>> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateStatusUpdateRequest req) {
        var caller = currentUser.require();
        ProjectStatusUpdate created = updates.create(caller, projectId, req.status(), req.title(), req.body());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(StatusUpdateResponse.from(created)));
    }
}
