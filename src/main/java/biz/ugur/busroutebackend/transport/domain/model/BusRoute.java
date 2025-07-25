package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Table("bus_routes")
public class BusRoute extends AggregateRoot<BusRoute, BusRouteId> {

    @Id
    @Column("id")
    private BusRouteId id;

    @Column("route_number")
    private String routeNumber; // "29", "7A"

    @Column("route_name")
    private String routeName; // "Центральный рынок - Гипподром"

    @Column("route_name_tm")
    private String routeNameTm; // "Merkezi bazar - Ýaryş meýdany"

    @Column("route_color")
    private String routeColor; // "#E53935" - HEX цвет для карты

    @Column("is_active")
    private Boolean isActive;

    @Column("fare_price")
    private BigDecimal farePrice;

    @Column("estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column("route_geometry_forward")
    private String routeGeometryForward;

    @Column("route_geometry_backward")
    private String routeGeometryBackward;

    @Column("total_distance_forward_meters")
    private Integer totalDistanceForwardMeters;

    @Column("total_distance_backward_meters")
    private Integer totalDistanceBackwardMeters;

    @Transient
    private List<BusStop> busStops = new ArrayList<>();

    public BusRoute(String routeNumber, String routeName, String routeNameTm, String routeColor) {
        this.id = BusRouteId.generate();
        this.routeNumber = validateRouteNumber(routeNumber);
        this.routeName = validateRouteName(routeName);
        this.routeNameTm = routeNameTm;
        this.routeColor = validateRouteColor(routeColor);
        this.isActive = true;
        this.farePrice = new BigDecimal("1.00");
        this.estimatedDurationMinutes = 60;
    }

    public BusRoute(BusRouteId id, String routeNumber, String routeName, String routeNameTm,
                    String routeColor, Boolean isActive, BigDecimal farePrice,
                    Integer estimatedDurationMinutes, String routeGeometryForward,
                    String routeGeometryBackward, Integer totalDistanceForwardMeters,
                    Integer totalDistanceBackwardMeters) {
        this.id = id;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.routeNameTm = routeNameTm;
        this.routeColor = routeColor;
        this.isActive = isActive;
        this.farePrice = farePrice;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.routeGeometryForward = routeGeometryForward;
        this.routeGeometryBackward = routeGeometryBackward;
        this.totalDistanceForwardMeters = totalDistanceForwardMeters;
        this.totalDistanceBackwardMeters = totalDistanceBackwardMeters;
    }

    public void updateRouteGeometry(RouteGeometry forwardGeometry, RouteGeometry backwardGeometry) {
        if (forwardGeometry == null) {
            throw new IllegalArgumentException("Forward route geometry cannot be null");
        }

        forwardGeometry.validate();
        if (backwardGeometry != null) {
            backwardGeometry.validate();
        }

        this.routeGeometryForward = forwardGeometry.toGeoJson();
        this.totalDistanceForwardMeters = forwardGeometry.calculateTotalDistance();

        if (backwardGeometry != null) {
            this.routeGeometryBackward = backwardGeometry.toGeoJson();
            this.totalDistanceBackwardMeters = backwardGeometry.calculateTotalDistance();
        }

        registerEvent(new RouteGeometryUpdatedEvent(
                this.id.getValue(),
                this.routeNumber,
                this.routeName,
                forwardGeometry.getCoordinatesCount(),
                this.totalDistanceForwardMeters,
                backwardGeometry != null ? backwardGeometry.getCoordinatesCount() : 0,
                this.totalDistanceBackwardMeters
        ));
    }

    public RouteGeometry getForwardGeometry() {
        if (routeGeometryForward == null) return null;
        return RouteGeometry.fromGeoJson(routeGeometryForward);
    }

    public RouteGeometry getBackwardGeometry() {
        if (routeGeometryBackward == null) return null;
        return RouteGeometry.fromGeoJson(routeGeometryBackward);
    }

    public boolean hasCompleteGeometry() {
        return routeGeometryForward != null && routeGeometryBackward != null;
    }

    public RouteGeometry getGeometryByDirection(RouteDirection direction) {
        return switch (direction) {
            case FORWARD -> getForwardGeometry();
            case BACKWARD -> getBackwardGeometry();
        };
    }

    // Обновление информации о маршруте
    public void updateRouteInfo(String routeName, String routeNameTm,
                                BigDecimal farePrice, Integer estimatedDuration) {
        if (routeName != null && !routeName.trim().isEmpty()) {
            this.routeName = routeName.trim();
        }
        if (routeNameTm != null && !routeNameTm.trim().isEmpty()) {
            this.routeNameTm = routeNameTm.trim();
        }
        if (farePrice != null && farePrice.compareTo(BigDecimal.ZERO) > 0) {
            this.farePrice = farePrice;
        }
        if (estimatedDuration != null && estimatedDuration > 0) {
            this.estimatedDurationMinutes = estimatedDuration;
        }
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    @Override
    public BusRouteId getId() {
        return id;
    }


    private String validateRouteNumber(String routeNumber) {
        if (routeNumber == null || routeNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Route number cannot be null or empty");
        }

        String number = routeNumber.trim().toUpperCase();
        // Формат: "29", "7A", "12B" - цифры + опциональная буква
        if (!number.matches("\\d{1,3}[A-Z]?")) {
            throw new IllegalArgumentException("Invalid route number format. Expected: '29' or '7A'");
        }
        return number;
    }

    private String validateRouteName(String routeName) {
        if (routeName == null || routeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Route name cannot be null or empty");
        }
        return routeName.trim();
    }

    private String validateRouteColor(String routeColor) {
        if (routeColor == null || !routeColor.matches("^#[0-9A-Fa-f]{6}$")) {
            return "#1976D2";
        }
        return routeColor.toUpperCase();
    }

    public boolean connectsStops(String fromStopId, String toStopId) {
        if (busStops.isEmpty()) {
            // Если остановки не загружены, возвращаем true для совместимости
            // В реальности нужно загружать через репозиторий
            return true;
        }

        boolean hasFromStop = busStops.stream()
                .anyMatch(stop -> stop.getId().getValue().equals(fromStopId));
        boolean hasToStop = busStops.stream()
                .anyMatch(stop -> stop.getId().getValue().equals(toStopId));

        return hasFromStop && hasToStop;
    }

    public boolean hasStop(String stopId) {
        if (busStops.isEmpty()) {
            return true; // Заглушка для совместимости
        }

        return busStops.stream()
                .anyMatch(stop -> stop.getId().getValue().equals(stopId));
    }

    public int getStopsBetween(String fromStopId, String toStopId) {
        if (busStops.isEmpty()) {
            return 3; // Заглушка - среднее количество остановок
        }

        int fromIndex = -1;
        int toIndex = -1;

        for (int i = 0; i < busStops.size(); i++) {
            String stopId = busStops.get(i).getId().getValue();
            if (stopId.equals(fromStopId)) {
                fromIndex = i;
            }
            if (stopId.equals(toStopId)) {
                toIndex = i;
            }
        }

        if (fromIndex == -1 || toIndex == -1) {
            return 3; // Заглушка
        }

        return Math.abs(toIndex - fromIndex);
    }

    public List<BusStop> getBusStops() {
        return new ArrayList<>(busStops);
    }

    public void setBusStops(List<BusStop> stops) {
        this.busStops = stops != null ? new ArrayList<>(stops) : new ArrayList<>();
    }
}
