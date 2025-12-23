package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehiclePositionDTO {

    @JsonProperty("vehicle_id")
    private String vehicleId;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("license_plate")
    private String licensePlate;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("current_latitude")
    private Double currentLatitude;

    @JsonProperty("current_longitude")
    private Double currentLongitude;

    @JsonProperty("speed_kmh")
    private Double speedKmh;

    @JsonProperty("is_in_motion")
    private Boolean isInMotion;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("last_position_update")
    private LocalDateTime lastPositionUpdate;

    @JsonProperty("course")
    private Double course;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public VehiclePositionDTO() {}

    public VehiclePositionDTO(String vehicleId,
                              String deviceId,
                              String licensePlate,
                              String routeNumber,
                              String routeName,
                              Double currentLatitude,
                              Double currentLongitude,
                              Double speedKmh,
                              Boolean isInMotion,
                              Boolean isActive,
                              Double course,
                              LocalDateTime lastPositionUpdate,
                              LocalDateTime createdAt) {
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
        this.licensePlate = licensePlate;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.speedKmh = speedKmh;
        this.isInMotion = isInMotion;
        this.isActive = isActive;
        this.course = course;
        this.lastPositionUpdate = lastPositionUpdate;
        this.createdAt = createdAt;
    }

    public VehiclePositionDTO(String vehicleId, String licensePlate,
                              Double latitude, Double longitude,
                              Double speed, Boolean inMotion) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
        this.speedKmh = speed;
        this.isInMotion = inMotion;
        this.isActive = true;
        this.lastPositionUpdate = LocalDateTime.now();
    }

    public boolean hasValidPosition() {
        return currentLatitude != null && currentLongitude != null
                && isValidCoordinate(currentLatitude, currentLongitude);
    }

    @JsonIgnore
    public boolean isMovingFast() {
        return speedKmh != null && speedKmh > 50.0;
    }

    public boolean isRecentUpdate() {
        if (lastPositionUpdate == null) return false;
        return lastPositionUpdate.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    public String getDisplayText() {
        if (licensePlate == null) return "Unknown Vehicle";

        StringBuilder sb = new StringBuilder(licensePlate);
        if (routeNumber != null) {
            sb.append(" (Route ").append(routeNumber).append(")");
        }
        return sb.toString();
    }

    private boolean isValidCoordinate(Double lat, Double lon) {
        return lat >= 35.0 && lat <= 43.0 && lon >= 52.0 && lon <= 67.0;
    }

    @Override
    public String toString() {
        return String.format("VehiclePosition{plate='%s', route='%s', pos=(%.4f,%.4f), speed=%.1f, motion=%s}",
                licensePlate, routeNumber, currentLatitude, currentLongitude, speedKmh, isInMotion);
    }
}