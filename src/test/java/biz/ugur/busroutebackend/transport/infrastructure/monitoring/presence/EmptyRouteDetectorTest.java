package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmptyRouteDetectorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 27, 10, 0);
    private static final LocalDateTime GRACE_PASSED = LocalDateTime.of(2026, 6, 27, 5, 20);
    private static final int SILENT_MIN = 15;

    private BusRoute route(String id, String number) {
        BusRoute r = mock(BusRoute.class);
        when(r.getId()).thenReturn(BusRouteId.of(id));
        when(r.getRouteNumber()).thenReturn(number);
        return r;
    }

    private RouteAssignment assignmentFor(String routeId) {
        RouteAssignment a = mock(RouteAssignment.class);
        when(a.getRouteId()).thenReturn(BusRouteId.of(routeId));
        return a;
    }

    private Vehicle vehicle(String id, String routeNumber, String assignedRouteId, LocalDateTime lastUpdate) {
        Vehicle v = mock(Vehicle.class);
        when(v.getId()).thenReturn(VehicleId.of(id));
        when(v.getRouteNumber()).thenReturn(routeNumber);
        when(v.getAssignedRouteId()).thenReturn(assignedRouteId == null ? null : BusRouteId.of(assignedRouteId));
        when(v.getLastPositionUpdate()).thenReturn(lastUpdate);
        return v;
    }

    @Test
    void flagsAssignedButSilentWhenNoLiveBus() {
        var routes = List.of(route("r1", "12"));
        var assignments = List.of(assignmentFor("r1"));

        var result = EmptyRouteDetector.detect(routes, assignments, List.of(), NOW, SILENT_MIN, GRACE_PASSED);

        assertThat(result).containsExactly(new EmptyRoute("12", EmptyRouteReason.ASSIGNED_BUT_SILENT, 1));
    }

    @Test
    void flagsNotAssignedWhenNoAssignmentAndNoLiveBus() {
        var routes = List.of(route("r1", "12"));

        var result = EmptyRouteDetector.detect(routes, List.of(), List.of(), NOW, SILENT_MIN, GRACE_PASSED);

        assertThat(result).containsExactly(new EmptyRoute("12", EmptyRouteReason.NOT_ASSIGNED, 0));
    }

    @Test
    void routeWithLiveBusByRouteNumberNotFlagged() {
        var routes = List.of(route("r1", "12"));
        var live = vehicle("v1", "12", null, NOW.minusMinutes(2));

        var result = EmptyRouteDetector.detect(routes, List.of(), List.of(live), NOW, SILENT_MIN, GRACE_PASSED);

        assertThat(result).isEmpty();
    }

    @Test
    void routeWithLiveBusByAssignedRouteIdNotFlagged() {
        var routes = List.of(route("r1", "12"));
        var live = vehicle("v1", null, "r1", NOW.minusMinutes(2));

        var result = EmptyRouteDetector.detect(routes, List.of(), List.of(live), NOW, SILENT_MIN, GRACE_PASSED);

        assertThat(result).isEmpty();
    }

    @Test
    void staleVehicleDoesNotCountAsPresence() {
        var routes = List.of(route("r1", "12"));
        var stale = vehicle("v1", "12", null, NOW.minusMinutes(40));

        var result = EmptyRouteDetector.detect(routes, List.of(), List.of(stale), NOW, SILENT_MIN, GRACE_PASSED);

        assertThat(result).containsExactly(new EmptyRoute("12", EmptyRouteReason.NOT_ASSIGNED, 0));
    }

    @Test
    void withinGraceReturnsEmpty() {
        var routes = List.of(route("r1", "12"));
        var graceNotPassed = NOW.plusMinutes(30);

        var result = EmptyRouteDetector.detect(routes, List.of(), List.of(), NOW, SILENT_MIN, graceNotPassed);

        assertThat(result).isEmpty();
    }
}
