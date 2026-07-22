package app.cairn.api.auth.provider;

/**
 * Provider-independent identity assertion (arch §4.3 {@code IdentityProvider} port).
 *
 * <p>Whatever provider authenticates a caller returns these claims; session issuance and every
 * downstream module are identical regardless of provider. In M1 only {@code local} exists; Lark/Entra
 * drop in later without touching session issuance or the security filter chain (D-020).
 *
 * @param provider      provider key, e.g. {@code "local"}
 * @param subject       the resolved first-party user id (as a string)
 * @param email         the authenticated email
 * @param emailVerified whether the provider vouches the email is verified
 * @param name          display name known to the provider (may be null)
 */
public record IdentityClaims(String provider, String subject, String email, boolean emailVerified, String name) {}
