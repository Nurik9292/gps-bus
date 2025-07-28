package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

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
    private final Instant timestamp;

    @JsonProperty("message_type")
    private final String messageType = "vehicle_position_update";

    public VehiclePositionWebSocketMessage(
            String vehicleId,
            String licensePlate,
            String routeNumber,
            Double latitude,
            Double longitude,
            Double speedKmh,
            Boolean isInMotion,
            Instant timestamp) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.routeNumber = routeNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.isInMotion = isInMotion;
        this.timestamp = timestamp;
    }
}