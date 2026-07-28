package app.cairn.api.tasks.web;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import app.cairn.api.tasks.Task;
import app.cairn.api.tasks.TaskCursor;
import app.cairn.api.tasks.TaskService;
import app.cairn.api.tasks.TaskWithSectionKey;
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
 * Tasks: a project's flat (grouped-ready) task list, create-under-project, single-task CRUD, and move.
 * The list is ordered by section then fractional sort key and paginated by an opaque keyset cursor.
 */
@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskService tasks;
    private final CurrentUser currentUser;

    public TaskController(TaskService tasks, CurrentUser currentUser) {
        this.tasks = tasks;
        this.currentUser = currentUser;
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ApiResponse<List<TaskResponse>> listByProject(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        currentUser.require();
        int pageSize = Cursor.clampLimit(limit);
        TaskCursor after = TaskCursor.decode(cursor);

        List<TaskWithSectionKey> rows = tasks.listByProject(projectId, after, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            TaskWithSectionKey last = rows.get(rows.size() - 1);
            nextCursor = new TaskCursor(
                            last.sectionSortKey(),
                            last.task().sortKey(),
                            last.task().createdAt(),
                            last.task().id())
                    .encode();
        }
        return ApiResponse.page(rows.stream().map(r -> TaskResponse.from(r.task())).toList(), nextCursor);
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateTaskRequest req) {
        AuthPrincipal caller = currentUser.require();
        Task task = tasks.create(
                caller, projectId, req.sectionId(), req.assigneeId(), req.title(), null, req.priority(),
                req.dueDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(TaskResponse.from(task)));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<TaskDetailResponse> get(@PathVariable UUID id) {
        currentUser.require();
        Task task = tasks.get(id);
        List<Task> subtasks = tasks.subtasks(id);
        int[] progress = tasks.subtaskProgress(id);
        return ApiResponse.of(TaskDetailResponse.of(task, subtasks, progress[0], progress[1]));
    }

    @PatchMapping("/tasks/{id}")
    public ApiResponse<TaskResponse> update(@PathVariable UUID id, @RequestBody UpdateTaskRequest req) {
        AuthPrincipal caller = currentUser.require();
        return ApiResponse.of(TaskResponse.from(tasks.update(caller, id, req)));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        AuthPrincipal caller = currentUser.require();
        tasks.delete(caller, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{id}/move")
    public ApiResponse<TaskResponse> move(@PathVariable UUID id, @RequestBody MoveTaskRequest req) {
        AuthPrincipal caller = currentUser.require();
        Task moved = tasks.move(caller, id, req.sectionId(), req.beforeTaskId(), req.afterTaskId());
        return ApiResponse.of(TaskResponse.from(moved));
    }

    // --- subtasks (C2) --------------------------------------------------------

    @PostMapping("/tasks/{id}/subtasks")
    public ResponseEntity<ApiResponse<TaskResponse>> addSubtask(
            @PathVariable UUID id, @Valid @RequestBody CreateSubtaskRequest req) {
        AuthPrincipal caller = currentUser.require();
        Task subtask = tasks.createSubtask(caller, id, req.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(TaskResponse.from(subtask)));
    }

    @PostMapping("/tasks/{id}/promote")
    public ApiResponse<TaskResponse> promote(
            @PathVariable UUID id, @RequestBody(required = false) PromoteTaskRequest req) {
        AuthPrincipal caller = currentUser.require();
        UUID sectionId = req == null ? null : req.sectionId();
        return ApiResponse.of(TaskResponse.from(tasks.promote(caller, id, sectionId)));
    }
}
