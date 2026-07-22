package app.cairn.api.auth.provider;

import app.cairn.api.orgs.member.MemberAccount;
import app.cairn.api.orgs.member.MembershipService;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * M1's only identity provider (D-020): verifies an email + argon2id password against a local account.
 * Enabled via {@code AUTH_LOCAL_ENABLED} (default true). Never reveals whether the email or the
 * password was wrong, and denies inactive members.
 */
@Component
public class LocalPasswordProvider implements IdentityProvider {

    private final MembershipService memberships;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    // A real argon2id hash of a random value; comparing against it spends hashing time on unknown users.
    private final String dummyHash;

    public LocalPasswordProvider(
            MembershipService memberships,
            PasswordEncoder passwordEncoder,
            @Value("${cairn.auth.local.enabled}") boolean enabled) {
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.dummyHash = passwordEncoder.encode("cairn-timing-dummy-" + java.util.UUID.randomUUID());
    }

    @Override
    public String key() {
        return "local";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public IdentityClaims authenticate(Credentials credentials) {
        Optional<MemberAccount> found = memberships.findByEmail(credentials.email());
        String storedHash = found.map(MemberAccount::passwordHash).orElse(null);

        // Always run the encoder to keep the timing of "no such user" and "wrong password" similar.
        boolean passwordOk = passwordEncoder.matches(
                credentials.rawPassword(), storedHash != null ? storedHash : dummyHash);

        if (found.isEmpty() || !found.get().active() || storedHash == null || !passwordOk) {
            throw new BadCredentialsException("Invalid email or password");
        }
        MemberAccount account = found.get();
        return new IdentityClaims(key(), account.userId().toString(), account.email(), true, account.name());
    }
}
