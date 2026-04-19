package biz.ugur.busroutebackend.advertising.domain.valueobjects;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PlacementWindowTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 5, 1, 12, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 5, 1, 14, 0);

    @Test
    void unscheduledAllowsAnyMomentAsActive() {
        PlacementWindow w = PlacementWindow.unscheduled();
        assertNull(w.getStartsAt());
        assertNull(w.getEndsAt());
        assertTrue(w.isActiveAt(T0));
        assertTrue(w.isActiveAt(T2));
        assertFalse(w.isExpiredAt(T0));
    }

    @Test
    void boundedWindowRejectsMomentBeforeStart() {
        PlacementWindow w = PlacementWindow.of(T1, T2);
        assertFalse(w.isActiveAt(T0));
        assertTrue(w.isActiveAt(T1));
        assertFalse(w.isActiveAt(T2));
    }

    @Test
    void boundedWindowReportsExpiredAtOrAfterEnd() {
        PlacementWindow w = PlacementWindow.of(T0, T1);
        assertFalse(w.isExpiredAt(T0));
        assertTrue(w.isExpiredAt(T1));
        assertTrue(w.isExpiredAt(T2));
    }

    @Test
    void openStartAcceptsAnyMomentBeforeEnd() {
        PlacementWindow w = PlacementWindow.of(null, T1);
        assertTrue(w.isActiveAt(T0));
        assertFalse(w.isActiveAt(T1));
    }

    @Test
    void openEndAcceptsAnyMomentAfterStart() {
        PlacementWindow w = PlacementWindow.of(T0, null);
        assertFalse(w.isActiveAt(T0.minusMinutes(1)));
        assertTrue(w.isActiveAt(T0));
        assertTrue(w.isActiveAt(T2));
        assertFalse(w.isExpiredAt(T2));
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThrows(AdvertisingValidationException.class,
                () -> PlacementWindow.of(T2, T0));
    }

    @Test
    void endEqualToStartIsRejected() {
        assertThrows(AdvertisingValidationException.class,
                () -> PlacementWindow.of(T0, T0));
    }

    @Test
    void equalityBasedOnBothEndpoints() {
        assertEquals(PlacementWindow.of(T0, T1), PlacementWindow.of(T0, T1));
        assertNotEquals(PlacementWindow.of(T0, T1), PlacementWindow.of(T0, T2));
    }
}
