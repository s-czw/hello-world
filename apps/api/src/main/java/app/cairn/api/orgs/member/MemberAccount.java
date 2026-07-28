package app.cairn.api.orgs.member;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user together with their membership in the current org (users are global; the org linkage and
 * role/active flags live on {@code memberships}). {@code passwordHash} is populated only for the
 * credential-verification path and is never serialized to clients.
 */
public record MemberAccount(
        UUID userId,
        UUID orgId,
        String email,
        String name,
        String passwordHash,
        String role,
        boolean active,
        OffsetDateTime createdAt) {}
