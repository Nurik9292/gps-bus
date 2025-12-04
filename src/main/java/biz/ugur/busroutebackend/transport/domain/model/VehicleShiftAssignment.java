package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleShiftAssignmentId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class VehicleShiftAssignment extends AggregateRoot<VehicleShiftAssignment, VehicleShiftAssignmentId> {

    private final VehicleShiftAssignmentId id;
    private final VehicleId vehicleId;
    private final BusRouteId routeId;
    private final ShiftType shiftType;
    private final Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static VehicleShiftAssignment create(VehicleId vehicleId, BusRouteId routeId, ShiftType shiftType) {
        if (vehicleId == null) {
            throw new IllegalArgumentException("Vehicle ID cannot be null");
        }
        if (routeId == null) {
            throw new IllegalArgumentException("Route ID cannot be null");
        }
        if (shiftType == null) {
            throw new IllegalArgumentException("Shift type cannot be null");
        }

        return builder()
                .id(VehicleShiftAssignmentId.generate())
                .vehicleId(vehicleId)
                .routeId(routeId)
                .shiftType(shiftType)
                .isActive(true)
                .version(0L)
                .build();
    }

    public static VehicleShiftAssignment restore(
            VehicleShiftAssignmentId id,
            VehicleId vehicleId,
            BusRouteId routeId,
            ShiftType shiftType,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        return builder()
                .id(id)
                .vehicleId(vehicleId)
                .routeId(routeId)
                .shiftType(shiftType)
                .isActive(isActive != null ? isActive : true)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    public VehicleShiftAssignment updateRoute(BusRouteId newRouteId) {
        if (newRouteId == null) {
            throw new IllegalArgumentException("Route ID cannot be null");
        }
        return this.toBuilder()
                .routeId(newRouteId)
                .build();
    }

    public VehicleShiftAssignment deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }
        return this.toBuilder()
                .isActive(false)
                .build();
    }

    public VehicleShiftAssignment activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }
        return this.toBuilder()
                .isActive(true)
                .build();
    }

    public boolean isCurrentlyActive() {
        return Boolean.TRUE.equals(isActive) && shiftType.isActive();
    }

    @Override
    public VehicleShiftAssignmentId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "VehicleShiftAssignment{" +
                "id=" + id +
                ", vehicleId=" + vehicleId +
                ", routeId=" + routeId +
                ", shiftType=" + shiftType +
                ", isActive=" + isActive +
                '}';
    }
}
