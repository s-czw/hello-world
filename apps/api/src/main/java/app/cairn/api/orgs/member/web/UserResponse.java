package app.cairn.api.orgs.member.web;

import app.cairn.api.orgs.member.MemberAccount;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A member as returned to clients (no password hash). */
public record UserResponse(
        UUID id, String email, String name, String role, boolean active, OffsetDateTime createdAt) {

    public static UserResponse from(MemberAccount account) {
        return new UserResponse(
                account.userId(),
                account.email(),
                account.name(),
                account.role(),
                account.active(),
                account.createdAt());
    }
}
