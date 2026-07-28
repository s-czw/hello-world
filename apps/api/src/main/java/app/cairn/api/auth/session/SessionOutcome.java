package app.cairn.api.auth.session;

import app.cairn.api.orgs.member.MemberAccount;
import java.util.List;
import org.springframework.http.ResponseCookie;

/** The Set-Cookie headers to emit plus the authenticated account, for building the response body. */
public record SessionOutcome(List<ResponseCookie> cookies, MemberAccount account) {}
