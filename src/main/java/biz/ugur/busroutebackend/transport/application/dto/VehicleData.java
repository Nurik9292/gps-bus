package biz.ugur.busroutebackend.transport.application.dto;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;

import java.time.LocalDateTime;

public record VehicleData(
        String id,
        String deviceId,
        String licensePlate,
        Double currentLatitude,
        Double currentLongitude,
        Double speedKmh,
        Boolean isInMotion,
        LocalDateTime lastPositionUpdate,
        String assignedRouteId,
        String routeNumber,
        Boolean isActive,
        Double course,
        String cityId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VehicleData fromDomain(Vehicle vehicle) {
        return new VehicleData(
                vehicle.getId().getValue(),
                vehicle.getDeviceId(),
                vehicle.getLicensePlate(),
                vehicle.getCurrentLatitude(),
                vehicle.getCurrentLongitude(),
                vehicle.getSpeedKmh(),
                vehicle.getIsInMotion(),
                vehicle.getLastPositionUpdate(),
                vehicle.getAssignedRouteId() != null ? vehicle.getAssignedRouteId().getValue() : null,
                vehicle.getRouteNumber(),
                vehicle.getIsActive(),
                vehicle.getCourse(),
                vehicle.getCityId() != null ? vehicle.getCityId().getValue() : null,
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }
}
