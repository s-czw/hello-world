package app.cairn.api.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Shared cryptographic primitives (kept in {@code core} so any module owning credentials can hash
 * consistently). The password hash is argon2id (D-020, arch §7 OWASP basics) via Spring Security's
 * {@link Argon2PasswordEncoder}, whose primitives come from Bouncy Castle.
 */
@Configuration
public class CryptoConfig {

    /** Argon2id password encoder (Spring Security v5.8 defaults: 16B salt, 32B hash, m=16384, t=2, p=1). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
