package biz.ugur.busroutebackend.integration.domain.events;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationEventsTest {

    private static final String SERVICE_ID = "svc-123";

    @Test
    void createdEventPopulatesFields() {
        ExternalServiceCreatedEvent e = new ExternalServiceCreatedEvent(
                SERVICE_ID, "Partner", "brt_***", "admin-1");
        assertEquals(SERVICE_ID, e.getExternalServiceId());
        assertEquals("Partner", e.getName());
        assertEquals("brt_***", e.getMaskedToken());
        assertEquals("admin-1", e.getCreatedByAdminId());
        assertNotNull(e.getEventId());
        assertNotNull(e.getOccurredAt());
        assertEquals(1, e.getEventVersion());
        assertEquals("ExternalServiceCreatedEvent", e.getEventType());
        assertEquals(ExternalServiceCreatedEvent.CURRENT_VERSION, e.getEventVersion());
    }

    @Test
    void updatedEventCarriesUpdatedFields() {
        ExternalServiceUpdatedEvent e = new ExternalServiceUpdatedEvent(
                SERVICE_ID, "New Name", "desc", List.of("/api/**"), 60);
        assertEquals("New Name", e.getName());
        assertEquals("desc", e.getDescription());
        assertEquals(List.of("/api/**"), e.getAllowedEndpoints());
        assertEquals(60, e.getRateLimitPerMinute());
        assertEquals("ExternalServiceUpdatedEvent", e.getEventType());
    }

    @Test
    void deletedEventCarriesDeletingAdmin() {
        ExternalServiceDeletedEvent e = new ExternalServiceDeletedEvent(
                SERVICE_ID, "Partner", "admin-2");
        assertEquals("Partner", e.getName());
        assertEquals("admin-2", e.getDeletedByAdminId());
        assertEquals("ExternalServiceDeletedEvent", e.getEventType());
    }

    @Test
    void blockedEventIncludesReason() {
        ExternalServiceBlockedEvent e = new ExternalServiceBlockedEvent(
                SERVICE_ID, "Partner", "admin-3", "suspicious");
        assertEquals("admin-3", e.getBlockedByAdminId());
        assertEquals("suspicious", e.getReason());
    }

    @Test
    void unblockedEventCarriesReviewingAdmin() {
        ExternalServiceUnblockedEvent e = new ExternalServiceUnblockedEvent(
                SERVICE_ID, "Partner", "admin-4");
        assertEquals("admin-4", e.getUnblockedByAdminId());
    }

    @Test
    void eventIdsAreUnique() {
        ExternalServiceCreatedEvent a = new ExternalServiceCreatedEvent(SERVICE_ID, "n", "t", "a");
        ExternalServiceCreatedEvent b = new ExternalServiceCreatedEvent(SERVICE_ID, "n", "t", "a");
        assertNotEquals(a.getEventId(), b.getEventId());
    }
}
