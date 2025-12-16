package biz.ugur.busroutebackend.transport.infrastructure.mapper;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.VehicleEntity;
import org.springframework.stereotype.Component;


@Component
public class VehicleEntityMapper {

    public VehicleEntity toEntity(Vehicle domain) {
        if (domain == null) {
            return null;
        }

        return VehicleEntity.builder()
                .id(domain.getId().getValue())
                .deviceId(domain.getDeviceId())
                .licensePlate(domain.getLicensePlate())
                .currentLatitude(domain.getCurrentLatitude())
                .currentLongitude(domain.getCurrentLongitude())
                .speedKmh(domain.getSpeedKmh())
                .isInMotion(domain.getIsInMotion())
                .lastPositionUpdate(domain.getLastPositionUpdate())
                .assignedRouteId(domain.getAssignedRouteId() != null ? domain.getAssignedRouteId().getValue() : null)
                .routeNumber(domain.getRouteNumber())
                .isActive(domain.getIsActive())
                .course(domain.getCourse())
                .currentDirection(domain.getCurrentDirection())
                .lastStopSequence(domain.getLastStopSequence())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }

    public Vehicle toDomain(VehicleEntity entity) {
        if (entity == null) {
            return null;
        }

        return Vehicle.restore(
                VehicleId.of(entity.getId()),
                entity.getDeviceId(),
                entity.getLicensePlate(),
                entity.getCurrentLatitude(),
                entity.getCurrentLongitude(),
                entity.getSpeedKmh(),
                entity.getIsInMotion(),
                entity.getLastPositionUpdate(),
                entity.getAssignedRouteId() != null ? BusRouteId.of(entity.getAssignedRouteId()) : null,
                entity.getRouteNumber(),
                entity.getIsActive(),
                entity.getCourse(),
                entity.getCurrentDirection(),
                entity.getLastStopSequence(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
