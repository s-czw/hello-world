package app.cairn.api.orgs.member;

import app.cairn.api.core.error.ConflictException;
import app.cairn.api.core.error.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public orgs service for users/members (the only entry point other modules use — auth calls
 * {@link #findByEmail}/{@link #findByUserId}; invites call {@link #createMember}). Enforces the
 * last-active-admin guard on updates.
 */
@Service
public class MembershipService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public MembershipService(
            UserRepository userRepository,
            MembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<MemberAccount> findByEmail(String email) {
        return membershipRepository.findByEmail(email);
    }

    public Optional<MemberAccount> findByUserId(UUID userId) {
        return membershipRepository.findByUserId(userId);
    }

    public List<MemberAccount> listMembers(UUID afterUserId, int limit) {
        return membershipRepository.page(afterUserId, limit);
    }

    /**
     * Create a user and their membership in the current org. 409 if the email is already taken.
     * The raw password is hashed here (argon2id) so the module owning {@code users} owns hashing.
     */
    @Transactional
    public UUID createMember(String email, String name, String rawPassword, String role) {
        if (userRepository.emailExists(email)) {
            throw new ConflictException("A user with that email already exists");
        }
        UUID userId = userRepository.insert(email, name, passwordEncoder.encode(rawPassword));
        membershipRepository.insert(userId, role, true);
        return userId;
    }

    /**
     * Update a member's role/active. Null fields are left unchanged. Rejects (409) any change that
     * would leave the org without an active admin (last-active-admin guard). 404 if not a member.
     */
    @Transactional
    public MemberAccount updateMember(UUID userId, String newRole, Boolean newActive) {
        MemberAccount current =
                membershipRepository.findByUserId(userId).orElseThrow(() -> NotFoundException.of("User"));

        if (newRole != null && !Role.isValid(newRole)) {
            throw new app.cairn.api.core.error.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid role");
        }

        String effectiveRole = newRole != null ? newRole : current.role();
        boolean effectiveActive = newActive != null ? newActive : current.active();

        if (AdminGuard.wouldRemoveLastActiveAdmin(
                current.role(), current.active(), effectiveRole, effectiveActive, membershipRepository.countActiveAdmins())) {
            throw new ConflictException("Cannot demote or deactivate the last active admin");
        }

        membershipRepository.updateRoleAndActive(userId, effectiveRole, effectiveActive);
        return membershipRepository.findByUserId(userId).orElseThrow(() -> NotFoundException.of("User"));
    }
}
