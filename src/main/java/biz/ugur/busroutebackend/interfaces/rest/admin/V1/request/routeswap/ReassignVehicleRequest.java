package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.routeswap;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReassignVehicleRequest(
        @NotBlank @JsonProperty("vehicle_id") String vehicleId,
        @NotBlank @JsonProperty("route_number") String routeNumber,
        @Size(max = 255) String reason) {
}
