package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityUpdate;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record CityUpdateRequest(

        @NotBlank(message = "City name is required")
        @Size(min = 2, max = 100, message = "City name must be between 2 and 100 characters")
        String name,

        @Size(max = 100, message = "Turkmen name must not exceed 100 characters")
        @JsonProperty("name_tm")
        String nameTm,

        @JsonProperty("is_active")
        Boolean isActive,

        @JsonProperty("display_order")
        Integer displayOrder,

        @DecimalMin(value = "-90.0",  message = "latitude must be >= -90")
        @DecimalMax(value = "90.0",   message = "latitude must be <= 90")
        @JsonProperty("latitude")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "longitude must be >= -180")
        @DecimalMax(value = "180.0",  message = "longitude must be <= 180")
        @JsonProperty("longitude")
        Double longitude

) {

    public CityUpdate toCommand(String id) {
        boolean coordsProvided = latitude != null || longitude != null;
        return new CityUpdate(id, name, nameTm, isActive, displayOrder,
                latitude, longitude, coordsProvided);
    }

}
