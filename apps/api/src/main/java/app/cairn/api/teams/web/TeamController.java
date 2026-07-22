package app.cairn.api.teams.web;

import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import app.cairn.api.teams.Team;
import app.cairn.api.teams.TeamService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teams CRUD + membership. All actions require an authenticated member (spec §2.1: any member manages
 * teams). Delete is refused (409) while the team still owns projects.
 */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teams;
    private final CurrentUser currentUser;

    public TeamController(TeamService teams, CurrentUser currentUser) {
        this.teams = teams;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<TeamResponse>> list(
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        currentUser.require();
        int pageSize = Cursor.clampLimit(limit);
        UUID after = Cursor.decode(cursor);

        List<Team> rows = teams.list(after, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            nextCursor = Cursor.encode(rows.get(rows.size() - 1).id());
        }
        Map<UUID, List<UUID>> members = teams.memberIdsByTeam(rows.stream().map(Team::id).toList());
        List<TeamResponse> data = rows.stream()
                .map(t -> TeamResponse.from(t, members.getOrDefault(t.id(), List.of())))
                .toList();
        return ApiResponse.page(data, nextCursor);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamResponse>> create(@Valid @RequestBody CreateTeamRequest req) {
        currentUser.require();
        Team team = teams.create(req.name(), req.description());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(TeamResponse.from(team, List.of())));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TeamResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateTeamRequest req) {
        currentUser.require();
        Team team = teams.update(id, req.name(), req.description());
        return ApiResponse.of(TeamResponse.from(team, teams.memberIds(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        currentUser.require();
        teams.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members")
    public ApiResponse<TeamResponse> setMembers(
            @PathVariable UUID id, @Valid @RequestBody SetTeamMembersRequest req) {
        currentUser.require();
        List<UUID> memberIds = teams.setMembers(id, req.memberIds());
        Team team = teams.get(id);
        return ApiResponse.of(TeamResponse.from(team, memberIds));
    }
}
