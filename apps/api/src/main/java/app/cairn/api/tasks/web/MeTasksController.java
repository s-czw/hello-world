package app.cairn.api.tasks.web;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.projects.ProjectMeta;
import app.cairn.api.projects.ProjectService;
import app.cairn.api.tasks.Task;
import app.cairn.api.tasks.TaskService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "My Tasks": the caller's open (not completed) tasks across every project, each carrying its project's
 * id, name, and color. Ordered by due date with nulls last (then created order). Bounded per user, so
 * the full set is returned (no pagination needed; offset pagination is banned regardless).
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeTasksController {

    private final TaskService tasks;
    private final ProjectService projects;
    private final CurrentUser currentUser;

    public MeTasksController(TaskService tasks, ProjectService projects, CurrentUser currentUser) {
        this.tasks = tasks;
        this.projects = projects;
        this.currentUser = currentUser;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<MyTaskResponse>> myTasks() {
        AuthPrincipal caller = currentUser.require();
        List<Task> rows = tasks.myOpenTasks(caller.userId());
        Map<UUID, ProjectMeta> projectMeta = projects.metaByIds(
                rows.stream().map(Task::projectId).collect(Collectors.toSet()));
        List<MyTaskResponse> data = rows.stream()
                .map(t -> MyTaskResponse.from(t, projectMeta.get(t.projectId())))
                .toList();
        return ApiResponse.of(data);
    }
}
