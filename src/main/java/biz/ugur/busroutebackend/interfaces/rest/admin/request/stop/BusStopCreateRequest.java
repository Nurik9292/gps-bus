package biz.ugur.busroutebackend.interfaces.rest.admin.request.stop;

import biz.ugur.busroutebackend.transport.application.dto.stop.CreateStop;
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

    @Size(max = 100, message = "English name must not exceed 100 characters")
    @JsonProperty("name_en")
    private String nameEn;

    @Size(max = 100, message = "Turkmen name must not exceed 100 characters")
    @JsonProperty("name_tm")
    private String nameTm;

    @Size(max = 20, message = "Stop code must not exceed 20 characters")
    @JsonProperty("stop_code")
    private String stopCode;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "35.1", message = "Latitude must be within Turkmenistan bounds (35.1-42.8)")
    @DecimalMax(value = "42.8", message = "Latitude must be within Turkmenistan bounds (35.1-42.8)")
    @JsonProperty("latitude")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "52.5", message = "Longitude must be within Turkmenistan bounds (52.5-66.7)")
    @DecimalMax(value = "66.7", message = "Longitude must be within Turkmenistan bounds (52.5-66.7)")
    @JsonProperty("longitude")
    private BigDecimal longitude;

    @JsonProperty("is_major_stop")
    private Boolean isMajorStop = false;

    @JsonProperty("is_active")
    private Boolean isActive = true;

    public CreateStop toInput() {
        return new CreateStop(
                stopName,
                nameEn,
                nameTm,
                stopCode,
                latitude,
                longitude,
                isMajorStop,
                isActive
        );
    }
}