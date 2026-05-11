package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StopTimeTest {

    private static final TripId TRIP = TripId.generate();
    private static final BusStopId STOP = BusStopId.generate();

    @Test
    void instantTransit_dwellIsZero() {
        StopTime st = StopTime.instantTransit(TRIP, 1, STOP, 600);
        assertEquals(600, st.arrivalOffsetSec());
        assertEquals(600, st.departureOffsetSec());
        assertEquals(0, st.dwellSec());
    }

    @Test
    void rejectsSequenceBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new StopTime(TRIP, 0, STOP, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StopTime(TRIP, -1, STOP, 0, 0));
    }

    @Test
    void rejectsDepartureBeforeArrival() {
        assertThrows(IllegalArgumentException.class,
                () -> new StopTime(TRIP, 1, STOP, 1200, 600));
    }

    @Test
    void rejectsNegativeArrivalOffset() {
        assertThrows(IllegalArgumentException.class,
                () -> new StopTime(TRIP, 1, STOP, -1, -1));
    }

    @Test
    void allowsPositiveDwell() {
        StopTime withDwell = new StopTime(TRIP, 5, STOP, 1200, 1245);
        assertEquals(45, withDwell.dwellSec());
    }
}
