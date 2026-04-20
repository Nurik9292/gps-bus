package biz.ugur.busroutebackend.notification.domain.specification;

import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationTitle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationSpecificationsTest {

    private Notification active;
    private Notification inactive;

    @BeforeEach
    void setUp() {
        active = Notification.create(NotificationTitle.of("Schedule change"), 1, "content", true);
        active.setCreatedAt(LocalDateTime.now().minusDays(1));
        active.setUpdatedAt(LocalDateTime.now());

        inactive = Notification.create(NotificationTitle.of("Old"), 50, "old content", false);
        inactive.setCreatedAt(LocalDateTime.now().minusDays(30));
    }

    @Test
    void isActiveMatchesActiveNotification() {
        assertTrue(NotificationSpecifications.isActive().isSatisfiedBy(active));
        assertFalse(NotificationSpecifications.isActive().isSatisfiedBy(inactive));
    }

    @Test
    void isInactiveMatchesInactive() {
        assertTrue(NotificationSpecifications.isInactive().isSatisfiedBy(inactive));
    }

    @Test
    void titleContainsIsCaseInsensitive() {
        assertTrue(NotificationSpecifications.titleContains("schedule").isSatisfiedBy(active));
        assertTrue(NotificationSpecifications.titleContains("CHANGE").isSatisfiedBy(active));
        assertFalse(NotificationSpecifications.titleContains("unknown").isSatisfiedBy(active));
    }

    @Test
    void displayOrderBetweenInclusive() {
        assertTrue(NotificationSpecifications.displayOrderBetween(0, 5).isSatisfiedBy(active));
        assertFalse(NotificationSpecifications.displayOrderBetween(100, 200).isSatisfiedBy(active));
    }

    @Test
    void createdAfterFiltersByTime() {
        assertTrue(NotificationSpecifications.createdAfter(LocalDateTime.now().minusDays(10))
                .isSatisfiedBy(active));
        assertFalse(NotificationSpecifications.createdAfter(LocalDateTime.now().plusDays(1))
                .isSatisfiedBy(active));
    }

    @Test
    void hasIdMatchesExactId() {
        assertTrue(NotificationSpecifications.hasId(active.getId().getValue()).isSatisfiedBy(active));
        assertFalse(NotificationSpecifications.hasId(UUID.randomUUID().toString()).isSatisfiedBy(active));
    }

    @Test
    void isReadyForDisplayMirrorsIsActive() {
        assertTrue(NotificationSpecifications.isReadyForDisplay().isSatisfiedBy(active));
        assertFalse(NotificationSpecifications.isReadyForDisplay().isSatisfiedBy(inactive));
    }
}
