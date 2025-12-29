package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;


@Getter
public class RouteAssignedEvent implements DomainEvent {

    private final String assignmentId;
    private final String vehicleId;
    private final String routeId;
    private final LocalDate effectiveDate;
    private final ShiftType shiftType;
    private final String assignedBy;
    private final Instant eventOccurredAt;

    public RouteAssignedEvent(
            String assignmentId,
            String vehicleId,
            String routeId,
            LocalDate effectiveDate,
            ShiftType shiftType,
            String assignedBy) {
        this.assignmentId = assignmentId;
        this.vehicleId = vehicleId;
        this.routeId = routeId;
        this.effectiveDate = effectiveDate;
        this.shiftType = shiftType;
        this.assignedBy = assignedBy;
        this.eventOccurredAt = Instant.now();
    }

    public boolean isForCurrentShift() {
        LocalDate today = LocalDate.now(ShiftType.ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();
        return effectiveDate.equals(today) && shiftType == currentShift;
    }

    public boolean isScheduled() {
        return !isForCurrentShift();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        String timing = isForCurrentShift() ? "IMMEDIATE" : "SCHEDULED";
        return String.format(
                "RouteAssigned[assignment=%s, vehicle=%s, route=%s, date=%s, shift=%s, timing=%s, by=%s]",
                assignmentId, vehicleId, routeId, effectiveDate, shiftType, timing, assignedBy
        );
    }
}
