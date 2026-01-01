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
        defaultExpiresAt = Instant.now().plusSeconds(28800); // 8 hours from now
    }

    @Test
    void create_ShouldCreateAssignmentWithCorrectProperties() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;
        String reason = "Test assignment";

        // Act
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, shiftType, assignedBy, reason, defaultExpiresAt
        );

        // Assert
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

        // Verify domain event
        assertEquals(1, assignment.getDomainEvents().size());
        assertInstanceOf(RouteAssignedEvent.class, assignment.getDomainEvents().get(0));
    }

    @Test
    void create_WithPastDate_ShouldThrowException() {
        // Arrange
        LocalDate yesterday = LocalDate.now(ASHGABAT_ZONE).minusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                RouteAssignment.create(vehicleId, routeId, yesterday, shiftType, assignedBy, null, defaultExpiresAt)
        );

        assertTrue(exception.getMessage().contains("Cannot create assignment for past date"));
    }

    @Test
    void create_WithEmptyAssignedBy_ShouldThrowException() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                RouteAssignment.create(vehicleId, routeId, tomorrow, shiftType, "", null, defaultExpiresAt)
        );

        assertTrue(exception.getMessage().contains("assignedBy"));
    }

    @Test
    void create_WithNullAssignedBy_ShouldThrowException() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                RouteAssignment.create(vehicleId, routeId, tomorrow, shiftType, null, null, defaultExpiresAt)
        );

        assertTrue(exception.getMessage().contains("assignedBy"));
    }

    @Test
    void create_WithNullExpiresAt_ShouldThrowException() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        // Act & Assert
        AssignmentValidationException exception = assertThrows(AssignmentValidationException.class, () ->
                RouteAssignment.create(vehicleId, routeId, tomorrow, shiftType, assignedBy, null, null)
        );

        assertTrue(exception.getMessage().contains("expiresAt"));
    }

    @Test
    void isForCurrentShift_WithTodayAndCurrentShift_ShouldReturnTrue() {
        // Arrange
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, today, currentShift, assignedBy, null, defaultExpiresAt
        );

        // Act & Assert
        assertTrue(assignment.isForCurrentShift());
        assertFalse(assignment.isScheduled());
    }

    @Test
    void isForCurrentShift_WithFutureDate_ShouldReturnFalse() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        ShiftType shiftType = ShiftType.FIRST;

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, shiftType, assignedBy, null, defaultExpiresAt
        );

        // Act & Assert
        assertFalse(assignment.isForCurrentShift());
        assertTrue(assignment.isScheduled());
    }

    @Test
    void isExpired_WithPastExpiresAt_ShouldReturnTrue() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        Instant pastTime = Instant.now().minusSeconds(3600); // 1 hour ago

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, pastTime
        );

        // Act & Assert
        assertTrue(assignment.isExpired());
        assertFalse(assignment.isCurrentlyValid());
    }

    @Test
    void isExpired_WithFutureExpiresAt_ShouldReturnFalse() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        Instant futureTime = Instant.now().plusSeconds(3600); // 1 hour from now

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, futureTime
        );

        // Act & Assert
        assertFalse(assignment.isExpired());
        assertTrue(assignment.isCurrentlyValid());
    }

    @Test
    void deactivate_ShouldReturnNewInstanceWithIsActiveFalse() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, defaultExpiresAt
        );

        // Act
        RouteAssignment deactivated = assignment.deactivate();

        // Assert
        assertNotSame(assignment, deactivated);
        assertTrue(assignment.getIsActive());
        assertFalse(deactivated.getIsActive());
        assertEquals(assignment.getId(), deactivated.getId());
    }

    @Test
    void deactivate_WhenAlreadyInactive_ShouldReturnSameInstance() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, defaultExpiresAt
        );
        RouteAssignment deactivated = assignment.deactivate();

        // Act
        RouteAssignment deactivatedAgain = deactivated.deactivate();

        // Assert
        assertSame(deactivated, deactivatedAgain);
    }

    @Test
    void updateExpiration_ShouldReturnNewInstanceWithUpdatedExpiresAt() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        Instant initialExpiresAt = Instant.now().plusSeconds(3600); // 1 hour from now
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, initialExpiresAt
        );
        Instant newExpiresAt = Instant.now().plusSeconds(7200); // 2 hours from now

        // Act
        RouteAssignment updated = assignment.updateExpiration(newExpiresAt);

        // Assert
        assertNotSame(assignment, updated);
        assertEquals(initialExpiresAt, assignment.getExpiresAt());
        assertEquals(newExpiresAt, updated.getExpiresAt());
    }

    @Test
    void isCurrentlyValid_WhenActiveAndNotExpired_ShouldReturnTrue() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        Instant futureTime = Instant.now().plusSeconds(3600);

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, futureTime
        );

        // Act & Assert
        assertTrue(assignment.isCurrentlyValid());
    }

    @Test
    void isCurrentlyValid_WhenInactive_ShouldReturnFalse() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, null, defaultExpiresAt
        );
        RouteAssignment deactivated = assignment.deactivate();

        // Act & Assert
        assertFalse(deactivated.isCurrentlyValid());
    }

    @Test
    void restore_ShouldCreateAssignmentFromPersistedData() {
        // Arrange
        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        RouteAssignment original = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, assignedBy, "reason", defaultExpiresAt
        );

        // Act
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

        // Assert
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getVehicleId(), restored.getVehicleId());
        assertEquals(original.getRouteId(), restored.getRouteId());
        assertEquals(original.getEffectiveDate(), restored.getEffectiveDate());
        assertEquals(original.getShiftType(), restored.getShiftType());
        assertEquals(original.getAssignedBy(), restored.getAssignedBy());
        assertEquals(original.getReason(), restored.getReason());
        assertEquals(original.getExpiresAt(), restored.getExpiresAt());
        // Restored instances don't have domain events
        assertEquals(0, restored.getDomainEvents().size());
    }
}
