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

    @JsonProperty("current_direction")
    private Integer currentDirection;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public VehiclePositionDTO() {}

    @JsonIgnore
    public Boolean getLine() {
        if (currentDirection == null) {
            return null;
        }
        return currentDirection == 0;
    }

    @Override
    public String toString() {
        return String.format("VehiclePosition{plate='%s', route='%s', pos=(%.4f,%.4f), speed=%.1f, motion=%s}",
                licensePlate, routeNumber, currentLatitude, currentLongitude, speedKmh, isInMotion);
    }
}