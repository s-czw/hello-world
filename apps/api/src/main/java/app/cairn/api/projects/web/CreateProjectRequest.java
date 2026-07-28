package app.cairn.api.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for {@code POST /projects}. Every project belongs to a team; owner defaults to the caller.
 *
 * <p>{@code startDate}/{@code endDate} are optional (spec B2 lists them on the create dialog) and drive
 * the portfolio schedule view — a project created without them lands in the "not scheduled" tray.
 */
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @Size(max = 32) String color,
        @NotNull UUID teamId,
        UUID ownerId,
        String defaultView,
        LocalDate startDate,
        LocalDate endDate) {}
