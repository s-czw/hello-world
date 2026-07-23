package app.cairn.api.activity.web;

import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The task activity stream (C3): comments + system events merged chronologically. */
@RestController
@RequestMapping("/api/v1")
public class ActivityController {

    private final ActivityStreamService stream;
    private final CurrentUser currentUser;

    public ActivityController(ActivityStreamService stream, CurrentUser currentUser) {
        this.stream = stream;
        this.currentUser = currentUser;
    }

    @GetMapping("/tasks/{taskId}/activity")
    public ApiResponse<List<ActivityStreamEntry>> activity(@PathVariable UUID taskId) {
        currentUser.require();
        return ApiResponse.of(stream.forTask(taskId));
    }
}
