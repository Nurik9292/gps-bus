package biz.ugur.busroutebackend.notification.domain.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotificationEventsTest {

    private static final String NID = "notif-123";

    @Test
    void createdEventCarriesFieldsAndDefaultMetadata() {
        NotificationCreatedEvent e = new NotificationCreatedEvent(NID, "Title", 5, "body");
        assertEquals(NID, e.getNotificationId());
        assertEquals("Title", e.getTitle());
        assertEquals(5, e.getDisplayOrder());
        assertEquals("body", e.getContent());
        assertNotNull(e.getEventId());
        assertNotNull(e.getOccurredAt());
        assertEquals(1, e.getEventVersion());
        assertEquals("NotificationCreatedEvent", e.getEventType());
    }

    @Test
    void createdEventExplicitConstructorPreservesFields() {
        Instant ts = Instant.parse("2026-05-01T10:00:00Z");
        NotificationCreatedEvent e = new NotificationCreatedEvent(
                "event-1", NID, ts, 7, "T", 0, "c");
        assertEquals("event-1", e.getEventId());
        assertEquals(ts, e.getOccurredAt());
        assertEquals(7, e.getEventVersion());
    }

    @Test
    void activatedEventDefaults() {
        NotificationActivatedEvent e = new NotificationActivatedEvent(NID);
        assertEquals(NID, e.getNotificationId());
        assertEquals("NotificationActivatedEvent", e.getEventType());
        assertNotNull(e.getEventId());
    }

    @Test
    void activatedEventExplicitMetadata() {
        Instant ts = Instant.now();
        NotificationActivatedEvent e = new NotificationActivatedEvent("e-1", NID, ts, 2);
        assertEquals("e-1", e.getEventId());
        assertEquals(ts, e.getOccurredAt());
        assertEquals(2, e.getEventVersion());
    }

    @Test
    void deactivatedEventDefaults() {
        NotificationDeactivatedEvent e = new NotificationDeactivatedEvent(NID);
        assertEquals("NotificationDeactivatedEvent", e.getEventType());
    }

    @Test
    void deactivatedEventExplicitMetadata() {
        Instant ts = Instant.now();
        NotificationDeactivatedEvent e = new NotificationDeactivatedEvent("e", NID, ts, 3);
        assertEquals(3, e.getEventVersion());
    }

    @Test
    void deletedEventDefaults() {
        NotificationDeletedEvent e = new NotificationDeletedEvent(NID);
        assertEquals("NotificationDeletedEvent", e.getEventType());
    }

    @Test
    void deletedEventExplicitMetadata() {
        NotificationDeletedEvent e = new NotificationDeletedEvent("eid", NID, Instant.now(), 5);
        assertEquals("eid", e.getEventId());
        assertEquals(5, e.getEventVersion());
    }

    @Test
    void updatedEventChangesMapAccessHelpers() {
        NotificationUpdatedEvent e = new NotificationUpdatedEvent(NID, Map.of("title", "New"));
        assertTrue(e.hasChange("title"));
        assertFalse(e.hasChange("content"));
        assertEquals("New", e.getChange("title", String.class));
        assertEquals("NotificationUpdatedEvent", e.getEventType());
    }

    @Test
    void eventIdsAreUnique() {
        assertNotEquals(new NotificationCreatedEvent(NID, "t", 0, "c").getEventId(),
                new NotificationCreatedEvent(NID, "t", 0, "c").getEventId());
    }
}
