package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VehiclePositionWebSocketMessage {

    @JsonProperty("vehicle_id")
    private final String vehicleId;

    @JsonProperty("license_plate")
    private final String licensePlate;

    @JsonProperty("route_number")
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

    @JsonProperty("next_stops")
    private final List<NextStopEta> nextStops;

    @JsonProperty("predicted")
    private final Boolean predicted;

    @JsonProperty("fraction")
    private final Double fraction;

    @JsonCreator
    public VehiclePositionWebSocketMessage(
            @JsonProperty("vehicle_id") String vehicleId,
            @JsonProperty("license_plate") String licensePlate,
            @JsonProperty("route_number") String routeNumber,
            @JsonProperty("latitude") Double latitude,
            @JsonProperty("longitude") Double longitude,
            @JsonProperty("speed_kmh") Double speedKmh,
            @JsonProperty("is_in_motion") Boolean isInMotion,
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("dir") Double course,
            @JsonProperty("line") Boolean line) {
        this(vehicleId, licensePlate, routeNumber, latitude, longitude,
                speedKmh, isInMotion, timestamp, course, line, null, null, null);
    }

    public VehiclePositionWebSocketMessage(
            String vehicleId,
            String licensePlate,
            String routeNumber,
            Double latitude,
            Double longitude,
            Double speedKmh,
            Boolean isInMotion,
            LocalDateTime timestamp,
            Double course,
            Boolean line,
            List<NextStopEta> nextStops) {
        this(vehicleId, licensePlate, routeNumber, latitude, longitude,
                speedKmh, isInMotion, timestamp, course, line, nextStops, null, null);
    }

    public VehiclePositionWebSocketMessage(
            String vehicleId,
            String licensePlate,
            String routeNumber,
            Double latitude,
            Double longitude,
            Double speedKmh,
            Boolean isInMotion,
            LocalDateTime timestamp,
            Double course,
            Boolean line,
            List<NextStopEta> nextStops,
            Boolean predicted,
            Double fraction) {
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
        this.nextStops = nextStops;
        this.predicted = predicted;
        this.fraction = fraction;
    }


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NextStopEta {
        @JsonProperty("stop_id")
        private final String stopId;

        @JsonProperty("stop_name")
        private final String stopName;

        @JsonProperty("eta_minutes")
        private final Integer etaMinutes;

        @JsonProperty("distance_meters")
        private final Integer distanceMeters;

        @JsonCreator
        public NextStopEta(
                @JsonProperty("stop_id") String stopId,
                @JsonProperty("stop_name") String stopName,
                @JsonProperty("eta_minutes") Integer etaMinutes,
                @JsonProperty("distance_meters") Integer distanceMeters) {
            this.stopId = stopId;
            this.stopName = stopName;
            this.etaMinutes = etaMinutes;
            this.distanceMeters = distanceMeters;
        }
    }
}