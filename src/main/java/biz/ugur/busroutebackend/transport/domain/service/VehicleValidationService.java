package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.constants.TurkmenistanBounds;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsValidationResult;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class VehicleValidationService {

    private static final String LICENSE_PLATE_PATTERN = "\\d{4}\\s[A-Z]{3}";

    public GpsValidationResult validateGpsPosition(GpsPositionDTO gpsPosition) {
        if (gpsPosition == null) {
            log.trace("GPS position is null");
            return GpsValidationResult.NULL_POSITION;
        }

        if (gpsPosition.getDeviceId() == null || gpsPosition.getDeviceId().isBlank()) {
            log.debug("GPS position has invalid device ID: {}", gpsPosition.getDeviceId());
            return GpsValidationResult.INVALID_DEVICE_ID;
        }

        if (gpsPosition.getLatitude() == null || gpsPosition.getLongitude() == null) {
            log.trace("GPS position has null coordinates");
            return GpsValidationResult.NULL_COORDINATES;
        }

        double lat = gpsPosition.getLatitude();
        double lon = gpsPosition.getLongitude();

        if (!TurkmenistanBounds.isWithinStandardBounds(lat, lon)) {
            log.warn("Coordinates ({}, {}) outside Turkmenistan bounds for device {}",
                    lat, lon, gpsPosition.getDeviceId());
            return GpsValidationResult.OUT_OF_BOUNDS;
        }

        return GpsValidationResult.VALID;
    }


    public boolean isValidGpsPosition(GpsPositionDTO gpsPosition) {
        return validateGpsPosition(gpsPosition).isValid();
    }

    public boolean isWithinServiceArea(Double latitude, Double longitude) {
        return isValidCoordinates(latitude, longitude);
    }

    public boolean isValidLicensePlateFormat(String licensePlate) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            return false;
        }
        String normalized = licensePlate.trim().toUpperCase();
        return normalized.matches(LICENSE_PLATE_PATTERN);
    }


    public static String validateDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID cannot be null or empty");
        }
        return deviceId.trim();
    }

    public static String validateLicensePlate(String licensePlate) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty");
        }
        String plate = licensePlate.trim().toUpperCase();
        if (!plate.matches(LICENSE_PLATE_PATTERN)) {
            throw new IllegalArgumentException("Invalid Turkmen license plate format. Expected: '1992 AGH'");
        }
        return plate;
    }

    public static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        if (!TurkmenistanBounds.isWithinStandardBounds(latitude, longitude)) {
            throw new IllegalArgumentException(
                    String.format("Coordinates (%.6f, %.6f) are outside Turkmenistan bounds", latitude, longitude)
            );
        }
    }

    public static boolean isValidCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        return TurkmenistanBounds.isWithinStandardBounds(latitude, longitude);
    }
}
