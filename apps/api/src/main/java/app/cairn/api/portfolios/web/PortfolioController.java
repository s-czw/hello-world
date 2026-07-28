package app.cairn.api.portfolios.web;

import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import app.cairn.api.portfolios.Portfolio;
import app.cairn.api.portfolios.PortfolioService;
import app.cairn.api.portfolios.PortfolioUpdate;
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
 * Portfolios CRUD, membership, and roll-up (D1/D2). Any authenticated member may manage portfolios.
 * Deleting a portfolio never deletes its member projects.
 */
@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolios;
    private final CurrentUser currentUser;

    public PortfolioController(PortfolioService portfolios, CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<PortfolioResponse>> list(
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        currentUser.require();
        int pageSize = Cursor.clampLimit(limit);
        UUID after = Cursor.decode(cursor);

        List<Portfolio> rows = portfolios.list(after, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            nextCursor = Cursor.encode(rows.get(rows.size() - 1).id());
        }
        return ApiResponse.page(rows.stream().map(PortfolioResponse::from).toList(), nextCursor);
    }

    @GetMapping("/{id}")
    public ApiResponse<PortfolioResponse> get(@PathVariable UUID id) {
        currentUser.require();
        return ApiResponse.of(PortfolioResponse.from(portfolios.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioResponse>> create(
            @Valid @RequestBody CreatePortfolioRequest req) {
        var caller = currentUser.require();
        Portfolio p = portfolios.create(caller, req.name(), req.description(), req.color(), req.ownerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(PortfolioResponse.from(p)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<PortfolioResponse> update(
            @PathVariable UUID id, @RequestBody UpdatePortfolioRequest req) {
        currentUser.require();
        PortfolioUpdate u = new PortfolioUpdate(
                req.namePresent(), req.getName() == null ? null : req.getName().trim(),
                req.descriptionPresent(), req.getDescription(),
                req.colorPresent(), req.getColor(),
                req.ownerPresent(), req.getOwnerId());
        return ApiResponse.of(PortfolioResponse.from(portfolios.update(id, u)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        currentUser.require();
        portfolios.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- membership ----------------------------------------------------------

    @PostMapping("/{id}/projects")
    public ResponseEntity<Void> addProject(
            @PathVariable UUID id, @Valid @RequestBody AddProjectRequest req) {
        currentUser.require();
        portfolios.addProject(id, req.projectId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    public ResponseEntity<Void> removeProject(@PathVariable UUID id, @PathVariable UUID projectId) {
        currentUser.require();
        portfolios.removeProject(id, projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/projects/{projectId}/move")
    public ResponseEntity<Void> moveProject(
            @PathVariable UUID id,
            @PathVariable UUID projectId,
            @RequestBody(required = false) MoveProjectRequest req) {
        currentUser.require();
        UUID before = req == null ? null : req.beforeId();
        UUID after = req == null ? null : req.afterId();
        portfolios.moveProject(id, projectId, before, after);
        return ResponseEntity.noContent().build();
    }

    // --- roll-up -------------------------------------------------------------

    @GetMapping("/{id}/rollup")
    public ApiResponse<RollupResponse> rollup(@PathVariable UUID id) {
        currentUser.require();
        return ApiResponse.of(RollupResponse.from(portfolios.rollup(id)));
    }
}
