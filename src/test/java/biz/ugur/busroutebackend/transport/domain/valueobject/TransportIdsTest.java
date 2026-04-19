package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransportIdsTest {

    @Test
    void vehicleIdGenerateProducesUuidLikeValue() {
        VehicleId id = VehicleId.generate();
        assertNotNull(id.getValue());
        assertEquals(36, id.getValue().length());
        assertEquals(id.getValue(), id.toString());
    }

    @Test
    void vehicleIdOfTrimsAndRejectsBlank() {
        VehicleId id = VehicleId.of("  abc  ");
        assertEquals("abc", id.getValue());
        assertThrows(IllegalArgumentException.class, () -> VehicleId.of("  "));
        assertThrows(IllegalArgumentException.class, () -> VehicleId.of(null));
    }

    @Test
    void vehicleIdEqualityBasedOnValue() {
        VehicleId a = VehicleId.of("same");
        VehicleId b = VehicleId.of("same");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void busRouteIdGenerateProducesUuid() {
        BusRouteId id = BusRouteId.generate();
        assertEquals(36, id.getValue().length());
    }

    @Test
    void busRouteIdOfTrimsAndRejectsBlank() {
        assertEquals("x", BusRouteId.of(" x ").getValue());
        assertThrows(IllegalArgumentException.class, () -> BusRouteId.of(null));
        assertThrows(IllegalArgumentException.class, () -> BusRouteId.of(""));
    }

    @Test
    void busStopIdGenerateProducesUuid() {
        BusStopId id = BusStopId.generate();
        assertEquals(36, id.getValue().length());
    }

    @Test
    void busStopIdOfTrimsAndRejectsBlank() {
        assertEquals("a", BusStopId.of("  a").getValue());
        assertThrows(IllegalArgumentException.class, () -> BusStopId.of(" "));
    }

    @Test
    void routeAssignmentIdGenerates() {
        RouteAssignmentId id = RouteAssignmentId.generate();
        assertNotNull(id.getValue());
    }

    @Test
    void routeAlternativeIdGenerates() {
        RouteAlternativeId id = RouteAlternativeId.generate();
        assertNotNull(id.getValue());
    }
}
