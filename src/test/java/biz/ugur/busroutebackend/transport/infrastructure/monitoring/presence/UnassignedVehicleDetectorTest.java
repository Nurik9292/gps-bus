package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnassignedVehicleDetectorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 27, 10, 0);
    private static final int SILENT_MIN = 15;

    private Vehicle vehicle(String id, String plate, String routeNumber, LocalDateTime lastUpdate) {
        Vehicle v = mock(Vehicle.class);
        when(v.getId()).thenReturn(VehicleId.of(id));
        when(v.getLicensePlate()).thenReturn(plate);
        when(v.getRouteNumber()).thenReturn(routeNumber);
        when(v.getLastPositionUpdate()).thenReturn(lastUpdate);
        return v;
    }

    @Test
    void includesLiveVehicleWithGpsRouteMarkedLive() {
        var v = vehicle("v1", "AG-1", "12", NOW.minusMinutes(2));

        var result = UnassignedVehicleDetector.detect(List.of(v), Set.of(), NOW, SILENT_MIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).licensePlate()).isEqualTo("AG-1");
        assertThat(result.get(0).gpsRouteNumber()).isEqualTo("12");
        assertThat(result.get(0).live()).isTrue();
    }

    @Test
    void includesParkedVehicleWithoutGpsRouteMarkedNotLive() {
        var v = vehicle("v1", "AG-1", null, NOW.minusMinutes(90));

        var result = UnassignedVehicleDetector.detect(List.of(v), Set.of(), NOW, SILENT_MIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).gpsRouteNumber()).isNull();
        assertThat(result.get(0).live()).isFalse();
    }

    @Test
    void skipsVehicleWithAssignmentToday() {
        var v = vehicle("v1", "AG-1", "12", NOW.minusMinutes(2));

        var result = UnassignedVehicleDetector.detect(List.of(v), Set.of("v1"), NOW, SILENT_MIN);

        assertThat(result).isEmpty();
    }

    @Test
    void zeroAssignmentsListsWholeActiveFleet() {
        var a = vehicle("v1", "AG-1", "12", NOW.minusMinutes(2));
        var b = vehicle("v2", "AG-2", null, NOW.minusMinutes(2));

        var result = UnassignedVehicleDetector.detect(List.of(a, b), Set.of(), NOW, SILENT_MIN);

        assertThat(result).hasSize(2);
    }
}
