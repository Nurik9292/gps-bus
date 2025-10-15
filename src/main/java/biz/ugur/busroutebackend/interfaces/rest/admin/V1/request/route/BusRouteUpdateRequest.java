package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.route;

import biz.ugur.busroutebackend.transport.application.dto.route.UpdateRoute;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class BusRouteUpdateRequest {

    @NotBlank(message = "Route number is required")
    @Pattern(regexp = "^[0-9]{1,3}[A-Z]?$", message = "Route number format should be like '29' or '7A'")
    @JsonProperty("route_number")
    private String routeNumber;

    @NotBlank(message = "Route name is required")
    @Size(min = 2, max = 200, message = "Route name must be between 5 and 200 characters")
    @JsonProperty("route_name")
    private String routeName;

    @Size(max = 200, message = "Turkmen route name must not exceed 200 characters")
    @JsonProperty("name_tm")
    private String nameTm;

    @Size(max = 200, message = "English route name must not exceed 200 characters")
    @JsonProperty("name_en")
    private String nameEn;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Route color must be a valid hex color")
    @JsonProperty("route_color")
    private String routeColor = "#1976D2";

    @Min(value = 2, message = "Estimated duration must be at least 10 minutes")
    @Max(value = 300, message = "Estimated duration must not exceed 300 minutes")
    @JsonProperty("estimated_duration_minutes")
    private Integer estimatedDurationMinutes = 60;

    @JsonProperty("is_active")
    private Boolean isActive = true;

    @JsonProperty("city_id")
    private String cityId;

    @JsonProperty("forward_stops")
    private List<String> forwardStopIds;

    @JsonProperty("backward_stops")
    private List<String> backwardStopIds;

    @JsonProperty("forward_geometry")
    private List<List<Double>> forwardGeometry; // [[lat, lon], [lat, lon], ...]

    @JsonProperty("backward_geometry")
    private List<List<Double>> backwardGeometry;

    public UpdateRoute toCommand(String routeId) {
        return new UpdateRoute(
                routeId,
                routeNumber,
                routeName,
                nameTm,
                nameEn,
                routeColor,
                estimatedDurationMinutes,
                isActive,
                cityId,
                forwardStopIds,
                backwardStopIds,
                forwardGeometry,
                backwardGeometry
        );
    }
}