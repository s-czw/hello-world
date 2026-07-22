package app.cairn.api.core.org;

import app.cairn.api.core.db.OrgResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Populates the request-scoped {@link OrgContext} before each controller runs (D-028, M1 single-org).
 *
 * <p>Runs inside the DispatcherServlet where the request scope is active, so it can safely set the
 * request-scoped {@link OrgContext} that {@link app.cairn.api.core.db.OrgScopedDsl} reads. Because M1
 * is single-org, the sole {@code organizations} row is authoritative and matches whatever org an
 * authenticated session would carry. Before bootstrap there is no org and the context stays empty
 * (only the permit-listed bootstrap endpoints run then, and they set it themselves after creating it).
 */
@Component
public class OrgContextInterceptor implements HandlerInterceptor {

    private final OrgResolver orgResolver;
    private final OrgContext orgContext;

    public OrgContextInterceptor(OrgResolver orgResolver, OrgContext orgContext) {
        this.orgResolver = orgResolver;
        this.orgContext = orgContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (orgContext.current().isEmpty()) {
            orgResolver.soleOrgId().ifPresent(orgContext::set);
        }
        return true;
    }
}
