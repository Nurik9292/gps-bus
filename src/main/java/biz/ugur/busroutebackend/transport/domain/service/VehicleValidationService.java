package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.constants.TurkmenistanBounds;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class VehicleValidationService {


    public boolean isValidGpsPosition(GpsPositionDTO gpsPosition) {
        if (gpsPosition == null) {
            log.trace("GPS position is null");
            return false;
        }

        if (gpsPosition.getDeviceId() == null || gpsPosition.getDeviceId().trim().isEmpty()) {
            log.trace("GPS position has empty device ID");
            return false;
        }

        if (gpsPosition.getLatitude() == null || gpsPosition.getLongitude() == null) {
            log.trace("GPS position has null coordinates");
            return false;
        }

        double lat = gpsPosition.getLatitude();
        double lon = gpsPosition.getLongitude();

        if (!TurkmenistanBounds.isWithinStandardBounds(lat, lon)) {
            log.warn("Coordinates ({}, {}) outside Turkmenistan bounds for device {}",
                    lat, lon, gpsPosition.getDeviceId());
            return false;
        }

        return true;
    }


    public boolean isWithinServiceArea(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        return TurkmenistanBounds.isWithinStandardBounds(latitude, longitude);
    }


    public boolean isValidLicensePlateFormat(String licensePlate) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            return false;
        }

        String normalized = licensePlate.trim().toUpperCase();
        return normalized.matches("\\d{4}\\s[A-Z]{3}");
    }
}
