package biz.ugur.busroutebackend.interfaces.rest.routing.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TripSearchRequest {

    @Valid
    @NotNull(message = "Origin location is required")
    @JsonProperty("from")
    private LocationDTO from;

    @Valid
    @NotNull(message = "Destination location is required")
    @JsonProperty("to")
    private LocationDTO to;

    @JsonProperty("preferences")
    private TripSearchPreferences preferences;

    @Data
    public static class         LocationDTO {
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "35.0", message = "Latitude must be within Turkmenistan bounds")
        @DecimalMax(value = "43.0", message = "Latitude must be within Turkmenistan bounds")
        @JsonProperty("lat")
        private Double latitude;

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "52.0", message = "Longitude must be within Turkmenistan bounds")
        @DecimalMax(value = "67.0", message = "Longitude must be within Turkmenistan bounds")
        @JsonProperty("lon")
        private Double longitude;

        @JsonProperty("description")
        private String description;
    }

    @Data
    public static class TripSearchPreferences {
        @JsonProperty("max_walking_distance_meters")
        private Integer maxWalkingDistanceMeters = 800;

        @JsonProperty("max_transfers")
        private Integer maxTransfers = 2;

        @JsonProperty("prioritize_speed")
        private Boolean prioritizeSpeed = true;

        @JsonProperty("prioritize_fewer_transfers")
        private Boolean prioritizeFewerTransfers = true;
    }
}