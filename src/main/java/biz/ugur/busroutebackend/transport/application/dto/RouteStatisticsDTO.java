package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RouteStatisticsDTO {

    @JsonProperty("route_id")
    private String routeId;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("active_vehicles_count")
    private Long activeVehiclesCount;

    @JsonProperty("vehicles_in_motion")
    private Long vehiclesInMotion;

    @JsonProperty("forward_stops_count")
    private Integer forwardStopsCount;

    @JsonProperty("backward_stops_count")
    private Integer backwardStopsCount;

    @JsonProperty("total_distance_forward_km")
    private BigDecimal totalDistanceForwardKm;

    @JsonProperty("total_distance_backward_km")
    private BigDecimal totalDistanceBackwardKm;

    public RouteStatisticsDTO(String routeId, String routeNumber, Long activeVehiclesCount,
                              Long vehiclesInMotion, Integer forwardStopsCount, Integer backwardStopsCount,
                              BigDecimal totalDistanceForwardKm, BigDecimal totalDistanceBackwardKm) {
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.activeVehiclesCount = activeVehiclesCount;
        this.vehiclesInMotion = vehiclesInMotion;
        this.forwardStopsCount = forwardStopsCount;
        this.backwardStopsCount = backwardStopsCount;
        this.totalDistanceForwardKm = totalDistanceForwardKm;
        this.totalDistanceBackwardKm = totalDistanceBackwardKm;
    }
}