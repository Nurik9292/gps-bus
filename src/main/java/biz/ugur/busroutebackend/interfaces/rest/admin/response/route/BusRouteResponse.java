package biz.ugur.busroutebackend.interfaces.rest.admin.response.route;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.domain.valueobject.RoutePoint;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class BusRouteResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("route_number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("name_tm")
    private String nameTm;

    @JsonProperty("name_en")
    private String nameEn;

    @JsonProperty("route_color")
    private String routeColor;

    @JsonProperty("city_id")
    private String cityId;

    @JsonProperty("is_active")
    private Boolean isActive;

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

    @JsonProperty("backword_geometry")
    private List<RoutePoint> backwardGeometry;

    @JsonProperty("forward_geometry")
    private List<RoutePoint> forwardGeometry;

    @JsonProperty("forward_stops_ids")
    private List<String> forwardStopsIds;

    @JsonProperty("backword_stops_ids")
    private List<String> backwardStopsIds;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public BusRouteResponse(String id,
                            String routeNumber,
                            String routeName,
                            String nameTm,
                            String nameEn,
                            String routeColor,
                            String cityId,
                            Boolean isActive,
                            Integer estimatedDurationMinutes,
                            Integer forwardStopsCount,
                            Integer backwardStopsCount,
                            BigDecimal totalDistanceForwardKm,
                            BigDecimal totalDistanceBackwardKm,
                            List<RoutePoint> backwardGeometry,
                            List<RoutePoint> forwardGeometry,
                            List<String> forwardStopsIds,
                            List<String> backwardStopsIds,
                            Long activeVehiclesCount,
                            Instant createdAt,
                            Instant updatedAt) {
        this.id = id;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.nameTm = nameTm;
        this.nameEn = nameEn;
        this.routeColor = routeColor;
        this.isActive = isActive;
        this.cityId = cityId;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.forwardStopsCount = forwardStopsCount;
        this.backwardStopsCount = backwardStopsCount;
        this.totalDistanceForwardKm = totalDistanceForwardKm;
        this.totalDistanceBackwardKm = totalDistanceBackwardKm;
        this.backwardGeometry = backwardGeometry;
        this.forwardGeometry = forwardGeometry;
        this.activeVehiclesCount = activeVehiclesCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.forwardStopsIds = forwardStopsIds;
        this.backwardStopsIds = backwardStopsIds;
    }

    public static BusRouteResponse fromResult(RouteData result) {
        return new  BusRouteResponse(
                result.id(),
                result.routeNumber(),
                result.routeName(),
                result.nameTm(),
                result.nameEn(),
                result.routeColor(),
                result.cityId(),
                result.isActive(),
                result.estimatedDurationMinutes(),
                result.forwardStopsCount(),
                result.backwardStopsCount(),
                result.totalDistanceForwardKm(),
                result.totalDistanceBackwardKm(),
                result.backwardGeometry(),
                result.forwardGeometry(),
                result.forwardStops().stream().map(RouteStopDTO::getStopId).toList(),
                result.backwardStops().stream().map(RouteStopDTO::getStopId).toList(),
                result.activeVehiclesCount(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}