package app.cairn.api.projects.web;

import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.projects.Section;
import app.cairn.api.projects.SectionService;
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

/** Sections: create (appends), list, rename, reorder, and delete (empty or with a moveTo target). */
@RestController
@RequestMapping("/api/v1")
public class SectionController {

    private final SectionService sections;
    private final CurrentUser currentUser;

    public SectionController(SectionService sections, CurrentUser currentUser) {
        this.sections = sections;
        this.currentUser = currentUser;
    }

    @GetMapping("/projects/{projectId}/sections")
    public ApiResponse<List<SectionResponse>> list(@PathVariable UUID projectId) {
        currentUser.require();
        List<Section> rows = sections.listByProject(projectId);
        return ApiResponse.of(rows.stream().map(SectionResponse::from).toList());
    }

    @PostMapping("/projects/{projectId}/sections")
    public ResponseEntity<ApiResponse<SectionResponse>> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateSectionRequest req) {
        currentUser.require();
        Section section = sections.create(projectId, req.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SectionResponse.from(section)));
    }

    @PatchMapping("/sections/{id}")
    public ApiResponse<SectionResponse> rename(
            @PathVariable UUID id, @Valid @RequestBody UpdateSectionRequest req) {
        currentUser.require();
        return ApiResponse.of(SectionResponse.from(sections.rename(id, req.name())));
    }

    @DeleteMapping("/sections/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestParam(required = false) UUID moveTo) {
        currentUser.require();
        sections.delete(id, moveTo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sections/{id}/move")
    public ApiResponse<SectionResponse> move(
            @PathVariable UUID id, @RequestBody MoveSectionRequest req) {
        currentUser.require();
        Section moved = sections.move(id, req.beforeSectionId(), req.afterSectionId());
        return ApiResponse.of(SectionResponse.from(moved));
    }
}
