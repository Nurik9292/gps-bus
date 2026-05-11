package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripTest {

    private static final BusRouteId ROUTE_ID = BusRouteId.generate();
    private static final LocalTime START = LocalTime.of(6, 0);
    private static final LocalTime END   = LocalTime.of(23, 0);

    @Test
    void frequencyBased_happyPath_buildsActiveTripWithGeneratedId() {
        Trip trip = Trip.frequencyBased(ROUTE_ID, RouteDirection.FORWARD,
                "ALL_DAYS", 600, START, END);

        assertNotNull(trip.getId());
        assertEquals(ROUTE_ID, trip.getRouteId());
        assertEquals(RouteDirection.FORWARD, trip.getDirection());
        assertEquals("ALL_DAYS", trip.getServiceId());
        assertEquals(600, trip.getHeadwaySeconds());
        assertEquals(START, trip.getStartTime());
        assertEquals(END, trip.getEndTime());
        assertTrue(trip.getIsActive());
        assertEquals(0L, trip.getVersion());
        assertTrue(trip.isFrequencyBased());
    }

    @Test
    void frequencyBased_rejectsEndBeforeOrEqualStart() {
        assertThrows(IllegalArgumentException.class, () ->
                Trip.frequencyBased(ROUTE_ID, RouteDirection.FORWARD,
                        "ALL_DAYS", 600, END, START));
        assertThrows(IllegalArgumentException.class, () ->
                Trip.frequencyBased(ROUTE_ID, RouteDirection.FORWARD,
                        "ALL_DAYS", 600, START, START));
    }

    @Test
    void frequencyBased_rejectsZeroOrNegativeHeadway() {
        assertThrows(IllegalArgumentException.class, () ->
                Trip.frequencyBased(ROUTE_ID, RouteDirection.FORWARD,
                        "ALL_DAYS", 0, START, END));
        assertThrows(IllegalArgumentException.class, () ->
                Trip.frequencyBased(ROUTE_ID, RouteDirection.FORWARD,
                        "ALL_DAYS", -1, START, END));
    }

    @Test
    void frequencyBased_rejectsBlankServiceId() {
        assertThrows(IllegalArgumentException.class, () ->
                Trip.frequencyBased(ROUTE_ID, RouteDirection.FORWARD,
                        "", 600, START, END));
        assertThrows(IllegalArgumentException.class, () ->
                Trip.frequencyBased(ROUTE_ID, RouteDirection.FORWARD,
                        "   ", 600, START, END));
    }

    @Test
    void restore_keepsExternalIdAndVersion() {
        TripId externalId = TripId.of("00000000-0000-0000-0000-000000000abc");
        Trip restored = Trip.restore(externalId, ROUTE_ID, RouteDirection.BACKWARD,
                "ALL_DAYS", 600, START, END, true, null, null, 42L);

        assertEquals(externalId, restored.getId());
        assertEquals(42L, restored.getVersion());
        assertEquals(RouteDirection.BACKWARD, restored.getDirection());
    }

    @Test
    void isFrequencyBased_falseWhenHeadwayNull() {
        Trip scheduleBased = Trip.restore(TripId.generate(), ROUTE_ID, RouteDirection.FORWARD,
                "ALL_DAYS", null, START, END, true, null, null, 0L);
        assertFalse(scheduleBased.isFrequencyBased());
    }
}
