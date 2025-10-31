package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;


@Slf4j
public class VehicleEnrichmentService {


    public VehiclePositionDTO enrichPosition(GpsPositionDTO gpsPosition, Map<String, String> routeMapping) {
        String routeNumber = routeMapping.get(gpsPosition.getDeviceId());

        String vehicleId = generateVehicleId(gpsPosition);

        VehiclePositionDTO enrichedDto = new VehiclePositionDTO();
        enrichedDto.setVehicleId(vehicleId);
        enrichedDto.setDeviceId(gpsPosition.getDeviceId());
        enrichedDto.setLicensePlate(gpsPosition.getVehicleName());
        enrichedDto.setRouteNumber(routeNumber);
        enrichedDto.setCurrentLatitude(gpsPosition.getLatitude());
        enrichedDto.setCurrentLongitude(gpsPosition.getLongitude());
        enrichedDto.setSpeedKmh(gpsPosition.getSpeed());
        enrichedDto.setIsInMotion(gpsPosition.getMotion());
        enrichedDto.setIsActive(true);
        enrichedDto.setLastPositionUpdate(LocalDateTime.now());

        log.trace("Enriched position for device {}: route {}",
                gpsPosition.getDeviceId(), routeNumber);

        return enrichedDto;
    }


    private String generateVehicleId(GpsPositionDTO gpsPosition) {
        return "vehicle-" + gpsPosition.getDeviceId();
    }


    public boolean hasValidRoute(VehiclePositionDTO enrichedPosition) {
        return enrichedPosition.getRouteNumber() != null;
    }
}
