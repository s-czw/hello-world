package app.cairn.api.auth.provider;

/**
 * The multi-provider identity port (D-020, arch §4.3). Each provider proves who a caller is and
 * returns {@link IdentityClaims}; session issuance never learns which provider was used and provider
 * tokens are consumed here only, never passed downstream. M1 ships only {@link LocalPasswordProvider};
 * Lark/Entra add new implementations behind an env toggle without changing anything else.
 */
public interface IdentityProvider {

    /** Stable provider key, e.g. {@code "local"}. */
    String key();

    /** Whether this provider is enabled for the deployment (env-toggled). */
    boolean enabled();

    /**
     * Authenticate the supplied credentials, returning identity claims on success.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException on any failure
     */
    IdentityClaims authenticate(Credentials credentials);

    /** Login input for a password provider. Other providers may ignore fields they don't use. */
    record Credentials(String email, String rawPassword) {}
}
