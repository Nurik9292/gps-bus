package biz.ugur.busroutebackend.prediction.broadcast;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record V31Frame(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("license_plate") String licensePlate,
        @JsonProperty("route_number") String routeNumber,
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude,
        @JsonProperty("speed_kmh") Double speedKmh,
        @JsonProperty("is_in_motion") Boolean isInMotion,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("dir") Double course,
        @JsonProperty("direction") Integer direction,
        @JsonProperty("predicted") Boolean predicted,
        @JsonProperty("fraction") Double fraction,
        @JsonProperty("confidence") Double confidence,
        @JsonProperty("t_gps") String tGps,
        @JsonProperty("conf") Double conf,
        @JsonProperty("mode") String mode,
        @JsonProperty("src") String src,
        @JsonProperty("trip_seq") Long tripSeq,
        @JsonProperty("server_time") String serverTime,
        @JsonProperty("eta_reliable") Boolean etaReliable,
        @JsonProperty("off_route") Boolean offRoute) {
}
