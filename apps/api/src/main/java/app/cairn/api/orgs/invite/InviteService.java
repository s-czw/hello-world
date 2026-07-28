package app.cairn.api.orgs.invite;

import app.cairn.api.core.error.ConflictException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.core.security.Tokens;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.orgs.member.Role;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email invites: an admin mints a single-use token (14-day expiry) whose SHA-256 hash is stored; the
 * plaintext copy-paste link is returned exactly once. Accepting the token creates a user + membership
 * and consumes the invite. All invite rows are org-scoped via the repository.
 */
@Service
public class InviteService {

    private final InviteRepository invites;
    private final MembershipService memberships;
    private final int ttlDays;
    private final String acceptUrlBase;

    public InviteService(
            InviteRepository invites,
            MembershipService memberships,
            @Value("${cairn.invite.ttl-days}") int ttlDays,
            @Value("${cairn.invite.accept-url-base}") String acceptUrlBase) {
        this.invites = invites;
        this.memberships = memberships;
        this.ttlDays = ttlDays;
        this.acceptUrlBase = acceptUrlBase;
    }

    /** Create an invite for an email; returns the id/expiry plus the one-time accept URL. */
    @Transactional
    public CreatedInvite create(String email, UUID invitedBy) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (memberships.findByEmail(normalized).isPresent()) {
            throw new ConflictException("That email is already a member");
        }
        String token = Tokens.randomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(ttlDays);
        UUID id = invites.insert(normalized, Tokens.sha256Hex(token), invitedBy, expiresAt);
        String acceptUrl = acceptUrlBase + "?token=" + token;
        return new CreatedInvite(id, normalized, expiresAt, acceptUrl);
    }

    public List<InviteRow> listPending(UUID afterId, int limit) {
        return invites.pagePending(afterId, limit);
    }

    /** Revoke a still-pending invite. 404 if it doesn't exist / was already consumed or revoked. */
    @Transactional
    public void revoke(UUID id) {
        if (invites.revoke(id, OffsetDateTime.now()) == 0) {
            throw NotFoundException.of("Invite");
        }
    }

    /** Consume a token: create the user + membership and mark the invite accepted. */
    @Transactional
    public AcceptedInvite accept(String token, String name, String rawPassword) {
        OffsetDateTime now = OffsetDateTime.now();
        InviteRow row = invites
                .findByTokenHash(Tokens.sha256Hex(token))
                .orElseThrow(() -> NotFoundException.of("Invite"));
        if (!row.isPending(now)) {
            throw new ConflictException("Invite is no longer valid");
        }
        UUID userId = memberships.createMember(row.email(), name, rawPassword, Role.MEMBER);
        invites.markAccepted(row.id(), now);
        return new AcceptedInvite(userId, row.email());
    }

    /** Result of {@link #create}: metadata plus the one-time accept URL (token embedded). */
    public record CreatedInvite(UUID id, String email, OffsetDateTime expiresAt, String acceptUrl) {}

    /** Result of {@link #accept}: the newly created member. */
    public record AcceptedInvite(UUID userId, String email) {}
}
