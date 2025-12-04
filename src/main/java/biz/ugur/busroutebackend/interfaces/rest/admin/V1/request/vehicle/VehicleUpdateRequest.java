package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.vehicle;

import biz.ugur.busroutebackend.transport.application.usecase.vehicle.UpdateVehicleUseCase;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VehicleUpdateRequest(
        @Size(min = 1, max = 100, message = "Device ID must be between 1 and 100 characters")
        @JsonProperty("device_id")
        String deviceId,

        @Pattern(regexp = "^\\d{4}\\s[A-Z]{3}$", message = "License plate must be in format: 1234 ABC (Turkmen format)")
        @JsonProperty("license_plate")
        String licensePlate,

        @JsonProperty("assigned_route_id")
        String assignedRouteId,

        @JsonProperty("is_active")
        Boolean isActive
) {
    public UpdateVehicleUseCase.Command toCommand(String vehicleId) {
        return new UpdateVehicleUseCase.Command(
                vehicleId,
                deviceId,
                licensePlate,
                assignedRouteId,
                isActive
        );
    }
}
