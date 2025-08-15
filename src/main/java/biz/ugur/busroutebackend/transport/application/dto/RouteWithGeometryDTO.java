package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RouteWithGeometryDTO {

    @JsonProperty("route_id")
    private String routeId;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("route_color")
    private String routeColor;


    @JsonProperty("geometry_forward")
    private Object geometryForward;

    @JsonProperty("geometry_backward")
    private Object geometryBackward;

    @JsonProperty("total_distance_forward_km")
    private BigDecimal totalDistanceForwardKm;

    @JsonProperty("total_distance_backward_km")
    private BigDecimal totalDistanceBackwardKm;

    @JsonProperty("forward_stops")
    private List<RouteStopDTO> forwardStops;

    @JsonProperty("backward_stops")
    private List<RouteStopDTO> backwardStops;

    @JsonProperty("active_vehicles_count")
    private Long activeVehiclesCount;

    @JsonProperty("vehicles_in_motion")
    private Long vehiclesInMotion;

    @JsonProperty("last_updated")
    private String lastUpdated;

    @JsonProperty("has_real_time_data")
    private Boolean hasRealTimeData;

    public int getForwardStopsCount() {
        return forwardStops != null ? forwardStops.size() : 0;
    }

    public int getBackwardStopsCount() {
        return backwardStops != null ? backwardStops.size() : 0;
    }

    public boolean hasCompleteGeometry() {
        return geometryForward != null && geometryBackward != null;
    }

    public BigDecimal getTotalDistanceKm() {
        if (totalDistanceForwardKm != null) {
            return totalDistanceForwardKm;
        }
        return BigDecimal.ZERO;
    }
}
