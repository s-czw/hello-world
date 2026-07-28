package app.cairn.api.orgs.member;

/**
 * Pure last-active-admin invariant (unit-tested without a DB). An org must always retain at least one
 * active admin, so a PATCH that would demote or deactivate the final active admin is rejected (409).
 */
public final class AdminGuard {

    private AdminGuard() {}

    /**
     * @param currentRole      the member's role before the change
     * @param currentActive    the member's active flag before the change
     * @param newRole          the role after the change
     * @param newActive        the active flag after the change
     * @param activeAdminCount total active admins in the org right now (including this member)
     * @return true if applying the change would leave the org with zero active admins
     */
    public static boolean wouldRemoveLastActiveAdmin(
            String currentRole, boolean currentActive, String newRole, boolean newActive, int activeAdminCount) {
        boolean wasActiveAdmin = currentActive && Role.ADMIN.equals(currentRole);
        boolean staysActiveAdmin = newActive && Role.ADMIN.equals(newRole);
        boolean losingAnActiveAdmin = wasActiveAdmin && !staysActiveAdmin;
        return losingAnActiveAdmin && activeAdminCount <= 1;
    }
}
