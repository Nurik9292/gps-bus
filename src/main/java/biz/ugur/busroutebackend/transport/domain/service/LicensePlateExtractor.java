package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;


@Slf4j
public class LicensePlateExtractor {

    private final VehicleValidationService validationService;

    public LicensePlateExtractor(VehicleValidationService validationService) {
        this.validationService = validationService;
    }


    public Optional<String> extractFromGpsData(GpsPositionDTO gpsPosition) {
        if (gpsPosition == null) {
            log.trace("GPS position is null");
            return Optional.empty();
        }

        String vehicleName = gpsPosition.getVehicleName();
        if (vehicleName == null || vehicleName.trim().isEmpty()) {
            log.trace("Vehicle name is empty for device: {}", gpsPosition.getDeviceId());
            return Optional.empty();
        }

        return extractFromString(vehicleName);
    }

    public Optional<String> extractFromString(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalized = input.trim().toUpperCase().replace("-", "");

        if (validationService.isValidLicensePlateFormat(normalized)) {
            log.debug("Extracted license plate: {}", normalized);
            return Optional.of(normalized);
        }

        log.trace("Could not extract valid license plate from: {}", input);
        return Optional.empty();
    }

    public String extractOrDefault(GpsPositionDTO gpsPosition, String defaultValue) {
        return extractFromGpsData(gpsPosition).orElse(defaultValue);
    }
}
