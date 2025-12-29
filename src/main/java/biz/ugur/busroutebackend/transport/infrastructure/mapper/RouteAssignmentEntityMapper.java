package biz.ugur.busroutebackend.transport.infrastructure.mapper;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.RouteAssignmentEntity;
import org.springframework.stereotype.Component;

@Component
public class RouteAssignmentEntityMapper {

    public RouteAssignment toDomain(RouteAssignmentEntity entity) {
        if (entity == null) {
            return null;
        }

        return RouteAssignment.restore(
                new RouteAssignmentId(entity.getId()),
                new VehicleId(entity.getVehicleId()),
                new BusRouteId(entity.getRouteId()),
                entity.getEffectiveDate(),
                ShiftType.valueOf(entity.getShiftType()), // Convert string to enum
                entity.getAssignedBy(),
                entity.getReason(),
                entity.getExpiresAt(),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public RouteAssignmentEntity toEntity(RouteAssignment domain) {
        if (domain == null) {
            return null;
        }

        return RouteAssignmentEntity.builder()
                .id(domain.getId().getValue())
                .vehicleId(domain.getVehicleId().getValue())
                .routeId(domain.getRouteId().getValue())
                .effectiveDate(domain.getEffectiveDate())
                .shiftType(domain.getShiftType().name()) // Convert enum to string
                .assignedBy(domain.getAssignedBy())
                .reason(domain.getReason())
                .expiresAt(domain.getExpiresAt())
                .isActive(domain.getIsActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }
}
