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
    private String routeNumber; // "29", "7A"

    @JsonProperty("route_name")
    private String routeName; // "Толкучка - Серхетабат"

    @JsonProperty("route_color")
    private String routeColor; // "#9C27B0" - HEX цвет для отображения на карте

    // Геометрия маршрута (GeoJSON LineString)
    @JsonProperty("geometry_forward")
    private Object geometryForward; // Parsed GeoJSON LineString object

    @JsonProperty("geometry_backward")
    private Object geometryBackward; // Parsed GeoJSON LineString object

    // Расстояния в километрах
    @JsonProperty("total_distance_forward_km")
    private BigDecimal totalDistanceForwardKm;

    @JsonProperty("total_distance_backward_km")
    private BigDecimal totalDistanceBackwardKm;

    // Остановки в правильном порядке
    @JsonProperty("forward_stops")
    private List<RouteStopDTO> forwardStops;

    @JsonProperty("backward_stops")
    private List<RouteStopDTO> backwardStops;

    // Статистика по автобусам
    @JsonProperty("active_vehicles_count")
    private Long activeVehiclesCount;

    @JsonProperty("vehicles_in_motion")
    private Long vehiclesInMotion;

    // Дополнительные метаданные
    @JsonProperty("last_updated")
    private String lastUpdated;

    @JsonProperty("has_real_time_data")
    private Boolean hasRealTimeData;

    // Вспомогательные методы
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
