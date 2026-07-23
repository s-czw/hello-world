package app.cairn.api.tasks.web;

import app.cairn.api.tasks.Task;
import java.util.List;

/**
 * A single task with its subtasks and a subtask progress count (C2). Returned by {@code GET /tasks/{id}}
 * so the side-peek can render the checklist and the "n/m" progress without extra round-trips.
 */
public record TaskDetailResponse(TaskResponse task, List<TaskResponse> subtasks, Progress subtaskProgress) {

    public record Progress(int total, int completed) {}

    public static TaskDetailResponse of(Task task, List<Task> subtasks, int total, int completed) {
        return new TaskDetailResponse(
                TaskResponse.from(task),
                subtasks.stream().map(TaskResponse::from).toList(),
                new Progress(total, completed));
    }
}
