package app.cairn.api.projects.web;

import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import app.cairn.api.projects.Project;
import app.cairn.api.projects.ProjectService;
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

/**
 * Projects CRUD. Listing excludes archived projects unless {@code ?includeArchived=true}. Delete is an
 * admin-or-owner action (enforced in the service) and cascades sections + tasks.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projects;
    private final CurrentUser currentUser;

    public ProjectController(ProjectService projects, CurrentUser currentUser) {
        this.projects = projects;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        currentUser.require();
        int pageSize = Cursor.clampLimit(limit);
        UUID after = Cursor.decode(cursor);

        List<Project> rows = projects.list(after, pageSize + 1, includeArchived);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            nextCursor = Cursor.encode(rows.get(rows.size() - 1).id());
        }
        return ApiResponse.page(rows.stream().map(ProjectResponse::from).toList(), nextCursor);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> get(@PathVariable UUID id) {
        currentUser.require();
        return ApiResponse.of(ProjectResponse.from(projects.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@Valid @RequestBody CreateProjectRequest req) {
        var caller = currentUser.require();
        Project project = projects.create(
                caller,
                req.name(),
                req.description(),
                req.color(),
                req.teamId(),
                req.ownerId(),
                req.defaultView(),
                req.startDate(),
                req.endDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(ProjectResponse.from(project)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProjectResponse> update(
            @PathVariable UUID id, @RequestBody UpdateProjectRequest req) {
        currentUser.require();
        return ApiResponse.of(ProjectResponse.from(projects.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        var caller = currentUser.require();
        projects.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}
