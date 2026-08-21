package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.event.ClientAuthenticatedEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientCreatedViaServiceEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientRegisteredEvent;
import biz.ugur.busroutebackend.client.domain.exceptions.ClientAuthenticationException;
import biz.ugur.busroutebackend.client.domain.exceptions.ClientValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {

    private static final String VALID_PHONE = "+99361234567";
    private static final String NAME = "Merdan";

    private Client freshClient;

    @BeforeEach
    void setUp() {
        freshClient = Client.create(NAME, VALID_PHONE, Platform.ANDROID);
    }

    private static Client activeClient() {
        return Client.createViaExternalService(NAME, "srv-123456", "ext-1", VALID_PHONE);
    }

    @Nested
    class Create {

        @Test
        void shouldCreateInactiveClientWithGeneratedId() {
            assertNotNull(freshClient.getId());
            assertEquals(NAME, freshClient.getName());
            assertEquals(VALID_PHONE, freshClient.getPhoneNumber());
            assertEquals(Platform.ANDROID, freshClient.getPlatform());
            assertEquals(ClientStatus.INACTIVE, freshClient.getStatus());
            assertFalse(freshClient.getOtpVerify());
            assertEquals(0L, freshClient.getVersion());
        }

        @Test
        void shouldRegisterClientRegisteredEvent() {
            assertEquals(1, freshClient.getDomainEvents().size());
            assertInstanceOf(ClientRegisteredEvent.class, freshClient.getDomainEvents().get(0));
        }

        @Test
        void shouldTrimName() {
            Client c = Client.create("  Amangul  ", VALID_PHONE, Platform.IOS);
            assertEquals("Amangul", c.getName());
        }

        @Test
        void shouldRejectBlankName() {
            ClientValidationException ex = assertThrows(ClientValidationException.class,
                    () -> Client.create("  ", VALID_PHONE, Platform.ANDROID));
            assertTrue(ex.getMessage().toLowerCase().contains("name"));
        }

        @Test
        void shouldRejectNameLongerThan100Chars() {
            String longName = "x".repeat(101);
            assertThrows(ClientValidationException.class,
                    () -> Client.create(longName, VALID_PHONE, Platform.ANDROID));
        }

        @Test
        void shouldRejectInvalidPhone() {
            ClientValidationException ex = assertThrows(ClientValidationException.class,
                    () -> Client.create(NAME, "not-a-phone", Platform.ANDROID));
            assertTrue(ex.getMessage().toLowerCase().contains("phone"));
        }
    }

    @Nested
    class ExternalServiceCreation {

        @Test
        void shouldCreateActiveClientWithVirtualPhone() {
            Client integration = Client.createViaExternalService("Intergrator", "srv-123456", "user-abc");

            assertEquals(ClientStatus.ACTIVE, integration.getStatus());
            assertTrue(integration.getOtpVerify());
            assertEquals(Platform.API, integration.getPlatform());
            assertTrue(integration.getPhoneNumber().startsWith("+993INT"));
            assertEquals("srv-123456", integration.getCreatedByServiceId());
            assertEquals("user-abc", integration.getExternalUserId());
            assertTrue(integration.isCreatedByService());
            assertTrue(integration.belongsToService("srv-123456"));
            assertFalse(integration.belongsToService("other"));
            assertTrue(integration.isApiClient());
            assertInstanceOf(ClientCreatedViaServiceEvent.class, integration.getDomainEvents().get(0));
        }

        @Test
        void shouldRejectBlankServiceId() {
            assertThrows(ClientValidationException.class,
                    () -> Client.createViaExternalService(NAME, "  ", "user-1"));
        }

        @Test
        void shouldRejectBlankExternalUserId() {
            assertThrows(ClientValidationException.class,
                    () -> Client.createViaExternalService(NAME, "srv-1", null));
        }

        @Test
        void shouldStoreCanonicalRealPhoneWhenProvided() {
            Client integration = Client.createViaExternalService("Integrator", "srv-123456", "user-abc", "61520000");

            assertEquals("99361520000", integration.getPhoneNumber());
            assertEquals(ClientStatus.ACTIVE, integration.getStatus());
            assertEquals("user-abc", integration.getExternalUserId());
            assertInstanceOf(ClientCreatedViaServiceEvent.class, integration.getDomainEvents().get(0));
        }

        @Test
        void shouldFallBackToVirtualPhoneWhenRealPhoneNull() {
            Client integration = Client.createViaExternalService("Integrator", "srv-123456", "user-abc", null);

            assertTrue(integration.getPhoneNumber().startsWith("+993INT"));
        }
    }

    @Nested
    class ExternalServiceLinking {

        @Test
        void linkExternalServiceAttachesIdentityActivatesAndKeepsRealPhone() {
            Client linked = freshClient.linkExternalService("srv-123456", "user-xyz");

            assertEquals("srv-123456", linked.getCreatedByServiceId());
            assertEquals("user-xyz", linked.getExternalUserId());
            assertEquals(ClientStatus.ACTIVE, linked.getStatus());
            assertTrue(linked.getOtpVerify());
            assertEquals(VALID_PHONE, linked.getPhoneNumber());
            assertTrue(linked.belongsToService("srv-123456"));
            assertInstanceOf(ClientCreatedViaServiceEvent.class, linked.getDomainEvents().get(0));
        }

        @Test
        void linkExternalServiceRejectsBlankArguments() {
            assertThrows(ClientValidationException.class,
                    () -> freshClient.linkExternalService("  ", "user-1"));
            assertThrows(ClientValidationException.class,
                    () -> freshClient.linkExternalService("srv-1", null));
        }

        @Test
        void upgradeIntegrationPhoneReplacesVirtualWithCanonicalReal() {
            Client integration = Client.createViaExternalService("Integrator", "srv-123456", "user-abc", null);
            assertTrue(integration.getPhoneNumber().startsWith("+993INT"));

            Client upgraded = integration.upgradeIntegrationPhone("61520000");

            assertEquals("99361520000", upgraded.getPhoneNumber());
            assertEquals("user-abc", upgraded.getExternalUserId());
            assertEquals("srv-123456", upgraded.getCreatedByServiceId());
        }
    }

    @Nested
    class Authentication {

        @Test
        void authenticateActiveClientSetsTokensAndEmitsEvent() {
            Client active = activeClient();

            Client auth = active.authenticate("access-token", "refresh-token");

            assertEquals("access-token", auth.getAccessToken());
            assertEquals("refresh-token", auth.getRefreshToken());
            assertTrue(auth.hasValidTokens());
            assertInstanceOf(ClientAuthenticatedEvent.class, auth.getDomainEvents().get(0));
        }

        @Test
        void authenticateFailsForInactiveClient() {
            assertThrows(ClientAuthenticationException.class,
                    () -> freshClient.authenticate("access", "refresh"));
        }

        @Test
        void authenticateFailsForSuspendedClient() {
            Client active = activeClient();
            Client suspended = active.suspend();
            assertThrows(ClientAuthenticationException.class,
                    () -> suspended.authenticate("a", "r"));
        }

        @Test
        void isRefreshTokenValidOnlyWhenMatchingAndCanLogin() {
            Client active = activeClient();
            Client auth = active.authenticate("a", "refresh-1");
            assertTrue(auth.isRefreshTokenValid("refresh-1"));
            assertFalse(auth.isRefreshTokenValid("refresh-2"));
            assertFalse(auth.isRefreshTokenValid(null));

            Client blocked = auth.block();
            assertFalse(blocked.isRefreshTokenValid("refresh-1"));
        }

        @Test
        void updateTokensReplacesTokens() {
            Client active = activeClient();
            Client auth = active.authenticate("old-a", "old-r");

            Client updated = auth.updateTokens("new-a", "new-r");

            assertEquals("new-a", updated.getAccessToken());
            assertEquals("new-r", updated.getRefreshToken());
        }

        @Test
        void logoutClearsTokens() {
            Client active = activeClient();
            Client auth = active.authenticate("a", "r");

            Client loggedOut = auth.logout();

            assertNull(loggedOut.getAccessToken());
            assertNull(loggedOut.getRefreshToken());
            assertFalse(loggedOut.hasValidTokens());
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        void suspendClearsTokensAndSetsStatus() {
            Client active = activeClient();
            Client auth = active.authenticate("a", "r");
            Client suspended = auth.suspend();

            assertEquals(ClientStatus.SUSPENDED, suspended.getStatus());
            assertNull(suspended.getAccessToken());
            assertNull(suspended.getRefreshToken());
            assertFalse(suspended.isActive());
        }

        @Test
        void blockSimilarToSuspend() {
            Client active = activeClient();
            Client blocked = active.block();
            assertEquals(ClientStatus.BLOCKED, blocked.getStatus());
        }

        @Test
        void activateReturnsClientWithActiveStatus() {
            Client suspended = activeClient().suspend();
            Client reactivated = suspended.activate();
            assertEquals(ClientStatus.ACTIVE, reactivated.getStatus());
            assertTrue(reactivated.isActive());
        }

        @Test
        void updateActivityChangesLastActivity() throws InterruptedException {
            Client stamped = freshClient.updateActivity();
            assertNotNull(stamped.getLastActivity());
        }
    }

    @Nested
    class Restore {

        @Test
        void fromDatabaseRebuildsStateWithoutEvents() {
            Client restored = Client.fromDatabase(
                    freshClient.getId(),
                    NAME,
                    VALID_PHONE,
                    "12345",
                    true,
                    Platform.IOS,
                    ClientStatus.ACTIVE,
                    freshClient.getLastActivity(),
                    "token-a",
                    "token-r",
                    freshClient.getCreatedAt(),
                    freshClient.getUpdatedAt(),
                    5L,
                    "srv",
                    "ext-user"
            );
            assertEquals(5L, restored.getVersion());
            assertTrue(restored.isOtpVerified());
            assertTrue(restored.hasValidTokens());
            assertEquals(0, restored.getDomainEvents().size());
        }

        @Test
        void fromDatabaseDefaultsVersionToZeroWhenNull() {
            Client restored = Client.fromDatabase(
                    freshClient.getId(), NAME, VALID_PHONE, null, false,
                    Platform.WEB, ClientStatus.INACTIVE,
                    null, null, null, null, null,
                    null, null, null
            );
            assertEquals(0L, restored.getVersion());
        }
    }

    @Test
    void clientStatusCanLoginOnlyForActive() {
        assertTrue(ClientStatus.ACTIVE.canLogin());
        assertFalse(ClientStatus.INACTIVE.canLogin());
        assertFalse(ClientStatus.SUSPENDED.canLogin());
        assertFalse(ClientStatus.BLOCKED.canLogin());
    }
}
