package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehiclePositionWebSocketMessage {

    @JsonProperty("vehicle_id")
    private final String vehicleId;

    @JsonProperty("license_plate")
    private final String licensePlate;

    @JsonProperty("rout_number")
    private final String routeNumber;

    @JsonProperty("latitude")
    private final Double latitude;

    @JsonProperty("longitude")
    private final Double longitude;

    @JsonProperty("speed_kmh")
    private final Double speedKmh;

    @JsonProperty("is_in_motion")
    private final Boolean isInMotion;

    @JsonProperty("timestamp")
    private final LocalDateTime timestamp;

    @JsonProperty("message_type")
    private final String messageType = "vehicle_position_update";

    @JsonProperty("dir")
    private final Double course;

    @JsonProperty("line")
    private final Boolean line;

    @JsonCreator
    public VehiclePositionWebSocketMessage(
            @JsonProperty("vehicle_id") String vehicleId,
            @JsonProperty("license_plate") String licensePlate,
            @JsonProperty("rout_number") String routeNumber,
            @JsonProperty("latitude") Double latitude,
            @JsonProperty("longitude") Double longitude,
            @JsonProperty("speed_kmh") Double speedKmh,
            @JsonProperty("is_in_motion") Boolean isInMotion,
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("dir") Double course,
            @JsonProperty("line") Boolean line) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.routeNumber = routeNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.isInMotion = isInMotion;
        this.timestamp = timestamp;
        this.course = course;
        this.line = line;
    }
}