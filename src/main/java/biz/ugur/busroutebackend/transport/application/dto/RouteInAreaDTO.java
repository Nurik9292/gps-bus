package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RouteInAreaDTO {

    @JsonProperty("route_id")
    private String routeId;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("route_color")
    private String routeColor;

    @JsonProperty("direction")
    private Integer direction;

    @JsonProperty("nearest_point")
    private RoutePointDTO nearestPoint;

    @JsonProperty("distance_to_center_meters")
    private Double distanceToCenterMeters;

    @JsonProperty("active_vehicles_count")
    private Long activeVehiclesCount;

    @JsonProperty("vehicles_in_motion_count")
    private Long vehiclesInMotionCount;

    @JsonProperty("estimated_frequency_minutes")
    private Integer estimatedFrequencyMinutes; // Примерная частота автобусов

    public RouteInAreaDTO(String routeId, String routeNumber, String routeName,
                          String routeColor, Integer direction, RoutePointDTO nearestPoint,
                          Double distanceToCenterMeters, Long activeVehiclesCount) {
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.routeColor = routeColor;
        this.direction = direction;
        this.nearestPoint = nearestPoint;
        this.distanceToCenterMeters = distanceToCenterMeters;
        this.activeVehiclesCount = activeVehiclesCount;
        this.vehiclesInMotionCount = 0L;
        this.estimatedFrequencyMinutes = calculateEstimatedFrequency();
    }

    private Integer calculateEstimatedFrequency() {
        if (activeVehiclesCount == null || activeVehiclesCount == 0) {
            return 60;
        } else if (activeVehiclesCount >= 3) {
            return 10;
        } else if (activeVehiclesCount >= 2) {
            return 20;
        } else {
            return 30;
        }
    }

    public String getDistanceText() {
        if (distanceToCenterMeters < 1000) {
            return Math.round(distanceToCenterMeters) + "м";
        } else {
            return String.format("%.1fкм", distanceToCenterMeters / 1000);
        }
    }
}