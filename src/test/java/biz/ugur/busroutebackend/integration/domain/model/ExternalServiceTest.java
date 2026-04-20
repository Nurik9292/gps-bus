package biz.ugur.busroutebackend.integration.domain.model;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.domain.events.ExternalServiceBlockedEvent;
import biz.ugur.busroutebackend.integration.domain.events.ExternalServiceCreatedEvent;
import biz.ugur.busroutebackend.integration.domain.events.ExternalServiceUnblockedEvent;
import biz.ugur.busroutebackend.integration.domain.events.ExternalServiceUpdatedEvent;
import biz.ugur.busroutebackend.integration.domain.exceptions.ClientManagementNotAllowedException;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceBlockedException;
import biz.ugur.busroutebackend.integration.domain.exceptions.UnauthorizedEndpointException;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ApiToken;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExternalServiceTest {

    private ExternalService service;
    private AdminId adminId;

    @BeforeEach
    void setUp() {
        adminId = AdminId.generate();
        service = ExternalService.create(
                "Integration Partner",
                "Description",
                adminId,
                List.of("/api/v1/clients/**"),
                60,
                true);
    }

    @Nested
    class Create {

        @Test
        void createsActiveServiceWithGeneratedTokenAndId() {
            assertNotNull(service.getId());
            assertNotNull(service.getApiToken());
            assertTrue(service.getIsActive());
            assertEquals("Integration Partner", service.getName());
            assertEquals("Description", service.getDescription());
            assertEquals(60, service.getRateLimitPerMinute());
            assertTrue(service.getCanManageClients());
            assertEquals(1, service.getDomainEvents().size());
            assertInstanceOf(ExternalServiceCreatedEvent.class, service.getDomainEvents().get(0));
        }

        @Test
        void createDefaultsCanManageClientsToFalseWhenNull() {
            ExternalService s = ExternalService.create("Shop", null, adminId, null, null, null);
            assertFalse(s.canManageClients());
        }

        @Test
        void createTrimsNameAndDescription() {
            ExternalService s = ExternalService.create("  Hello  ", "  desc  ",
                    adminId, null, null, true);
            assertEquals("Hello", s.getName());
            assertEquals("desc", s.getDescription());
        }

        @Test
        void createRejectsBlankName() {
            assertThrows(IllegalArgumentException.class, () -> ExternalService.create(
                    "   ", null, adminId, null, null, null));
            assertThrows(IllegalArgumentException.class, () -> ExternalService.create(
                    null, null, adminId, null, null, null));
        }

        @Test
        void createRejectsShortName() {
            assertThrows(IllegalArgumentException.class, () -> ExternalService.create(
                    "ab", null, adminId, null, null, null));
        }

        @Test
        void createRejectsOverlongName() {
            String longName = "x".repeat(256);
            assertThrows(IllegalArgumentException.class, () -> ExternalService.create(
                    longName, null, adminId, null, null, null));
        }

        @Test
        void createRejectsNonPositiveRateLimit() {
            assertThrows(IllegalArgumentException.class, () -> ExternalService.create(
                    "Abc", null, adminId, null, 0, null));
            assertThrows(IllegalArgumentException.class, () -> ExternalService.create(
                    "Abc", null, adminId, null, -5, null));
        }

        @Test
        void createAllowsNullRateLimit() {
            ExternalService s = ExternalService.create("Abc", null, adminId, null, null, null);
            assertNull(s.getRateLimitPerMinute());
        }
    }

    @Nested
    class BlockAndUnblock {

        @Test
        void blockFlipsIsActiveAndEmitsEvent() {
            AdminId blocker = AdminId.generate();
            ExternalService blocked = service.block(blocker, "suspicious activity");
            assertFalse(blocked.getIsActive());
            assertTrue(blocked.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof ExternalServiceBlockedEvent));
        }

        @Test
        void blockIsIdempotentOnceInactive() {
            ExternalService blocked = service.block(AdminId.generate(), "reason");
            ExternalService stillBlocked = blocked.block(AdminId.generate(), "again");
            assertSame(blocked, stillBlocked);
        }

        @Test
        void unblockRestoresIsActiveAndEmitsEvent() {
            ExternalService blocked = service.block(AdminId.generate(), "reason");
            ExternalService unblocked = blocked.unblock(AdminId.generate());
            assertTrue(unblocked.getIsActive());
            assertTrue(unblocked.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof ExternalServiceUnblockedEvent));
        }

        @Test
        void unblockIsIdempotentWhenActive() {
            ExternalService stillActive = service.unblock(AdminId.generate());
            assertSame(service, stillActive);
        }
    }

    @Nested
    class UpdateAndUsage {

        @Test
        void updateChangesAllEditableFieldsAndEmitsEvent() {
            ExternalService updated = service.update(
                    "New Name", "new desc", List.of("/api/v2/**"), 120);
            assertEquals("New Name", updated.getName());
            assertEquals("new desc", updated.getDescription());
            assertEquals(120, updated.getRateLimitPerMinute());
            assertTrue(updated.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof ExternalServiceUpdatedEvent));
        }

        @Test
        void updateStillValidatesName() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.update("ab", null, null, 60));
        }

        @Test
        void updateStillValidatesRateLimit() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.update("Valid Name", null, null, 0));
        }

        @Test
        void recordUsageUpdatesLastUsedAt() {
            LocalDateTime before = LocalDateTime.now();
            ExternalService used = service.recordUsage();
            assertNotNull(used.getLastUsedAt());
            assertTrue(!used.getLastUsedAt().isBefore(before));
        }
    }

    @Nested
    class ClientManagement {

        @Test
        void canManageClientsTrueWhenFlagAndActive() {
            assertTrue(service.canManageClients());
        }

        @Test
        void canManageClientsFalseWhenBlocked() {
            ExternalService blocked = service.block(AdminId.generate(), "bad");
            assertFalse(blocked.canManageClients());
        }

        @Test
        void canManageClientsFalseWhenFlagNotSet() {
            ExternalService noManage = ExternalService.create(
                    "Abc", null, adminId, null, null, false);
            assertFalse(noManage.canManageClients());
        }

        @Test
        void validateClientManagementThrowsWhenBlocked() {
            ExternalService blocked = service.block(AdminId.generate(), "x");
            assertThrows(ExternalServiceBlockedException.class, blocked::validateClientManagement);
        }

        @Test
        void validateClientManagementThrowsWhenFlagOff() {
            ExternalService readOnly = ExternalService.create(
                    "Abc", null, adminId, null, null, false);
            assertThrows(ClientManagementNotAllowedException.class,
                    readOnly::validateClientManagement);
        }

        @Test
        void validateClientManagementPassesWhenAllowedAndActive() {
            assertDoesNotThrow(service::validateClientManagement);
        }
    }

    @Nested
    class EndpointAccess {

        @Test
        void allowsAnyEndpointWhenNoRestrictions() {
            ExternalService open = ExternalService.create("Abc", null, adminId, null, null, true);
            assertTrue(open.isEndpointAllowed("/api/v1/anything"));
        }

        @Test
        void allowsExactMatch() {
            ExternalService restricted = ExternalService.create("Abc", null, adminId,
                    List.of("/api/v1/clients"), null, true);
            assertTrue(restricted.isEndpointAllowed("/api/v1/clients"));
            assertFalse(restricted.isEndpointAllowed("/api/v1/admin"));
        }

        @Test
        void allowsWildcardMatch() {
            assertTrue(service.isEndpointAllowed("/api/v1/clients/123"));
            assertFalse(service.isEndpointAllowed("/api/v2/clients"));
        }

        @Test
        void allowsSingleSegmentWildcard() {
            ExternalService restricted = ExternalService.create("Abc", null, adminId,
                    List.of("/api/*/clients"), null, true);
            assertTrue(restricted.isEndpointAllowed("/api/v1/clients"));
            assertFalse(restricted.isEndpointAllowed("/api/v1/admin/clients"));
        }

        @Test
        void validateEndpointAccessThrowsForDisallowed() {
            assertThrows(UnauthorizedEndpointException.class,
                    () -> service.validateEndpointAccess("/api/v2/admin"));
        }

        @Test
        void validateEndpointAccessPassesForAllowed() {
            assertDoesNotThrow(() -> service.validateEndpointAccess("/api/v1/clients/1"));
        }
    }

    @Test
    void allowedEndpointsReturnsUnmodifiableListOrNull() {
        List<String> returned = service.getAllowedEndpoints();
        assertEquals(1, returned.size());
        assertThrows(UnsupportedOperationException.class, () -> returned.add("x"));

        ExternalService open = ExternalService.create("Abc", null, adminId, null, null, null);
        assertNull(open.getAllowedEndpoints());
    }

    @Test
    void fromDatabaseRebuildsStateWithoutEvents() {
        ApiToken token = ApiToken.generate();
        ExternalService restored = ExternalService.fromDatabase(
                ExternalServiceId.generate(),
                "Name", "Desc", token, true,
                List.of("/api/**"), 30, true,
                LocalDateTime.now(), adminId,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), 3L);
        assertEquals(3L, restored.getVersion());
        assertEquals(0, restored.getDomainEvents().size());
    }

    @Test
    void fromDatabaseDefaultsCanManageClientsWhenNull() {
        ExternalService restored = ExternalService.fromDatabase(
                ExternalServiceId.generate(),
                "Name", null, ApiToken.generate(), true,
                null, null, null,
                null, adminId,
                null, null, null);
        assertFalse(restored.getCanManageClients());
    }
}
