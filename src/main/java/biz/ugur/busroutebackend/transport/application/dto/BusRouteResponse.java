package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class BusRouteResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("route_name_tm")
    private String routeNameTm;

    @JsonProperty("route_color")
    private String routeColor;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("fare_price")
    private BigDecimal farePrice;

    @JsonProperty("estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @JsonProperty("forward_stops_count")
    private Integer forwardStopsCount;

    @JsonProperty("backward_stops_count")
    private Integer backwardStopsCount;

    @JsonProperty("total_distance_forward_km")
    private BigDecimal totalDistanceForwardKm;

    @JsonProperty("total_distance_backward_km")
    private BigDecimal totalDistanceBackwardKm;

    @JsonProperty("active_vehicles_count")
    private Long activeVehiclesCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public BusRouteResponse(String id, String routeNumber, String routeName, String routeNameTm,
                            String routeColor, Boolean isActive, BigDecimal farePrice,
                            Integer estimatedDurationMinutes, Integer forwardStopsCount,
                            Integer backwardStopsCount, BigDecimal totalDistanceForwardKm,
                            BigDecimal totalDistanceBackwardKm, Long activeVehiclesCount) {
        this.id = id;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.routeNameTm = routeNameTm;
        this.routeColor = routeColor;
        this.isActive = isActive;
        this.farePrice = farePrice;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.forwardStopsCount = forwardStopsCount;
        this.backwardStopsCount = backwardStopsCount;
        this.totalDistanceForwardKm = totalDistanceForwardKm;
        this.totalDistanceBackwardKm = totalDistanceBackwardKm;
        this.activeVehiclesCount = activeVehiclesCount;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}