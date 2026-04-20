package biz.ugur.busroutebackend.transport.domain.services;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleAssignmentExpirationServiceTest {

    private VehicleAssignmentExpirationService service;
    private Vehicle vehicle;
    private Vehicle assignedVehicle;

    @BeforeEach
    void setUp() {
        service = new VehicleAssignmentExpirationService();
        vehicle = Vehicle.create("dev-1", "1234 AGH");
        assignedVehicle = vehicle.toBuilder()
                .assignedRouteId(BusRouteId.generate())
                .routeNumber("29A")
                .build();
    }

    @Test
    void hasExpiredAssignmentReturnsFalseForNullVehicle() {
        assertFalse(service.hasExpiredAssignment(null, buildAssignment(-1)));
    }

    @Test
    void hasExpiredAssignmentReturnsFalseForVehicleWithoutRoute() {
        assertFalse(service.hasExpiredAssignment(vehicle, buildAssignment(-1)));
    }

    @Test
    void hasExpiredAssignmentReturnsFalseForNullAssignment() {
        assertFalse(service.hasExpiredAssignment(assignedVehicle, null));
    }

    @Test
    void hasExpiredAssignmentDelegatesToAssignmentIsExpired() {
        assertTrue(service.hasExpiredAssignment(assignedVehicle, buildAssignment(-1)));
        assertFalse(service.hasExpiredAssignment(assignedVehicle, buildAssignment(3600)));
    }

    @Test
    void isExpiredReturnsFalseForNullAssignment() {
        assertFalse(service.isExpired(null));
    }

    @Test
    void isExpiredMirrorsAssignmentState() {
        assertTrue(service.isExpired(buildAssignment(-1)));
        assertFalse(service.isExpired(buildAssignment(3600)));
    }

    @Test
    void shouldClearRouteReturnsFalseForNullOrUnassignedVehicle() {
        assertFalse(service.shouldClearRoute(null, Instant.now().minusSeconds(10)));
        assertFalse(service.shouldClearRoute(vehicle, Instant.now().minusSeconds(10)));
    }

    @Test
    void shouldClearRouteReturnsFalseWhenExpiresAtIsNull() {
        assertFalse(service.shouldClearRoute(assignedVehicle, null));
    }

    @Test
    void shouldClearRouteReturnsTrueWhenExpiresAtInPast() {
        assertTrue(service.shouldClearRoute(assignedVehicle, Instant.now().minusSeconds(60)));
    }

    @Test
    void shouldClearRouteReturnsFalseWhenExpiresAtInFuture() {
        assertFalse(service.shouldClearRoute(assignedVehicle, Instant.now().plusSeconds(3600)));
    }

    @Test
    void clearExpiredAssignmentReturnsSameVehicleWhenNotAssigned() {
        assertSame(vehicle, service.clearExpiredAssignment(vehicle));
    }

    @Test
    void clearExpiredAssignmentReturnsNullForNullInput() {
        assertNull(service.clearExpiredAssignment(null));
    }

    @Test
    void clearExpiredAssignmentClearsRouteForAssignedVehicle() {
        Vehicle cleared = service.clearExpiredAssignment(assignedVehicle);
        assertFalse(cleared.hasAssignedRoute());
    }

    private RouteAssignment buildAssignment(int secondsFromNow) {
        return RouteAssignment.create(
                vehicle.getId(),
                BusRouteId.generate(),
                LocalDate.now(),
                ShiftType.FULL_DAY,
                "admin-1",
                "reason",
                Instant.now().plusSeconds(secondsFromNow)
        );
    }
}
