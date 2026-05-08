package biz.ugur.busroutebackend.transport.infrastructure.mapper;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.CityId;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
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
                .lastGarageId(domain.getLastGarageId())
                .garageEntryTime(domain.getGarageEntryTime())
                .garageExitTime(domain.getGarageExitTime())
                .isInGarage(domain.getIsInGarage())
                .routeSource(domain.getRouteSource() != null ? domain.getRouteSource().name() : null)
                .routeConfidence(domain.getRouteConfidence())
                .gpsDetectionEnabled(domain.getGpsDetectionEnabled())
                .gpsProvider(domain.getGpsProvider() != null ? domain.getGpsProvider().getCode() : null)
                .cityId(domain.getCityId() != null ? domain.getCityId().getValue() : null)
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
                entity.getLastGarageId(),
                entity.getGarageEntryTime(),
                entity.getGarageExitTime(),
                entity.getIsInGarage(),
                entity.getRouteSource() != null ? RouteSource.valueOf(entity.getRouteSource()) : null,
                entity.getRouteConfidence(),
                entity.getGpsDetectionEnabled(),
                entity.getGpsProvider() != null ? GpsProviderType.fromCodeOrDefault(entity.getGpsProvider()) : null,
                entity.getCityId() != null ? CityId.of(entity.getCityId()) : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
