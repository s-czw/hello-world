package app.cairn.api.orgs;

import app.cairn.api.core.db.OrgResolver;
import app.cairn.api.core.error.ConflictException;
import app.cairn.api.core.org.OrgContext;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.orgs.member.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization lifecycle. In M1 the only mutation is first-run {@link #bootstrap}, which is guarded so
 * a second org can never be created (D-028 single-org). Reads expose the org name for {@code /auth/me}.
 */
@Service
public class OrgService {

    private final OrgResolver orgResolver;
    private final OrgRepository orgRepository;
    private final MembershipService membershipService;
    private final OrgContext orgContext;

    public OrgService(
            OrgResolver orgResolver,
            OrgRepository orgRepository,
            MembershipService membershipService,
            OrgContext orgContext) {
        this.orgResolver = orgResolver;
        this.orgRepository = orgRepository;
        this.membershipService = membershipService;
        this.orgContext = orgContext;
    }

    public boolean needsBootstrap() {
        return orgResolver.needsBootstrap();
    }

    public String orgName(UUID orgId) {
        return orgRepository.nameById(orgId).orElse(null);
    }

    /**
     * First-run setup: create the one org, its General team, and the admin (user + admin membership).
     * 409 if an org already exists. Atomic.
     */
    @Transactional
    public BootstrapResult bootstrap(String orgName, String adminName, String email, String rawPassword) {
        if (!orgResolver.needsBootstrap()) {
            throw new ConflictException("An organization already exists");
        }
        UUID orgId = orgRepository.insertOrganization(orgName);
        // Make the freshly created org the active context so org-scoped writes below are stamped.
        orgContext.set(orgId);

        UUID userId = membershipService.createMember(email, adminName, rawPassword, Role.ADMIN);
        orgRepository.insertTeam("General");

        return new BootstrapResult(orgId, orgName, userId, adminName, email);
    }
}
