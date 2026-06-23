package biz.ugur.busroutebackend.routing.application.dto;

import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RouteSegmentDTO {

    @JsonProperty("type")
    private String type; 

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

    @JsonProperty("route_geometry")
    private RouteGeometryDTO routeGeometry;

    @JsonProperty("total_distance_meters")
    private Integer totalDistanceMeters;

    public RouteSegmentDTO(String type, String description, int durationMinutes,
                           String routeNumber, String instruction) {
        this.type = type;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.routeNumber = routeNumber;
        this.instruction = instruction;
    }

    public RouteSegmentDTO(String type, String description, int durationMinutes,
                           String routeNumber, String instruction,
                           RouteGeometryDTO routeGeometry, Integer totalDistanceMeters) {
        this(type, description, durationMinutes, routeNumber, instruction);
        this.routeGeometry = routeGeometry;
        this.totalDistanceMeters = totalDistanceMeters;
    }

    @Data
    public static class LocationPointDTO {
        @JsonProperty("latitude")
        private Double latitude;

        @JsonProperty("longitude")
        private Double longitude;

        @JsonProperty("name")
        private String name;

        @JsonProperty("stop_id")
        private String stopId;

        public LocationPointDTO(Double latitude, Double longitude, String name) {
            this(latitude, longitude, name, null);
        }

        public LocationPointDTO(Double latitude, Double longitude, String name, String stopId) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
            this.stopId = stopId;
        }
    }

    @Data
    public static class RouteGeometryDTO {
        @JsonProperty("type")
        private String type = "LineString";

        @JsonProperty("coordinates")
        private List<List<Double>> coordinates;

        @JsonProperty("total_distance_meters")
        private Integer totalDistanceMeters;

        public RouteGeometryDTO(List<List<Double>> coordinates, Integer totalDistanceMeters) {
            this.coordinates = coordinates;
            this.totalDistanceMeters = totalDistanceMeters;
        }

        public RouteGeometryDTO() {}

        public static RouteGeometryDTO fromRouteSegment(RouteSegment segment) {
            if (segment == null || segment.getGeoJsonCoordinates() == null) {
                return null;
            }

            List<List<Double>> coordinates = new ArrayList<>();
            for (Double[] coord : segment.getGeoJsonCoordinates()) {
                if (coord.length == 2) {
                    coordinates.add(List.of(coord[0], coord[1]));
                }
            }

            return coordinates.isEmpty() ? null :
                    new RouteGeometryDTO(coordinates, segment.getTotalDistanceMeters());
        }
    }
}