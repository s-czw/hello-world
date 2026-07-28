package app.cairn.api.orgs.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminGuardTest {

    @Test
    void demotingTheOnlyActiveAdminIsBlocked() {
        assertThat(AdminGuard.wouldRemoveLastActiveAdmin("admin", true, "member", true, 1))
                .isTrue();
    }

    @Test
    void deactivatingTheOnlyActiveAdminIsBlocked() {
        assertThat(AdminGuard.wouldRemoveLastActiveAdmin("admin", true, "admin", false, 1))
                .isTrue();
    }

    @Test
    void demotingOneAdminOfTwoIsAllowed() {
        assertThat(AdminGuard.wouldRemoveLastActiveAdmin("admin", true, "member", true, 2))
                .isFalse();
    }

    @Test
    void noOpUpdateOnLastAdminIsAllowed() {
        // Staying an active admin never trips the guard even when they are the last one.
        assertThat(AdminGuard.wouldRemoveLastActiveAdmin("admin", true, "admin", true, 1))
                .isFalse();
    }

    @Test
    void changingANonAdminNeverTripsGuard() {
        assertThat(AdminGuard.wouldRemoveLastActiveAdmin("member", true, "member", false, 1))
                .isFalse();
        assertThat(AdminGuard.wouldRemoveLastActiveAdmin("member", true, "admin", true, 0))
                .isFalse();
    }

    @Test
    void alreadyInactiveAdminIsNotCountedAsLosing() {
        // An inactive admin was not an active admin, so deactivating/demoting can't remove the last one.
        assertThat(AdminGuard.wouldRemoveLastActiveAdmin("admin", false, "member", false, 1))
                .isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}/{1} -> {2}/{3} count={4} => blocked={5}")
    @CsvSource({
        "admin,true,member,true,1,true",
        "admin,true,admin,false,1,true",
        "admin,true,member,false,1,true",
        "admin,true,member,true,2,false",
        "admin,true,admin,true,1,false",
        "member,true,member,false,1,false",
    })
    void exhaustiveMatrix(
            String curRole, boolean curActive, String newRole, boolean newActive, int count, boolean blocked) {
        assertThat(AdminGuard.wouldRemoveLastActiveAdmin(curRole, curActive, newRole, newActive, count))
                .isEqualTo(blocked);
    }
}
