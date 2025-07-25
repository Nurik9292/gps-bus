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
    private Integer direction; // 0 = прямое, 1 = обратное

    @JsonProperty("nearest_point")
    private RoutePointDTO nearestPoint; // Ближайшая точка маршрута к центру поиска

    @JsonProperty("distance_to_center_meters")
    private Double distanceToCenterMeters; // Расстояние от центра поиска до ближайшей точки

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
        this.vehiclesInMotionCount = 0L; // Будет заполнено в use case
        this.estimatedFrequencyMinutes = calculateEstimatedFrequency();
    }

    private Integer calculateEstimatedFrequency() {
        // Простая логика: больше автобусов = чаще ходят
        if (activeVehiclesCount == null || activeVehiclesCount == 0) {
            return 60; // Раз в час если нет автобусов
        } else if (activeVehiclesCount >= 3) {
            return 10; // Каждые 10 минут если много автобусов
        } else if (activeVehiclesCount >= 2) {
            return 20; // Каждые 20 минут
        } else {
            return 30; // Каждые 30 минут
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