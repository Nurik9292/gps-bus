package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StopTransferTest {

    private static final BusStopId A = BusStopId.of("stop-a");
    private static final BusStopId B = BusStopId.of("stop-b");

    @Test
    void footpath_buildsForwardLink() {
        StopTransfer t = StopTransfer.footpath(A, B, 144, 200);
        assertEquals(A, t.fromStopId());
        assertEquals(B, t.toStopId());
        assertEquals(144, t.walkingSeconds());
        assertEquals(200, t.distanceMeters());
        assertEquals(TransferType.FOOTPATH, t.transferType());
    }

    @Test
    void rejectsSameFromAndTo() {
        assertThrows(IllegalArgumentException.class,
                () -> StopTransfer.footpath(A, A, 1, 1));
    }

    @Test
    void rejectsZeroOrNegativeWalkingSeconds() {
        assertThrows(IllegalArgumentException.class,
                () -> StopTransfer.footpath(A, B, 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> StopTransfer.footpath(A, B, -1, 100));
    }

    @Test
    void rejectsZeroOrNegativeDistance() {
        assertThrows(IllegalArgumentException.class,
                () -> StopTransfer.footpath(A, B, 60, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StopTransfer.footpath(A, B, 60, -5));
    }

    @Test
    void transferTypeFromValueRoundTrip() {
        for (TransferType type : TransferType.values()) {
            assertEquals(type, TransferType.fromValue(type.getValue()));
        }
        assertThrows(IllegalArgumentException.class, () -> TransferType.fromValue(99));
    }
}
