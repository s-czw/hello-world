package app.cairn.api.orgs;

import java.util.UUID;

/** Outcome of first-run bootstrap: the created org and its admin user. */
public record BootstrapResult(UUID orgId, String orgName, UUID adminUserId, String adminName, String adminEmail) {}
