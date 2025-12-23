package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.ImmediateAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immediate route assignment - dispatcher's manual assignment that takes highest priority.
 *
 * Priority order:
 * 1. IMMEDIATE (this) - dispatcher's manual assignment, applied immediately
 * 2. SHIFT_ASSIGNMENT - scheduled assignments for 07:00/14:00
 * 3. GPS_DETECTED - auto-detected from GPS track
 */
@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class ImmediateRouteAssignment extends AggregateRoot<ImmediateRouteAssignment, ImmediateAssignmentId> {

    private final ImmediateAssignmentId id;
    private final VehicleId vehicleId;
    private final BusRouteId routeId;
    private final String assignedBy;
    private final String reason;
    private final Instant assignedAt;
    private final Instant expiresAt;
    private final Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    /**
     * Create a new immediate assignment.
     */
    public static ImmediateRouteAssignment create(
            VehicleId vehicleId,
            BusRouteId routeId,
            String assignedBy,
            String reason,
            Instant expiresAt) {

        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(routeId, "routeId must not be null");
        Objects.requireNonNull(assignedBy, "assignedBy must not be null");

        return builder()
                .id(ImmediateAssignmentId.generate())
                .vehicleId(vehicleId)
                .routeId(routeId)
                .assignedBy(assignedBy)
                .reason(reason)
                .assignedAt(Instant.now())
                .expiresAt(expiresAt)
                .isActive(true)
                .version(0L)
                .build();
    }

    /**
     * Restore from persistence.
     */
    public static ImmediateRouteAssignment restore(
            ImmediateAssignmentId id,
            VehicleId vehicleId,
            BusRouteId routeId,
            String assignedBy,
            String reason,
            Instant assignedAt,
            Instant expiresAt,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        return builder()
                .id(id)
                .vehicleId(vehicleId)
                .routeId(routeId)
                .assignedBy(assignedBy)
                .reason(reason)
                .assignedAt(assignedAt)
                .expiresAt(expiresAt)
                .isActive(isActive != null ? isActive : true)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    /**
     * Deactivate this assignment.
     */
    public ImmediateRouteAssignment deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }
        return this.toBuilder()
                .isActive(false)
                .build();
    }

    /**
     * Check if this assignment has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this assignment is currently valid (active and not expired).
     */
    public boolean isCurrentlyValid() {
        return Boolean.TRUE.equals(isActive) && !isExpired();
    }

    @Override
    public ImmediateAssignmentId getId() {
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
        return "ImmediateRouteAssignment{" +
                "id=" + id +
                ", vehicleId=" + vehicleId +
                ", routeId=" + routeId +
                ", assignedBy='" + assignedBy + '\'' +
                ", isActive=" + isActive +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
