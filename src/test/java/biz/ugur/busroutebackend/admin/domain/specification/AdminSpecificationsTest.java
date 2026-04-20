package biz.ugur.busroutebackend.admin.domain.specification;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AdminSpecificationsTest {

    private Admin active() {
        return Admin.create("ivan", "encoded", "Ivan Ivanov", null, false, true);
    }

    private Admin deactivated() {
        return Admin.create("mary", "encoded", "Mary", null, false, false);
    }

    private Admin superAdmin() {
        return Admin.create("root", "encoded", "Super Root", null, true, true);
    }

    @Nested
    class ActiveStatus {

        @Test
        void isActiveMatches() {
            assertTrue(AdminSpecifications.isActive().isSatisfiedBy(active()));
            assertFalse(AdminSpecifications.isActive().isSatisfiedBy(deactivated()));
        }

        @Test
        void isInactiveMatches() {
            assertTrue(AdminSpecifications.isInactive().isSatisfiedBy(deactivated()));
        }
    }

    @Nested
    class Role {

        @Test
        void isSuperAdminMatches() {
            assertTrue(AdminSpecifications.isSuperAdmin().isSatisfiedBy(superAdmin()));
            assertFalse(AdminSpecifications.isSuperAdmin().isSatisfiedBy(active()));
        }

        @Test
        void isRegularAdminMatchesNonSuper() {
            assertTrue(AdminSpecifications.isRegularAdmin().isSatisfiedBy(active()));
            assertFalse(AdminSpecifications.isRegularAdmin().isSatisfiedBy(superAdmin()));
        }
    }

    @Nested
    class TextSearch {

        @Test
        void usernameContainsIsCaseInsensitive() {
            assertTrue(AdminSpecifications.usernameContains("Iv").isSatisfiedBy(active()));
            assertFalse(AdminSpecifications.usernameContains("unknown").isSatisfiedBy(active()));
        }

        @Test
        void fullNameContainsIsCaseInsensitive() {
            assertTrue(AdminSpecifications.fullNameContains("iVaNoV").isSatisfiedBy(active()));
        }

        @Test
        void hasUsernameMatchesExact() {
            assertTrue(AdminSpecifications.hasUsername("ivan").isSatisfiedBy(active()));
            assertFalse(AdminSpecifications.hasUsername("Ivan").isSatisfiedBy(active()));
        }

        @Test
        void searchByTextMatchesEitherUsernameOrFullName() {
            assertTrue(AdminSpecifications.searchByText("Ivan").isSatisfiedBy(active()));
            assertTrue(AdminSpecifications.searchByText("Ivanov").isSatisfiedBy(active()));
            assertFalse(AdminSpecifications.searchByText("nothing").isSatisfiedBy(active()));
        }
    }

    @Nested
    class Identity {

        @Test
        void hasIdMatchesAdminId() {
            Admin admin = active();
            assertTrue(AdminSpecifications.hasId(admin.getId().getValue()).isSatisfiedBy(admin));
            assertFalse(AdminSpecifications.hasId("nope").isSatisfiedBy(admin));
        }
    }

    @Nested
    class Timestamps {

        @Test
        void lastLoginAfterUsesPivot() {
            Admin admin = Admin.fromDatabase(
                    AdminId.generate(), "ivan", "hash", "Ivan", null,
                    true, false, LocalDateTime.now(),
                    LocalDateTime.now(), LocalDateTime.now(), 0L);
            assertTrue(AdminSpecifications.lastLoginAfter(LocalDateTime.now().minusHours(1))
                    .isSatisfiedBy(admin));
        }

        @Test
        void lastLoginBeforeUsesPivot() {
            Admin admin = Admin.fromDatabase(
                    AdminId.generate(), "ivan", "hash", "Ivan", null,
                    true, false, LocalDateTime.now().minusDays(10),
                    LocalDateTime.now().minusDays(10), LocalDateTime.now(), 0L);
            assertTrue(AdminSpecifications.lastLoginBefore(LocalDateTime.now().minusDays(5))
                    .isSatisfiedBy(admin));
        }

        @Test
        void neverLoggedInMatchesNullLastLogin() {
            Admin admin = Admin.fromDatabase(
                    AdminId.generate(), "ivan", "hash", "Ivan", null,
                    true, false, null,
                    LocalDateTime.now(), LocalDateTime.now(), 0L);
            assertTrue(AdminSpecifications.neverLoggedIn().isSatisfiedBy(admin));
        }

        @Test
        void neverLoggedInFalseForActive() {
            assertFalse(AdminSpecifications.neverLoggedIn().isSatisfiedBy(active()));
        }

        @Test
        void createdAfterAndBefore() {
            Admin admin = active();
            assertTrue(AdminSpecifications.createdAfter(LocalDateTime.now().minusHours(1))
                    .isSatisfiedBy(admin));
            assertTrue(AdminSpecifications.createdBefore(LocalDateTime.now().plusHours(1))
                    .isSatisfiedBy(admin));
        }

        @Test
        void hasRecentLoginRespectsDayWindow() {
            assertTrue(AdminSpecifications.hasRecentLogin(1).isSatisfiedBy(active()));
        }

        @Test
        void hasStaleLoginTrueForOldOrNever() {
            Admin old = Admin.fromDatabase(
                    AdminId.generate(), "ivan", "hash", "Ivan", null,
                    true, false, LocalDateTime.now().minusDays(60),
                    LocalDateTime.now(), LocalDateTime.now(), 0L);
            Admin never = Admin.fromDatabase(
                    AdminId.generate(), "nobody", "hash", "Nobody", null,
                    true, false, null,
                    LocalDateTime.now(), LocalDateTime.now(), 0L);
            assertTrue(AdminSpecifications.hasStaleLogin(30).isSatisfiedBy(old));
            assertTrue(AdminSpecifications.hasStaleLogin(30).isSatisfiedBy(never));
        }
    }

    @Nested
    class Avatar {

        @Test
        void hasAvatarFalseWhenMissing() {
            assertFalse(AdminSpecifications.hasAvatar().isSatisfiedBy(active()));
        }

        @Test
        void hasAvatarTrueWhenSet() {
            Admin withAvatar = Admin.create("ivan", "hash", "Ivan", "avatar.jpg", false, true);
            assertTrue(AdminSpecifications.hasAvatar().isSatisfiedBy(withAvatar));
        }

        @Test
        void noAvatarTrueWhenMissing() {
            assertTrue(AdminSpecifications.noAvatar().isSatisfiedBy(active()));
        }
    }

    @Nested
    class Composite {

        @Test
        void isActiveRegularAdminMatchesActiveNonSuper() {
            assertTrue(AdminSpecifications.isActiveRegularAdmin().isSatisfiedBy(active()));
            assertFalse(AdminSpecifications.isActiveRegularAdmin().isSatisfiedBy(superAdmin()));
            assertFalse(AdminSpecifications.isActiveRegularAdmin().isSatisfiedBy(deactivated()));
        }

        @Test
        void isActiveSuperAdminMatchesOnlyActiveSuper() {
            assertTrue(AdminSpecifications.isActiveSuperAdmin().isSatisfiedBy(superAdmin()));
            assertFalse(AdminSpecifications.isActiveSuperAdmin().isSatisfiedBy(active()));
        }
    }
}
