package app.cairn.api.tasks.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Body for {@code POST /projects/{id}/tasks}. New tasks append to the end of their section. */
public record CreateTaskRequest(
        @NotBlank @Size(max = 500) String title,
        UUID sectionId,
        UUID assigneeId,
        LocalDate dueDate,
        String priority) {}
