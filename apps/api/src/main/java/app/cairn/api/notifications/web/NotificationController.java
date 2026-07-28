package app.cairn.api.notifications.web;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.auth.CurrentUser;
import app.cairn.api.core.web.ApiResponse;
import app.cairn.api.core.web.Cursor;
import app.cairn.api.notifications.Notification;
import app.cairn.api.notifications.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * In-app notifications inbox (F1). All endpoints are scoped to the authenticated caller — a user only
 * ever sees or mutates their own notifications; a notification that is not the caller's reads as 404.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;

    public NotificationController(
            NotificationService notifications, CurrentUser currentUser, ObjectMapper mapper) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    /** The caller's notifications, newest first, cursor-paginated. */
    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        AuthPrincipal caller = currentUser.require();
        int pageSize = Cursor.clampLimit(limit);
        UUID after = Cursor.decode(cursor);

        List<Notification> rows = notifications.list(caller.userId(), after, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            nextCursor = Cursor.encode(rows.get(rows.size() - 1).id());
        }
        return ApiResponse.page(rows.stream().map(n -> NotificationResponse.from(n, mapper)).toList(), nextCursor);
    }

    /** The caller's unread count (the client caps the badge at "9+"). */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        AuthPrincipal caller = currentUser.require();
        return ApiResponse.of(new UnreadCountResponse(notifications.unreadCount(caller.userId())));
    }

    /** Mark one notification read. 404 if it is not the caller's. */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        AuthPrincipal caller = currentUser.require();
        notifications.markRead(caller, id);
        return ResponseEntity.noContent().build();
    }

    /** Mark all of the caller's notifications read. */
    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll() {
        AuthPrincipal caller = currentUser.require();
        notifications.markAllRead(caller.userId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
