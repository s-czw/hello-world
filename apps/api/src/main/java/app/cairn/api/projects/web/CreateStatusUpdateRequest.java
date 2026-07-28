package app.cairn.api.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /projects/{id}/status-updates}. Body is plain text (D-011). */
public record CreateStatusUpdateRequest(
        @NotBlank String status,
        @Size(max = 200) String title,
        @Size(max = 10000) String body) {}
