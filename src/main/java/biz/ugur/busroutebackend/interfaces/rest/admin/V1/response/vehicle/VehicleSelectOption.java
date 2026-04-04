package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VehicleSelectOption(
        @JsonProperty("id")
        String id,

        @JsonProperty("device_id")
        String deviceId,

        @JsonProperty("license_plate")
        String licensePlate
) {
}
