package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BusRouteCreateRequest {

    @NotBlank(message = "Route number is required")
    @Pattern(regexp = "^[0-9]{1,3}[A-Z]?$", message = "Route number format should be like '29' or '7A'")
    @JsonProperty("route_number")
    private String routeNumber;

    @NotBlank(message = "Route name is required")
    @Size(min = 5, max = 200, message = "Route name must be between 5 and 200 characters")
    @JsonProperty("route_name")
    private String routeName;

    @Size(max = 200, message = "Turkmen route name must not exceed 200 characters")
    @JsonProperty("route_name_tm")
    private String routeNameTm;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Route color must be a valid hex color")
    @JsonProperty("route_color")
    private String routeColor = "#1976D2";

    @DecimalMin(value = "0.5", message = "Fare price must be at least 0.5 manat")
    @DecimalMax(value = "10.0", message = "Fare price must not exceed 10 manat")
    @JsonProperty("fare_price")
    private BigDecimal farePrice = new BigDecimal("1.00");

    @Min(value = 10, message = "Estimated duration must be at least 10 minutes")
    @Max(value = 300, message = "Estimated duration must not exceed 300 minutes")
    @JsonProperty("estimated_duration_minutes")
    private Integer estimatedDurationMinutes = 60;

    @JsonProperty("is_active")
    private Boolean isActive = true;

    // Остановки для прямого направления
    @JsonProperty("forward_stops")
    private List<String> forwardStopIds;

    // Остановки для обратного направления
    @JsonProperty("backward_stops")
    private List<String> backwardStopIds;

    // Геометрия маршрута (опционально)
    @JsonProperty("forward_geometry")
    private List<List<Double>> forwardGeometry; // [[lat, lon], [lat, lon], ...]

    @JsonProperty("backward_geometry")
    private List<List<Double>> backwardGeometry;
}