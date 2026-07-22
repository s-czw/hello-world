package app.cairn.api.orgs.invite.web;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import app.cairn.api.orgs.invite.InviteRow;
import app.cairn.api.orgs.invite.InviteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invites. Create/list/revoke are admin-only; {@code POST /invites/accept} is public (the invitee is
 * not yet a user) and is permit-listed in the security chain.
 */
@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {

    private final InviteService inviteService;
    private final CurrentUser currentUser;

    public InviteController(InviteService inviteService, CurrentUser currentUser) {
        this.inviteService = inviteService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatedInviteResponse> create(@Valid @RequestBody CreateInviteRequest req) {
        AuthPrincipal admin = currentUser.requireAdmin();
        InviteService.CreatedInvite created = inviteService.create(req.email(), admin.userId());
        return ApiResponse.of(CreatedInviteResponse.from(created));
    }

    @GetMapping
    public ApiResponse<List<InviteResponse>> list(
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        currentUser.requireAdmin();
        int pageSize = Cursor.clampLimit(limit);
        UUID after = Cursor.decode(cursor);

        List<InviteRow> rows = inviteService.listPending(after, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            nextCursor = Cursor.encode(rows.get(rows.size() - 1).id());
        }
        return ApiResponse.page(rows.stream().map(InviteResponse::from).toList(), nextCursor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        currentUser.requireAdmin();
        inviteService.revoke(id);
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AcceptedInviteResponse> accept(@Valid @RequestBody AcceptInviteRequest req) {
        InviteService.AcceptedInvite accepted =
                inviteService.accept(req.token(), req.name(), req.password());
        return ApiResponse.of(AcceptedInviteResponse.from(accepted));
    }
}
