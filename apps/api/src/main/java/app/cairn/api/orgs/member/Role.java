package app.cairn.api.orgs.member;

/** Membership roles (D-009: role lives on the membership, not the user). */
public final class Role {
    public static final String ADMIN = "admin";
    public static final String MEMBER = "member";

    private Role() {}

    public static boolean isValid(String role) {
        return ADMIN.equals(role) || MEMBER.equals(role);
    }
}
