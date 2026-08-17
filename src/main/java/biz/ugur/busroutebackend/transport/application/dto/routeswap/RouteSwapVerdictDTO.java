package biz.ugur.busroutebackend.transport.application.dto.routeswap;

import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository.VerdictRecord;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

public record RouteSwapVerdictDTO(
        Long id,
        @JsonProperty("license_plate") String licensePlate,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("assigned_route_number") String assignedRouteNumber,
        String verdict,
        String detail,
        @JsonProperty("factual_route_numbers") String factualRouteNumbers,
        @JsonProperty("operational_date") LocalDate operationalDate,
        String shift,
        @JsonProperty("created_at") Instant createdAt) {

    public static RouteSwapVerdictDTO fromRecord(VerdictRecord record) {
        return new RouteSwapVerdictDTO(record.id(), record.licensePlate(), record.vehicleId(),
                record.assignedRouteNumber(), record.verdict(), record.detail(),
                record.factualRouteNumbers(), record.operationalDate(), record.shift(),
                record.createdAt());
    }
}
