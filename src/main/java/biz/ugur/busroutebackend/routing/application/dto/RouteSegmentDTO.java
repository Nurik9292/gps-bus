package biz.ugur.busroutebackend.routing.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RouteSegmentDTO {

    @JsonProperty("type")
    private String type; // "walking", "bus_ride", "transfer"

    @JsonProperty("description")
    private String description;

    @JsonProperty("duration_minutes")
    private int durationMinutes;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("instruction")
    private String instruction;

    @JsonProperty("from_location")
    private LocationPointDTO fromLocation;

    @JsonProperty("to_location")
    private LocationPointDTO toLocation;

    public RouteSegmentDTO(String type, String description, int durationMinutes,
                           String routeNumber, String instruction) {
        this.type = type;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.routeNumber = routeNumber;
        this.instruction = instruction;
    }

    @Data
    public static class LocationPointDTO {
        @JsonProperty("latitude")
        private Double latitude;

        @JsonProperty("longitude")
        private Double longitude;

        @JsonProperty("name")
        private String name;

        public LocationPointDTO(Double latitude, Double longitude, String name) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
        }
    }
}
