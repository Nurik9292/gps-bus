package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RouteBasicInfoDTO {

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("route_color")
    private String routeColor;

    @JsonProperty("total_distance_km")
    private BigDecimal totalDistanceKm;

    @JsonProperty("active_vehicles_count")
    private Long activeVehiclesCount;

    public RouteBasicInfoDTO(String routeNumber, String routeName, String routeColor,
                             BigDecimal totalDistanceKm, Long activeVehiclesCount) {
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.routeColor = routeColor;
        this.totalDistanceKm = totalDistanceKm;
        this.activeVehiclesCount = activeVehiclesCount;
    }
}