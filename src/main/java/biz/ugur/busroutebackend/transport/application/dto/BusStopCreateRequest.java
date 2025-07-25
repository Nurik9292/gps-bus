package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BusStopCreateRequest {

    @NotBlank(message = "Stop name is required")
    @Size(min = 2, max = 100, message = "Stop name must be between 2 and 100 characters")
    @JsonProperty("stop_name")
    private String stopName;

    @Size(max = 20, message = "Stop code must not exceed 20 characters")
    @JsonProperty("stop_code")
    private String stopCode;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "35.0", message = "Latitude must be within Turkmenistan bounds")
    @DecimalMax(value = "43.0", message = "Latitude must be within Turkmenistan bounds")
    @JsonProperty("latitude")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "52.0", message = "Longitude must be within Turkmenistan bounds")
    @DecimalMax(value = "67.0", message = "Longitude must be within Turkmenistan bounds")
    @JsonProperty("longitude")
    private BigDecimal longitude;

    @JsonProperty("is_major_stop")
    private Boolean isMajorStop = false;

    @JsonProperty("has_shelter")
    private Boolean hasShelter = false;

    @JsonProperty("is_active")
    private Boolean isActive = true;
}