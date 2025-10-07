package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Table("bus_routes")
@Builder
public class BusRoute extends AggregateRoot<BusRoute, BusRouteId> {

    @Id
    @Column("id")
    private BusRouteId id;

    @Column("route_number")
    private String routeNumber;

    @Column("route_name")
    private String routeName;

    @Column("name_tm")
    private String nameTm;

    @Column("name_en")
    private String nameEn;

    @Column("route_color")
    private String routeColor;

    @Column("is_active")
    private Boolean isActive;

    @Column("city_id")
    private String cityId;

    @Column("estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column("route_geometry_forward")
    private String routeGeometryForward;

    @Column("route_geometry_backward")
    private String routeGeometryBackward;

    @Setter
    @Column("total_distance_forward_meters")
    private Integer totalDistanceForwardMeters;

    @Setter
    @Column("total_distance_backward_meters")
    private Integer totalDistanceBackwardMeters;

    @Transient
    private List<BusStop> busStops = new ArrayList<>();

    public void updateRouteGeometry(RouteGeometry forwardGeometry, RouteGeometry backwardGeometry) {
        boolean hasChanges = false;

        if (forwardGeometry != null) {
            String forwardWKT = forwardGeometry.toWKT();
            int forwardDistance = (int) Math.round(forwardGeometry.calculateDistanceMeters());

            this.routeGeometryForward = forwardWKT;
            this.totalDistanceForwardMeters = forwardDistance;
            hasChanges = true;

        }

        if (backwardGeometry != null) {
            String backwardWKT = backwardGeometry.toWKT();
            int backwardDistance = (int) Math.round(backwardGeometry.calculateDistanceMeters());

            this.routeGeometryBackward = backwardWKT;
            this.totalDistanceBackwardMeters = backwardDistance;
            hasChanges = true;


        }

        if (hasChanges) {
            registerEvent(new RouteGeometryUpdatedEvent(
                    this.id.getValue(),
                    this.routeNumber,
                    this.routeName,
                    Objects.requireNonNull(forwardGeometry).getPointCount(),
                    Objects.requireNonNull(backwardGeometry).getPointCount(),
                    this.totalDistanceForwardMeters,
                    this.totalDistanceBackwardMeters
            ));

        }
    }


    public RouteGeometry getForwardGeometry() {
        if (routeGeometryForward == null || routeGeometryForward.trim().isEmpty()) {
            return null;
        }

        try {
            return RouteGeometry.fromWKT(routeGeometryForward);
        } catch (Exception e) {
            return null;
        }
    }

    public RouteGeometry getBackwardGeometry() {
        if (routeGeometryBackward == null || routeGeometryBackward.trim().isEmpty()) {
            return null;
        }

        try {
            return RouteGeometry.fromWKT(routeGeometryBackward);
        } catch (Exception e) {
            return null;
        }
    }

    public void updateBasicInfo(
            String routeNumber,
            String routeName,
            String nameTm,
            String nameEn,
            String routeColor,
            Integer estimatedDurationMinutes,
            String cityId) {
        this.routeName = routeName;
        this.nameTm = nameTm;
        this.nameEn = nameEn;
        this.routeColor = routeColor;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.cityId = cityId;
        this.updatedAt = Instant.now();

    }

    public void updateForwardGeometry(String geometryWKT, Integer distanceMeters) {
        if (geometryWKT == null || geometryWKT.trim().isEmpty()) {
            throw new IllegalArgumentException("Forward geometry WKT cannot be empty");
        }

        this.routeGeometryForward = geometryWKT;
        this.totalDistanceForwardMeters = distanceMeters;
        this.updatedAt = Instant.now();
    }

    public void updateBackwardGeometry(String geometryWKT, Integer distanceMeters) {
        if (geometryWKT == null || geometryWKT.trim().isEmpty()) {
            throw new IllegalArgumentException("Backward geometry WKT cannot be empty");
        }

        this.routeGeometryBackward = geometryWKT;
        this.totalDistanceBackwardMeters = distanceMeters;
        this.updatedAt = Instant.now();

    }

    public boolean hasGeometry() {
        return hasForwardGeometry() || hasBackwardGeometry();
    }


    public boolean hasForwardGeometry() {
        return routeGeometryForward != null && !routeGeometryForward.trim().isEmpty();
    }


    public boolean hasBackwardGeometry() {
        return routeGeometryBackward != null && !routeGeometryBackward.trim().isEmpty();
    }


    public boolean hasCompleteGeometry() {
        return hasForwardGeometry() && hasBackwardGeometry();
    }



    public RouteGeometry getGeometryByDirection(RouteDirection direction) {
        return switch (direction) {
            case FORWARD -> getForwardGeometry();
            case BACKWARD -> getBackwardGeometry();
        };
    }


    public int getTotalGeometryPoints() {
        int points = 0;

        RouteGeometry forward = getForwardGeometry();
        if (forward != null) {
            points += forward.getPointCount();
        }

        RouteGeometry backward = getBackwardGeometry();
        if (backward != null) {
            points += backward.getPointCount();
        }

        return points;
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

    public void clearGeometry() {
        this.routeGeometryForward = null;
        this.routeGeometryBackward = null;
        this.totalDistanceForwardMeters = null;
        this.totalDistanceBackwardMeters = null;
        this.updatedAt = Instant.now();

    }


    public void clearForwardGeometry() {
        this.routeGeometryForward = null;
        this.totalDistanceForwardMeters = null;
        this.updatedAt = Instant.now();

    }


    public void clearBackwardGeometry() {
        this.routeGeometryBackward = null;
        this.totalDistanceBackwardMeters = null;
        this.updatedAt = Instant.now();

    }

    public List<BusStop> getBusStops() {
        return new ArrayList<>(busStops);
    }

    public void setBusStops(List<BusStop> stops) {
        this.busStops = stops != null ? new ArrayList<>(stops) : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BusRoute busRoute = (BusRoute) o;
        return Objects.equals(id, busRoute.id) &&
                Objects.equals(routeNumber, busRoute.routeNumber) &&
                Objects.equals(routeName, busRoute.routeName) &&
                Objects.equals(nameTm, busRoute.nameTm) &&
                Objects.equals(nameEn, busRoute.nameEn) &&
                Objects.equals(routeColor, busRoute.routeColor) &&
                Objects.equals(isActive, busRoute.isActive) &&
                Objects.equals(cityId, busRoute.cityId) &&
                Objects.equals(estimatedDurationMinutes, busRoute.estimatedDurationMinutes) &&
                Objects.equals(routeGeometryForward, busRoute.routeGeometryForward) &&
                Objects.equals(routeGeometryBackward, busRoute.routeGeometryBackward) &&
                Objects.equals(totalDistanceForwardMeters, busRoute.totalDistanceForwardMeters) &&
                Objects.equals(totalDistanceBackwardMeters, busRoute.totalDistanceBackwardMeters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                id,
                routeNumber,
                routeName,
                nameTm,
                nameEn,
                routeColor,
                isActive,
                cityId,
                estimatedDurationMinutes,
                routeGeometryForward,
                routeGeometryBackward,
                totalDistanceForwardMeters,
                totalDistanceBackwardMeters);
    }


    @Override
    public Instant getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public Instant getUpdatedAt() {
        return super.getUpdatedAt();
    }

    @Override
    public String toString() {
        return "BusRoute{" +
                "id=" + id +
                ", routeNumber='" + routeNumber + '\'' +
                ", routeName='" + routeName + '\'' +
                ", nameTm='" + nameTm + '\'' +
                ", nameEn='" + nameEn + '\'' +
                ", routeColor='" + routeColor + '\'' +
                ", isActive=" + isActive +
                ", cityId='" + cityId + '\'' +
                ", estimatedDurationMinutes=" + estimatedDurationMinutes +
                ", routeGeometryForward='" + routeGeometryForward + '\'' +
                ", routeGeometryBackward='" + routeGeometryBackward + '\'' +
                ", totalDistanceForwardMeters=" + totalDistanceForwardMeters +
                ", totalDistanceBackwardMeters=" + totalDistanceBackwardMeters +
                ", busStops=" + busStops +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
