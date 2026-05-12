package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CreateCity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CityCreateRequest {

    @NotBlank(message = "City name is required")
    @Size(max = 100, message = "City name must not exceed 100 characters")
    @JsonProperty("name")
    private String name;

    @Size(max = 100, message = "Turkmen name must not exceed 100 characters")
    @JsonProperty("name_tm")
    private String nameTm;

    @JsonProperty("is_active")
    private Boolean isActive = true;

    @JsonProperty("display_order")
    private Integer displayOrder = 0;

    @DecimalMin(value = "-90.0",  message = "latitude must be >= -90")
    @DecimalMax(value = "90.0",   message = "latitude must be <= 90")
    @JsonProperty("latitude")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "longitude must be >= -180")
    @DecimalMax(value = "180.0",  message = "longitude must be <= 180")
    @JsonProperty("longitude")
    private Double longitude;

    public CreateCity toCommand() {
        return new CreateCity(this.name, this.nameTm, this.isActive, this.displayOrder,
                this.latitude, this.longitude);
    }
}
