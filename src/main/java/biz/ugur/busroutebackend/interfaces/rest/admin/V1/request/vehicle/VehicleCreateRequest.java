package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.vehicle;

import biz.ugur.busroutebackend.transport.application.usecase.vehicle.CreateVehicleUseCase;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VehicleCreateRequest(
        @NotBlank(message = "Device ID is required")
        @Size(min = 1, max = 100, message = "Device ID must be between 1 and 100 characters")
        @JsonProperty("device_id")
        String deviceId,

        @NotBlank(message = "License plate is required")
        @Pattern(regexp = "^\\d{4}\\s[A-Z]{3}$", message = "License plate must be in format: 1234 ABC (Turkmen format)")
        @JsonProperty("license_plate")
        String licensePlate,

        @JsonProperty("assigned_route_id")
        String assignedRouteId,

        @JsonProperty("is_active")
        Boolean isActive
) {
    public CreateVehicleUseCase.Command toCommand() {
        return new CreateVehicleUseCase.Command(
                deviceId,
                licensePlate,
                assignedRouteId,
                isActive != null ? isActive : true
        );
    }
}
