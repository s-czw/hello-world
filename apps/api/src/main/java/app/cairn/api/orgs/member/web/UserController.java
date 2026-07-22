package app.cairn.api.orgs.member.web;

import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import app.cairn.api.orgs.member.MemberAccount;
import app.cairn.api.orgs.member.MembershipService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Members of the single org. Listing is available to any authenticated member (assignee pickers);
 * role/active changes are admin-only and pass through the last-active-admin guard (D-009).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final MembershipService membershipService;
    private final CurrentUser currentUser;

    public UserController(MembershipService membershipService, CurrentUser currentUser) {
        this.membershipService = membershipService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> list(
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        currentUser.require();
        int pageSize = Cursor.clampLimit(limit);
        UUID after = Cursor.decode(cursor);

        List<MemberAccount> rows = membershipService.listMembers(after, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            nextCursor = Cursor.encode(rows.get(rows.size() - 1).userId());
        }
        return ApiResponse.page(rows.stream().map(UserResponse::from).toList(), nextCursor);
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        currentUser.requireAdmin();
        MemberAccount updated = membershipService.updateMember(id, req.role(), req.active());
        return ApiResponse.of(UserResponse.from(updated));
    }
}
