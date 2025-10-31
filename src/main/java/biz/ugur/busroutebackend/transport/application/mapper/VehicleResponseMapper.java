package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;


@Component
public class VehicleResponseMapper {


    public Mono<VehiclePositionDTO> toPositionDTO(Vehicle vehicle, BusRoute busRoute) {
        VehiclePositionDTO dto = new VehiclePositionDTO();

        dto.setVehicleId(vehicle.getId().getValue());
        dto.setDeviceId(vehicle.getDeviceId());
        dto.setLicensePlate(vehicle.getLicensePlate());
        dto.setCurrentLatitude(vehicle.getCurrentLatitude());
        dto.setCurrentLongitude(vehicle.getCurrentLongitude());
        dto.setSpeedKmh(vehicle.getSpeedKmh());
        dto.setCourse(vehicle.getCourse());
        dto.setIsInMotion(vehicle.getIsInMotion());
        dto.setIsActive(vehicle.getIsActive());

        if (vehicle.getLastPositionUpdate() != null) {
            dto.setLastPositionUpdate(vehicle.getLastPositionUpdate());
        }

        if (busRoute != null) {
            dto.setRouteNumber(busRoute.getRouteNumber());
            dto.setRouteName(busRoute.getRouteName());
        }

        dto.setCreatedAt(LocalDateTime.now());

        return Mono.just(dto);
    }

    public Mono<VehiclePositionDTO> toPositionDTO(Vehicle vehicle) {
        return toPositionDTO(vehicle, null);
    }


    public VehiclePositionDTO toPositionDTOSync(Vehicle vehicle, String routeNumber, String routeName) {
        VehiclePositionDTO dto = new VehiclePositionDTO();

        dto.setVehicleId(vehicle.getId().getValue());
        dto.setDeviceId(vehicle.getDeviceId());
        dto.setLicensePlate(vehicle.getLicensePlate());
        dto.setRouteNumber(routeNumber);
        dto.setRouteName(routeName);
        dto.setCurrentLatitude(vehicle.getCurrentLatitude());
        dto.setCurrentLongitude(vehicle.getCurrentLongitude());
        dto.setSpeedKmh(vehicle.getSpeedKmh());
        dto.setCourse(vehicle.getCourse());
        dto.setIsInMotion(vehicle.getIsInMotion());
        dto.setIsActive(vehicle.getIsActive());

        if (vehicle.getLastPositionUpdate() != null) {
            dto.setLastPositionUpdate(vehicle.getLastPositionUpdate());
        }

        dto.setCreatedAt(LocalDateTime.now());

        return dto;
    }
}
