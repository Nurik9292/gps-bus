package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.vehicle;

import biz.ugur.busroutebackend.transport.application.dto.VehicleData;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record VehicleResponse(
        @JsonProperty("id")
        String id,

        @JsonProperty("device_id")
        String deviceId,

        @JsonProperty("license_plate")
        String licensePlate,

        @JsonProperty("current_latitude")
        Double currentLatitude,

        @JsonProperty("current_longitude")
        Double currentLongitude,

        @JsonProperty("speed_kmh")
        Double speedKmh,

        @JsonProperty("is_in_motion")
        Boolean isInMotion,

        @JsonProperty("last_position_update")
        LocalDateTime lastPositionUpdate,

        @JsonProperty("assigned_route_id")
        String assignedRouteId,

        @JsonProperty("route_number")
        String routeNumber,

        @JsonProperty("is_active")
        Boolean isActive,

        @JsonProperty("course")
        Double course,

        @JsonProperty("city_id")
        String cityId,

        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
    public static VehicleResponse fromData(VehicleData data) {
        return new VehicleResponse(
                data.id(),
                data.deviceId(),
                data.licensePlate(),
                data.currentLatitude(),
                data.currentLongitude(),
                data.speedKmh(),
                data.isInMotion(),
                data.lastPositionUpdate(),
                data.assignedRouteId(),
                data.routeNumber(),
                data.isActive(),
                data.course(),
                data.cityId(),
                data.createdAt(),
                data.updatedAt()
        );
    }
}
