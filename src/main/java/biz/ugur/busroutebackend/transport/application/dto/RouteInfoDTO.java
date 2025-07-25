package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RouteInfoDTO {

    @JsonProperty("route_id")
    private String routeId;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("route_color")
    private String routeColor;

    @JsonProperty("total_distance_forward_km")
    private BigDecimal totalDistanceForwardKm;

    @JsonProperty("total_distance_backward_km")
    private BigDecimal totalDistanceBackwardKm;

    @JsonProperty("active_vehicles_count")
    private Long activeVehiclesCount;

    @JsonProperty("forward_stops_count")
    private Integer forwardStopsCount;

    @JsonProperty("backward_stops_count")
    private Integer backwardStopsCount;

    public RouteInfoDTO(String routeId, String routeNumber, String routeName,
                        String routeColor, BigDecimal totalDistanceForwardKm,
                        BigDecimal totalDistanceBackwardKm, Long activeVehiclesCount,
                        Integer forwardStopsCount, Integer backwardStopsCount) {
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.routeColor = routeColor;
        this.totalDistanceForwardKm = totalDistanceForwardKm;
        this.totalDistanceBackwardKm = totalDistanceBackwardKm;
        this.activeVehiclesCount = activeVehiclesCount;
        this.forwardStopsCount = forwardStopsCount;
        this.backwardStopsCount = backwardStopsCount;
    }
}
