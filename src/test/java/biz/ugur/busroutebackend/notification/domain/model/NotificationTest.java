package biz.ugur.busroutebackend.notification.domain.model;

import biz.ugur.busroutebackend.notification.domain.events.NotificationActivatedEvent;
import biz.ugur.busroutebackend.notification.domain.events.NotificationCreatedEvent;
import biz.ugur.busroutebackend.notification.domain.events.NotificationDeactivatedEvent;
import biz.ugur.busroutebackend.notification.domain.events.NotificationUpdatedEvent;
import biz.ugur.busroutebackend.notification.domain.exceptions.NotificationValidationException;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationId;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationTitle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    private NotificationTitle title;
    private Notification fresh;

    @BeforeEach
    void setUp() {
        title = NotificationTitle.of("Route changes");
        fresh = Notification.create(title, 0, "Body text", true);
    }

    @Nested
    class Create {

        @Test
        void createsActiveWithGeneratedIdAndEvent() {
            assertNotNull(fresh.getId());
            assertTrue(fresh.getIsActive());
            assertEquals(0, fresh.getDisplayOrder());
            assertEquals("Body text", fresh.getContent());
            assertEquals(0L, fresh.getVersion());
            assertEquals(1, fresh.getDomainEvents().size());
            assertInstanceOf(NotificationCreatedEvent.class, fresh.getDomainEvents().get(0));
        }

        @Test
        void createDefaultsIsActiveTrueWhenNull() {
            Notification n = Notification.create(title, 5, "x", null);
            assertTrue(n.getIsActive());
        }

        @Test
        void createDefaultsDisplayOrderZeroWhenNull() {
            Notification n = Notification.create(title, null, "x", true);
            assertEquals(0, n.getDisplayOrder());
        }
    }

    @Nested
    class TitleValueObject {

        @Test
        void rejectsBlank() {
            assertThrows(NotificationValidationException.class, () -> NotificationTitle.of("   "));
            assertThrows(NotificationValidationException.class, () -> NotificationTitle.of(null));
        }

        @Test
        void trimsValue() {
            assertEquals("hi", NotificationTitle.of("  hi ").getValue());
        }

        @Test
        void equalityBasedOnValue() {
            assertEquals(NotificationTitle.of("a"), NotificationTitle.of("a"));
        }
    }

    @Nested
    class Update {

        @Test
        void updateNotificationChangesTitleAndEmitsEvent() {
            NotificationTitle renamed = NotificationTitle.of("New title");
            Notification updated = fresh.updateNotification(renamed, 0, "Body text");
            assertEquals(renamed, updated.getTitle());
            NotificationUpdatedEvent ev = (NotificationUpdatedEvent) updated.getDomainEvents().get(0);
            assertTrue(ev.getChanges().containsKey("title"));
            assertFalse(ev.getChanges().containsKey("displayOrder"));
            assertFalse(ev.getChanges().containsKey("content"));
        }

        @Test
        void updateNotificationChangesDisplayOrderAndContent() {
            Notification updated = fresh.updateNotification(title, 9, "  New body  ");
            assertEquals(9, updated.getDisplayOrder());
            assertEquals("New body", updated.getContent());
            NotificationUpdatedEvent ev = (NotificationUpdatedEvent) updated.getDomainEvents().get(0);
            assertTrue(ev.getChanges().containsKey("displayOrder"));
            assertTrue(ev.getChanges().containsKey("content"));
        }

        @Test
        void updateNotificationWithUnchangedValuesEmitsNoEvent() {
            Notification updated = fresh.updateNotification(title, 0, "Body text");
            assertEquals(0, updated.getDomainEvents().size());
        }

        @Test
        void updateNotificationRejectsNullTitle() {
            assertThrows(NotificationValidationException.class,
                    () -> fresh.updateNotification(null, 0, "x"));
        }

        @Test
        void updateNotificationKeepsExistingDisplayOrderWhenNull() {
            Notification reordered = fresh.updateNotification(title, 5, "Body text");
            Notification updated = reordered.updateNotification(title, null, "Body text");
            assertEquals(5, updated.getDisplayOrder());
        }
    }

    @Nested
    class ActivationLifecycle {

        @Test
        void deactivateFlipsFlagAndEmitsEvent() {
            Notification off = fresh.deactivate();
            assertFalse(off.getIsActive());
            assertEquals(1, off.getDomainEvents().size());
            assertInstanceOf(NotificationDeactivatedEvent.class, off.getDomainEvents().get(0));
        }

        @Test
        void deactivateIsIdempotent() {
            Notification off = fresh.deactivate();
            Notification again = off.deactivate();
            assertSame(off, again);
        }

        @Test
        void activateIsIdempotentWhenAlreadyActive() {
            assertSame(fresh, fresh.activate());
        }

        @Test
        void activateAfterDeactivateEmitsActivatedEvent() {
            Notification off = fresh.deactivate();
            Notification on = off.activate();
            assertTrue(on.getIsActive());
            assertEquals(1, on.getDomainEvents().size());
            assertInstanceOf(NotificationActivatedEvent.class, on.getDomainEvents().get(0));
        }
    }

    @Test
    void restoreRebuildsStateWithoutEvents() {
        Notification restored = Notification.restore(
                fresh.getId(), title, false, 7, "Body",
                fresh.getCreatedAt(), fresh.getUpdatedAt(), 4L
        );
        assertEquals(fresh.getId(), restored.getId());
        assertFalse(restored.getIsActive());
        assertEquals(7, restored.getDisplayOrder());
        assertEquals(4L, restored.getVersion());
        assertEquals(0, restored.getDomainEvents().size());
    }

    @Test
    void restoreDefaultsVersionToZeroWhenNull() {
        Notification restored = Notification.restore(
                NotificationId.generate(), title, true, 0, "c",
                null, null, null
        );
        assertEquals(0L, restored.getVersion());
    }
}
