package app.cairn.api.orgs.member.web;

/**
 * PATCH body for a member. Both fields optional; null means "leave unchanged". {@code role} must be
 * {@code admin} or {@code member} (validated in the service, which also enforces the last-admin guard).
 */
public record UpdateUserRequest(String role, Boolean active) {}
