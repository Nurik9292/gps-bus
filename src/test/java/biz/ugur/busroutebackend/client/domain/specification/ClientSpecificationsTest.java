package biz.ugur.busroutebackend.client.domain.specification;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ClientSpecificationsTest {

    private Client make(ClientStatus status, Platform platform, boolean otp, String name, String phone,
                         String access, String refresh, LocalDateTime lastActivity, LocalDateTime createdAt) {
        return Client.fromDatabase(
                biz.ugur.busroutebackend.client.domain.valueobject.ClientId.generate(),
                name, phone, "12345", otp, platform, status,
                lastActivity, access, refresh,
                createdAt != null ? createdAt : LocalDateTime.now(),
                LocalDateTime.now(), 0L, null, null
        );
    }

    private Client active() {
        return make(ClientStatus.ACTIVE, Platform.ANDROID, true, "Ivan", "+99361234567",
                "a", "r", LocalDateTime.now(), LocalDateTime.now().minusDays(1));
    }

    private Client inactive() {
        return make(ClientStatus.INACTIVE, Platform.IOS, false, "Jan", "+99361234568",
                null, null, LocalDateTime.now(), LocalDateTime.now().minusDays(10));
    }

    @Nested
    class Status {

        @Test
        void isActiveMatchesActiveStatus() {
            Specification<Client> spec = ClientSpecifications.isActive();
            assertTrue(spec.isSatisfiedBy(active()));
            assertFalse(spec.isSatisfiedBy(inactive()));
            assertTrue(spec.toSqlCriteria().getWhereClause().contains("status = :status"));
            assertEquals("ACTIVE", spec.toSqlCriteria().getParameters().get("status"));
        }

        @Test
        void isInactiveMatchesOnlyInactive() {
            Specification<Client> spec = ClientSpecifications.isInactive();
            assertTrue(spec.isSatisfiedBy(inactive()));
            assertFalse(spec.isSatisfiedBy(active()));
            assertEquals("INACTIVE", spec.toSqlCriteria().getParameters().get("status"));
        }

        @Test
        void isSuspendedMatchesSuspended() {
            Client suspended = make(ClientStatus.SUSPENDED, Platform.ANDROID, true, "x", "+99361111111",
                    null, null, LocalDateTime.now(), null);
            Specification<Client> spec = ClientSpecifications.isSuspended();
            assertTrue(spec.isSatisfiedBy(suspended));
            assertFalse(spec.isSatisfiedBy(active()));
            assertEquals("SUSPENDED", spec.toSqlCriteria().getParameters().get("status"));
        }

        @Test
        void isBlockedMatchesBlocked() {
            Client blocked = make(ClientStatus.BLOCKED, Platform.IOS, true, "b", "+99361111112",
                    null, null, LocalDateTime.now(), null);
            Specification<Client> spec = ClientSpecifications.isBlocked();
            assertTrue(spec.isSatisfiedBy(blocked));
            assertFalse(spec.isSatisfiedBy(active()));
        }

        @Test
        void hasStatusParameterised() {
            Specification<Client> spec = ClientSpecifications.hasStatus(ClientStatus.INACTIVE);
            assertTrue(spec.isSatisfiedBy(inactive()));
            assertFalse(spec.isSatisfiedBy(active()));
            assertEquals("INACTIVE", spec.toSqlCriteria().getParameters().get("status"));
        }
    }

    @Nested
    class PlatformChecks {

        @Test
        void isAndroidMatchesAndroidClient() {
            Specification<Client> spec = ClientSpecifications.isAndroid();
            assertTrue(spec.isSatisfiedBy(active()));
            assertFalse(spec.isSatisfiedBy(inactive()));
            assertEquals("ANDROID", spec.toSqlCriteria().getParameters().get("platform"));
        }

        @Test
        void isIOSMatchesIOSClient() {
            Specification<Client> spec = ClientSpecifications.isIOS();
            assertTrue(spec.isSatisfiedBy(inactive()));
            assertFalse(spec.isSatisfiedBy(active()));
        }

        @Test
        void isWebMatchesWebClient() {
            Client webClient = make(ClientStatus.ACTIVE, Platform.WEB, true, "w", "+99361111113",
                    null, null, LocalDateTime.now(), null);
            assertTrue(ClientSpecifications.isWeb().isSatisfiedBy(webClient));
            assertFalse(ClientSpecifications.isWeb().isSatisfiedBy(active()));
        }

        @Test
        void hasPlatformParameterised() {
            Specification<Client> spec = ClientSpecifications.hasPlatform(Platform.IOS);
            assertTrue(spec.isSatisfiedBy(inactive()));
            assertEquals("IOS", spec.toSqlCriteria().getParameters().get("platform"));
        }
    }

    @Nested
    class Phone {

        @Test
        void hasPhoneExactMatch() {
            Specification<Client> spec = ClientSpecifications.hasPhone("+99361234567");
            assertTrue(spec.isSatisfiedBy(active()));
            assertFalse(spec.isSatisfiedBy(inactive()));
        }

        @Test
        void hasPhoneNullFragmentSatisfiedByNothing() {
            Specification<Client> spec = ClientSpecifications.hasPhone(null);
            assertFalse(spec.isSatisfiedBy(active()));
        }

        @Test
        void phoneContainsFragmentMatchesSubstring() {
            Specification<Client> spec = ClientSpecifications.phoneContains("123");
            assertTrue(spec.isSatisfiedBy(active()));
        }

        @Test
        void nameContainsIsCaseInsensitive() {
            Specification<Client> spec = ClientSpecifications.nameContains("iVa");
            assertTrue(spec.isSatisfiedBy(active()));
        }
    }

    @Nested
    class Otp {

        @Test
        void hasVerifiedOtpMatchesVerified() {
            assertTrue(ClientSpecifications.hasVerifiedOtp().isSatisfiedBy(active()));
            assertFalse(ClientSpecifications.hasVerifiedOtp().isSatisfiedBy(inactive()));
        }

        @Test
        void hasUnverifiedOtpMatchesUnverified() {
            assertTrue(ClientSpecifications.hasUnverifiedOtp().isSatisfiedBy(inactive()));
            assertFalse(ClientSpecifications.hasUnverifiedOtp().isSatisfiedBy(active()));
        }
    }

    @Nested
    class Tokens {

        @Test
        void hasValidTokensMatchesWhenBothTokensSet() {
            assertTrue(ClientSpecifications.hasValidTokens().isSatisfiedBy(active()));
            assertFalse(ClientSpecifications.hasValidTokens().isSatisfiedBy(inactive()));
            SqlCriteria c = ClientSpecifications.hasValidTokens().toSqlCriteria();
            assertTrue(c.getWhereClause().contains("access_token IS NOT NULL"));
        }

        @Test
        void hasNoTokensMatchesWhenTokenMissing() {
            assertTrue(ClientSpecifications.hasNoTokens().isSatisfiedBy(inactive()));
            assertFalse(ClientSpecifications.hasNoTokens().isSatisfiedBy(active()));
        }
    }

    @Nested
    class ActivityAndCreation {

        @Test
        void hasActivityAfterMatchesFutureActivity() {
            LocalDateTime pivot = LocalDateTime.now().minusMinutes(5);
            assertTrue(ClientSpecifications.hasActivityAfter(pivot).isSatisfiedBy(active()));
        }

        @Test
        void hasActivityBeforeMatchesPastActivity() {
            LocalDateTime pivot = LocalDateTime.now().plusMinutes(5);
            assertTrue(ClientSpecifications.hasActivityBefore(pivot).isSatisfiedBy(active()));
        }

        @Test
        void hasRecentActivityDaysWorks() {
            assertTrue(ClientSpecifications.hasRecentActivity(1).isSatisfiedBy(active()));
            Client old = make(ClientStatus.ACTIVE, Platform.ANDROID, true, "x", "+99361111115",
                    null, null, LocalDateTime.now().minusDays(30), null);
            assertFalse(ClientSpecifications.hasRecentActivity(1).isSatisfiedBy(old));
        }

        @Test
        void hasNoRecentActivityNegatesRecentActivity() {
            Client old = make(ClientStatus.ACTIVE, Platform.ANDROID, true, "x", "+99361111116",
                    null, null, LocalDateTime.now().minusDays(30), null);
            assertTrue(ClientSpecifications.hasNoRecentActivity(10).isSatisfiedBy(old));
        }

        @Test
        void createdAfterAndCreatedBefore() {
            LocalDateTime pivot = LocalDateTime.now().minusHours(5);
            assertTrue(ClientSpecifications.createdAfter(LocalDateTime.now().minusDays(30)).isSatisfiedBy(active()));
            assertTrue(ClientSpecifications.createdBefore(LocalDateTime.now().plusDays(1)).isSatisfiedBy(active()));
        }
    }

    @Nested
    class Composite {

        @Test
        void isActiveAndVerifiedRequiresBothConditions() {
            assertTrue(ClientSpecifications.isActiveAndVerified().isSatisfiedBy(active()));
            assertFalse(ClientSpecifications.isActiveAndVerified().isSatisfiedBy(inactive()));
        }

        @Test
        void canAuthenticateRequiresActiveVerifiedAndTokens() {
            assertTrue(ClientSpecifications.canAuthenticate().isSatisfiedBy(active()));
            Client noTokens = make(ClientStatus.ACTIVE, Platform.ANDROID, true, "x", "+99361111117",
                    null, null, LocalDateTime.now(), null);
            assertFalse(ClientSpecifications.canAuthenticate().isSatisfiedBy(noTokens));
        }

        @Test
        void needsOtpVerificationMatchesInactiveUnverified() {
            assertTrue(ClientSpecifications.needsOtpVerification().isSatisfiedBy(inactive()));
            assertFalse(ClientSpecifications.needsOtpVerification().isSatisfiedBy(active()));
        }

        @Test
        void hasProblemStatusMatchesSuspendedOrBlocked() {
            Client suspended = make(ClientStatus.SUSPENDED, Platform.ANDROID, true, "s", "+99361111118",
                    null, null, LocalDateTime.now(), null);
            assertTrue(ClientSpecifications.hasProblemStatus().isSatisfiedBy(suspended));
            assertFalse(ClientSpecifications.hasProblemStatus().isSatisfiedBy(active()));
        }

        @Test
        void hasRecentActivityOnSpecificPlatform() {
            assertTrue(ClientSpecifications.hasRecentActivityOn(Platform.ANDROID, 7).isSatisfiedBy(active()));
            assertFalse(ClientSpecifications.hasRecentActivityOn(Platform.IOS, 7).isSatisfiedBy(active()));
        }

        @Test
        void searchByTextMatchesPhoneOrName() {
            assertTrue(ClientSpecifications.searchByText("Ivan").isSatisfiedBy(active()));
            assertTrue(ClientSpecifications.searchByText("1234").isSatisfiedBy(active()));
            assertFalse(ClientSpecifications.searchByText("nothing-here").isSatisfiedBy(active()));
        }
    }
}
