package biz.ugur.busroutebackend.transport.infrastructure.mapper;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.VehicleShiftAssignment;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleShiftAssignmentId;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.VehicleShiftAssignmentEntity;
import org.springframework.stereotype.Component;

@Component
public class VehicleShiftAssignmentEntityMapper {

    public VehicleShiftAssignmentEntity toEntity(VehicleShiftAssignment domain) {
        if (domain == null) {
            return null;
        }

        return VehicleShiftAssignmentEntity.builder()
                .id(domain.getId().getValue())
                .vehicleId(domain.getVehicleId().getValue())
                .routeId(domain.getRouteId().getValue())
                .shiftType(domain.getShiftType().name())
                .isActive(domain.getIsActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }

    public VehicleShiftAssignment toDomain(VehicleShiftAssignmentEntity entity) {
        if (entity == null) {
            return null;
        }

        return VehicleShiftAssignment.restore(
                VehicleShiftAssignmentId.of(entity.getId()),
                VehicleId.of(entity.getVehicleId()),
                BusRouteId.of(entity.getRouteId()),
                ShiftType.fromString(entity.getShiftType()),
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
