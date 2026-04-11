package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.event.RouteAssignedEvent;
import biz.ugur.busroutebackend.transport.domain.exceptions.AssignmentValidationException;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class RouteAssignmentTest {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private VehicleId vehicleId;
    private BusRouteId routeId;
    private String assignedBy;
    private Instant defaultExpiresAt;

    @BeforeEach
    void setUp() {
        vehicleId = VehicleId.generate();
        routeId = BusRouteId.generate();
        assignedBy = "admin";
        defaultExpiresAt = Instant.now().plusSeconds(28800);
    }

    @Test
    void create_ShouldCreateAssignmentWithCorrectProperties() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;
        String reason = "Test assignment";

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, shiftType, assignedBy, reason, defaultExpiresAt
        );

        assertNotNull(assignment);
        assertNotNull(assignment.getId());
        assertEquals(vehicleId, assignment.getVehicleId());
        assertEquals(routeId, assignment.getRouteId());
        assertEquals(tomorrow, assignment.getEffectiveDate());
        assertEquals(shiftType, assignment.getShiftType());
        assertEquals(assignedBy, assignment.getAssignedBy());
        assertEquals(reason, assignment.getReason());
        assertEquals(defaultExpiresAt, assignment.getExpiresAt());
        assertTrue(assignment.getIsActive());
        assertEquals(0L, assignment.getVersion());

        assertEquals(1, assignment.getDomainEvents().size());
        assertInstanceOf(RouteAssignedEvent.class, assignment.getDomainEvents().get(0));
    }

    @Test
    void create_WithPastDate_ShouldThrowException() {
        LocalDate yesterday = LocalDate.now(ASHGABAT_ZONE).minusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                RouteAssignment.create(vehicleId, routeId, yesterday, shiftType, assignedBy, null, defaultExpiresAt)
        );

        assertTrue(exception.getMessage().contains("Cannot create assignment for past date"));
    }

    @Test
    void create_WithEmptyAssignedBy_ShouldThrowException() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                RouteAssignment.create(vehicleId, routeId, tomorrow, shiftType, "", null, defaultExpiresAt)
        );

        assertTrue(exception.getMessage().contains("assignedBy"));
    }

    @Test
    void create_WithNullAssignedBy_ShouldThrowException() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                RouteAssignment.create(vehicleId, routeId, tomorrow, shiftType, null, null, defaultExpiresAt)
        );

        assertTrue(exception.getMessage().contains("assignedBy"));
    }

    @Test
    void create_WithNullExpiresAt_ShouldThrowException() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        AssignmentValidationException exception = assertThrows(AssignmentValidationException.class, () ->
                RouteAssignment.create(vehicleId, routeId, tomorrow, shiftType, assignedBy, null, null)
        );

        assertTrue(exception.getMessage().contains("expiresAt"));
    }

    @Test
    void isForCurrentShift_WithTodayAndCurrentShift_ShouldReturnTrue() {
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, today, currentShift, assignedBy, null, defaultExpiresAt
        );

        assertTrue(assignment.isForCurrentShift());
        assertFalse(assignment.isScheduled());
    }

    @Test
    void isForCurrentShift_WithFutureDate_ShouldReturnFalse() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, shiftType, assignedBy, null, defaultExpiresAt
        );

        assertFalse(assignment.isForCurrentShift());
        assertTrue(assignment.isScheduled());
    }

    @Test
    void isExpired_WithPastExpiresAt_ShouldReturnTrue() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        Instant pastTime = Instant.now().minusSeconds(3600);

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, pastTime
        );

        assertTrue(assignment.isExpired());
        assertFalse(assignment.isCurrentlyValid());
    }

    @Test
    void isExpired_WithFutureExpiresAt_ShouldReturnFalse() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        Instant futureTime = Instant.now().plusSeconds(3600);

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, futureTime
        );

        assertFalse(assignment.isExpired());
        assertTrue(assignment.isCurrentlyValid());
    }

    @Test
    void deactivate_ShouldReturnNewInstanceWithIsActiveFalse() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, defaultExpiresAt
        );

        RouteAssignment deactivated = assignment.deactivate();

        assertNotSame(assignment, deactivated);
        assertTrue(assignment.getIsActive());
        assertFalse(deactivated.getIsActive());
        assertEquals(assignment.getId(), deactivated.getId());
    }

    @Test
    void deactivate_WhenAlreadyInactive_ShouldReturnSameInstance() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, defaultExpiresAt
        );
        RouteAssignment deactivated = assignment.deactivate();

        RouteAssignment deactivatedAgain = deactivated.deactivate();

        assertSame(deactivated, deactivatedAgain);
    }

    @Test
    void updateExpiration_ShouldReturnNewInstanceWithUpdatedExpiresAt() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        Instant initialExpiresAt = Instant.now().plusSeconds(3600); 
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, initialExpiresAt
        );
        Instant newExpiresAt = Instant.now().plusSeconds(7200); 

        RouteAssignment updated = assignment.updateExpiration(newExpiresAt);

        assertNotSame(assignment, updated);
        assertEquals(initialExpiresAt, assignment.getExpiresAt());
        assertEquals(newExpiresAt, updated.getExpiresAt());
    }

    @Test
    void isCurrentlyValid_WhenActiveAndNotExpired_ShouldReturnTrue() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        Instant futureTime = Instant.now().plusSeconds(3600);

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, futureTime
        );

        assertTrue(assignment.isCurrentlyValid());
    }

    @Test
    void isCurrentlyValid_WhenInactive_ShouldReturnFalse() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, defaultExpiresAt
        );
        RouteAssignment deactivated = assignment.deactivate();

        assertFalse(deactivated.isCurrentlyValid());
    }

    @Test
    void restore_ShouldCreateAssignmentFromPersistedData() {
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        RouteAssignment original = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, "reason", defaultExpiresAt
        );

        RouteAssignment restored = RouteAssignment.restore(
                original.getId(),
                original.getVehicleId(),
                original.getRouteId(),
                original.getEffectiveDate(),
                original.getShiftType(),
                original.getAssignedBy(),
                original.getReason(),
                original.getExpiresAt(),
                original.getIsActive(),
                original.getCreatedAt(),
                original.getUpdatedAt(),
                original.getVersion()
        );

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getVehicleId(), restored.getVehicleId());
        assertEquals(original.getRouteId(), restored.getRouteId());
        assertEquals(original.getEffectiveDate(), restored.getEffectiveDate());
        assertEquals(original.getShiftType(), restored.getShiftType());
        assertEquals(original.getAssignedBy(), restored.getAssignedBy());
        assertEquals(original.getReason(), restored.getReason());
        assertEquals(original.getExpiresAt(), restored.getExpiresAt());
        assertEquals(0, restored.getDomainEvents().size());
    }
}
