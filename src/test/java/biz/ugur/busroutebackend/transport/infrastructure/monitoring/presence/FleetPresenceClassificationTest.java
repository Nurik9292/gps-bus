package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute.OffRouteRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FleetPresenceClassificationTest {

    private final FleetPresenceAlertProperties props = new FleetPresenceAlertProperties();
    private final ShiftType shift = ShiftType.FIRST;
    private final LocalDateTime now = LocalDateTime.of(2026, 6, 9, 10, 0);

    @Test
    void offRouteTakesPriority() {
        Optional<OffRouteRecord> offRoute = Optional.of(new OffRouteRecord(Instant.now(), 300, 0, 0));
        var status = FleetPresenceAlertMonitor.classify(now.minusMinutes(1), offRoute, now, shift, props);
        assertEquals(Optional.of(AssignedVehicleStatus.OFF_ROUTE), status);
    }

    @Test
    void offRouteTakesPriorityOverStaleGps() {
        Optional<OffRouteRecord> offRoute = Optional.of(new OffRouteRecord(Instant.now(), 300, 0, 0));
        var status = FleetPresenceAlertMonitor.classify(now.minusMinutes(40), offRoute, now, shift, props);
        assertEquals(Optional.of(AssignedVehicleStatus.OFF_ROUTE), status);
    }

    @Test
    void freshWithinThresholdIsOk() {
        var status = FleetPresenceAlertMonitor.classify(now.minusMinutes(5), Optional.empty(), now, shift, props);
        assertTrue(status.isEmpty());
    }

    @Test
    void seenThisShiftButStaleIsWentSilent() {
        var status = FleetPresenceAlertMonitor.classify(now.minusMinutes(40), Optional.empty(), now, shift, props);
        assertEquals(Optional.of(AssignedVehicleStatus.WENT_SILENT), status);
    }

    @Test
    void neverSeenAndPastGraceIsNotStarted() {
        var status = FleetPresenceAlertMonitor.classify(null, Optional.empty(), now, shift, props);
        assertEquals(Optional.of(AssignedVehicleStatus.NOT_STARTED), status);
    }

    @Test
    void neverSeenWithinGraceIsNotReported() {
        LocalDateTime earlyShift = LocalDateTime.of(2026, 6, 9, 5, 10);
        var status = FleetPresenceAlertMonitor.classify(null, Optional.empty(), earlyShift, shift, props);
        assertTrue(status.isEmpty());
    }
}
