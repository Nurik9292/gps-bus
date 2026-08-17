package biz.ugur.busroutebackend.transport.application.dto.routeswap;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

public record VehicleReassignmentDTO(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("license_plate") String licensePlate,
        @JsonProperty("previous_route_id") String previousRouteId,
        @JsonProperty("previous_route_number") String previousRouteNumber,
        @JsonProperty("new_route_id") String newRouteId,
        @JsonProperty("new_route_number") String newRouteNumber,
        @JsonProperty("operational_date") LocalDate operationalDate,
        String shift,
        @JsonProperty("expires_at") Instant expiresAt) {
}
