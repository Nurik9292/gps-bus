package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.event.RouteAssignedEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteAssignment extends AggregateRoot<RouteAssignment, RouteAssignmentId> {

    private final RouteAssignmentId id;
    private final VehicleId vehicleId;
    private final BusRouteId routeId;

    private final LocalDate effectiveDate;

    private final ShiftType shiftType;

    private final String assignedBy;

    private final String reason;

    private final Instant expiresAt;

    private final Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    public static RouteAssignment create(
            VehicleId vehicleId,
            BusRouteId routeId,
            LocalDate effectiveDate,
            ShiftType shiftType,
            String assignedBy,
            String reason,
            Instant expiresAt) {

        validateNotPastDate(effectiveDate, shiftType);
        validateAssignedByNotEmpty(assignedBy);

        RouteAssignment assignment = RouteAssignment.builder()
                .id(RouteAssignmentId.generate())
                .vehicleId(vehicleId)
                .routeId(routeId)
                .effectiveDate(effectiveDate)
                .shiftType(shiftType)
                .assignedBy(assignedBy)
                .reason(reason)
                .expiresAt(expiresAt)
                .isActive(true)
                .version(0L)
                .build();

        // Emit domain event
        assignment.registerEvent(new RouteAssignedEvent(
                assignment.id.getValue(),
                vehicleId.getValue(),
                routeId.getValue(),
                effectiveDate,
                shiftType,
                assignedBy
        ));

        return assignment;
    }

    public boolean isForCurrentShift() {
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();

        return this.effectiveDate.equals(today) && this.shiftType == currentShift;
    }

    public boolean isScheduled() {
        return !isForCurrentShift();
    }

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isCurrentlyValid() {
        return Boolean.TRUE.equals(isActive) && !isExpired();
    }

    public RouteAssignment deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }

        return this.toBuilder()
                .isActive(false)
                .build();
    }

    public RouteAssignment updateExpiration(Instant newExpiresAt) {
        return this.toBuilder()
                .expiresAt(newExpiresAt)
                .build();
    }

    public static RouteAssignment restore(
            RouteAssignmentId id,
            VehicleId vehicleId,
            BusRouteId routeId,
            LocalDate effectiveDate,
            ShiftType shiftType,
            String assignedBy,
            String reason,
            Instant expiresAt,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        return RouteAssignment.builder()
                .id(id)
                .vehicleId(vehicleId)
                .routeId(routeId)
                .effectiveDate(effectiveDate)
                .shiftType(shiftType)
                .assignedBy(assignedBy)
                .reason(reason)
                .expiresAt(expiresAt)
                .isActive(isActive)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();
    }

    @Override
    public RouteAssignmentId getId() {
        return id;
    }



    private static void validateNotPastDate(LocalDate effectiveDate, ShiftType shiftType) {
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);

        if (effectiveDate.isBefore(today)) {
            throw new IllegalArgumentException(
                    String.format("Cannot create assignment for past date: %s (today is %s)",
                            effectiveDate, today)
            );
        }

        if (effectiveDate.equals(today)) {
            ShiftType currentShift = ShiftType.getCurrentShift();

            if (currentShift == ShiftType.SECOND && shiftType == ShiftType.FIRST) {
                throw new IllegalArgumentException(
                        "Cannot create FIRST shift assignment after 14:00. " +
                        "SECOND shift is already active. " +
                        "To assign immediately, use SECOND shift or wait until tomorrow."
                );
            }
        }
    }

    private static void validateAssignedByNotEmpty(String assignedBy) {
        if (assignedBy == null || assignedBy.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "assignedBy cannot be null or empty. All assignments must have an accountable admin."
            );
        }
    }



    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
}
