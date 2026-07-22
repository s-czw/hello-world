package app.cairn.api.tasks.web;

import app.cairn.api.projects.ProjectMeta;
import app.cairn.api.tasks.Task;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A "My Tasks" row: the task plus the identifying fields of its project (id + name + color). */
public record MyTaskResponse(
        UUID id,
        UUID projectId,
        String projectName,
        String projectColor,
        UUID sectionId,
        UUID assigneeId,
        String title,
        String priority,
        LocalDate dueDate,
        boolean completed,
        OffsetDateTime createdAt) {

    public static MyTaskResponse from(Task t, ProjectMeta project) {
        return new MyTaskResponse(
                t.id(),
                t.projectId(),
                project == null ? null : project.name(),
                project == null ? null : project.color(),
                t.sectionId(),
                t.assigneeId(),
                t.title(),
                t.priority(),
                t.dueDate(),
                t.completed(),
                t.createdAt());
    }
}
