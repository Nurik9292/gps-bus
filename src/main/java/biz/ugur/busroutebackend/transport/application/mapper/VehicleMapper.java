package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class VehicleMapper {

    public VehiclePositionDTO toDTO(Vehicle vehicle, BusRoute route) {
        VehiclePositionDTO dto = new VehiclePositionDTO();

        dto.setVehicleId(vehicle.getId().getValue());
        dto.setDeviceId(vehicle.getDeviceId());
        dto.setLicensePlate(vehicle.getLicensePlate());
        dto.setRouteNumber(route != null ? route.getRouteNumber() : null);
        dto.setRouteName(route != null ? route.getRouteName() : null);
        dto.setCurrentLatitude(vehicle.getCurrentLatitude());
        dto.setCurrentLongitude(vehicle.getCurrentLongitude());
        dto.setSpeedKmh(vehicle.getSpeedKmh());
        dto.setIsInMotion(vehicle.getIsInMotion());
        dto.setIsActive(vehicle.getIsActive());

        if (vehicle.getLastPositionUpdate() != null) {
            dto.setLastPositionUpdate(vehicle.getLastPositionUpdate());
        }

        dto.setCreatedAt(LocalDateTime.now()); // TODO: заменить на createdAt из сущности

        return dto;
    }
}