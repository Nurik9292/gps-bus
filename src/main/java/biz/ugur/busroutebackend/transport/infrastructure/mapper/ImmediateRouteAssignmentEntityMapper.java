package biz.ugur.busroutebackend.transport.infrastructure.mapper;

import biz.ugur.busroutebackend.transport.domain.model.ImmediateRouteAssignment;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.ImmediateAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.ImmediateRouteAssignmentEntity;
import org.springframework.stereotype.Component;

@Component
public class ImmediateRouteAssignmentEntityMapper {

    public ImmediateRouteAssignment toDomain(ImmediateRouteAssignmentEntity entity) {
        if (entity == null) {
            return null;
        }

        return ImmediateRouteAssignment.restore(
                new ImmediateAssignmentId(entity.getId()),
                new VehicleId(entity.getVehicleId()),
                new BusRouteId(entity.getRouteId()),
                entity.getAssignedBy(),
                entity.getReason(),
                entity.getAssignedAt(),
                entity.getExpiresAt(),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public ImmediateRouteAssignmentEntity toEntity(ImmediateRouteAssignment domain) {
        if (domain == null) {
            return null;
        }

        return ImmediateRouteAssignmentEntity.builder()
                .id(domain.getId().getValue())
                .vehicleId(domain.getVehicleId().getValue())
                .routeId(domain.getRouteId().getValue())
                .assignedBy(domain.getAssignedBy())
                .reason(domain.getReason())
                .assignedAt(domain.getAssignedAt())
                .expiresAt(domain.getExpiresAt())
                .isActive(domain.getIsActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }
}
