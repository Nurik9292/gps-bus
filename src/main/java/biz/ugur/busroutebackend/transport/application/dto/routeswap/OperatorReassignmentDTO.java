package biz.ugur.busroutebackend.transport.application.dto.routeswap;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record OperatorReassignmentDTO(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("license_plate") String licensePlate,
        @JsonProperty("from_route_number") String fromRouteNumber,
        @JsonProperty("to_route_number") String toRouteNumber,
        String actor,
        @JsonProperty("reassigned_at") Instant reassignedAt) {
}
