package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteValidationException;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;


@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class BusRoute extends AggregateRoot<BusRoute, BusRouteId> {

    private final BusRouteId id;

    private final String routeNumber;
    private final String routeName;
    private final String nameTm;
    private final String nameEn;
    private final String routeColor;

    private final Boolean isActive;
    private final String cityId;
    private final Integer estimatedDurationMinutes;


    private final String routeGeometryForward;
    private final String routeGeometryBackward;
    private final Integer totalDistanceForwardMeters;
    private final Integer totalDistanceBackwardMeters;


    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private Long version;


    public BusRoute editedBy(String adminUsername) {
        return this.toBuilder().updatedBy(adminUsername).build();
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public static BusRoute create(
            String routeNumber,
            String routeName,
            String nameTm,
            String nameEn,
            String routeColor,
            String cityId,
            Integer estimatedDurationMinutes) {

        String validatedNumber = validateAndNormalizeRouteNumber(routeNumber);
        String validatedName = validateRouteName(routeName);
        String validatedColor = validateAndNormalizeRouteColor(routeColor);

        return builder()
                .id(BusRouteId.generate())
                .routeNumber(validatedNumber)
                .routeName(validatedName)
                .nameTm(nameTm != null ? nameTm.trim() : "")
                .nameEn(nameEn != null ? nameEn.trim() : "")
                .routeColor(validatedColor)
                .isActive(true)
                .cityId(cityId)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .routeGeometryForward(null)
                .routeGeometryBackward(null)
                .totalDistanceForwardMeters(null)
                .totalDistanceBackwardMeters(null)
                .version(0L)
                .build();
    }


    public static BusRoute restore(
            BusRouteId id,
            String routeNumber,
            String routeName,
            String nameTm,
            String nameEn,
            String routeColor,
            Boolean isActive,
            String cityId,
            Integer estimatedDurationMinutes,
            String routeGeometryForward,
            String routeGeometryBackward,
            Integer totalDistanceForwardMeters,
            Integer totalDistanceBackwardMeters,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        return builder()
                .id(id)
                .routeNumber(routeNumber)
                .routeName(routeName)
                .nameTm(nameTm)
                .nameEn(nameEn)
                .routeColor(routeColor)
                .isActive(isActive != null ? isActive : true)
                .cityId(cityId)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .routeGeometryForward(routeGeometryForward)
                .routeGeometryBackward(routeGeometryBackward)
                .totalDistanceForwardMeters(totalDistanceForwardMeters)
                .totalDistanceBackwardMeters(totalDistanceBackwardMeters)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }



    public BusRoute updateRouteGeometry(RouteGeometry forwardGeometry, RouteGeometry backwardGeometry) {
        String forwardWKT = null;
        Integer forwardDistance = null;
        String backwardWKT = null;
        Integer backwardDistance = null;

        if (forwardGeometry != null) {
            forwardWKT = forwardGeometry.toWKT();
            forwardDistance = (int) Math.round(forwardGeometry.calculateDistanceMetersSimple());
        }

        if (backwardGeometry != null) {
            backwardWKT = backwardGeometry.toWKT();
            backwardDistance = (int) Math.round(backwardGeometry.calculateDistanceMetersSimple());
        }

        BusRoute updatedRoute = this.toBuilder()
                .routeGeometryForward(forwardWKT)
                .routeGeometryBackward(backwardWKT)
                .totalDistanceForwardMeters(forwardDistance)
                .totalDistanceBackwardMeters(backwardDistance)
                .build();

        if (forwardGeometry != null || backwardGeometry != null) {
            updatedRoute.registerEvent(new RouteGeometryUpdatedEvent(
                    this.id.getValue(),
                    this.routeNumber,
                    this.routeName,
                    forwardGeometry != null ? forwardGeometry.getPointCount() : 0,
                    backwardGeometry != null ? backwardGeometry.getPointCount() : 0,
                    forwardDistance,
                    backwardDistance
            ));
        }

        return updatedRoute;
    }

    public BusRoute updateBasicInfo(
            String routeNumber,
            String routeName,
            String nameTm,
            String nameEn,
            String routeColor,
            Integer estimatedDurationMinutes,
            String cityId) {

        String validatedNumber = validateAndNormalizeRouteNumber(routeNumber);
        String validatedName = validateRouteName(routeName);
        String validatedColor = validateAndNormalizeRouteColor(routeColor);

        return this.toBuilder()
                .routeNumber(validatedNumber)
                .routeName(validatedName)
                .nameTm(nameTm != null ? nameTm.trim() : "")
                .nameEn(nameEn != null ? nameEn.trim() : "")
                .routeColor(validatedColor)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .cityId(cityId)
                .build();
    }

    public BusRoute updateForwardGeometry(String geometryWKT, Integer distanceMeters) {
        if (geometryWKT == null || geometryWKT.trim().isEmpty()) {
            throw new IllegalArgumentException("Forward geometry WKT cannot be empty");
        }

        return this.toBuilder()
                .routeGeometryForward(geometryWKT)
                .totalDistanceForwardMeters(distanceMeters)
                .build();
    }

    public BusRoute updateBackwardGeometry(String geometryWKT, Integer distanceMeters) {
        if (geometryWKT == null || geometryWKT.trim().isEmpty()) {
            throw new IllegalArgumentException("Backward geometry WKT cannot be empty");
        }

        return this.toBuilder()
                .routeGeometryBackward(geometryWKT)
                .totalDistanceBackwardMeters(distanceMeters)
                .build();
    }

    public BusRoute activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }
        return this.toBuilder().isActive(true).build();
    }

    public BusRoute deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }
        return this.toBuilder().isActive(false).build();
    }

    public BusRoute clearGeometry() {
        return this.toBuilder()
                .routeGeometryForward(null)
                .routeGeometryBackward(null)
                .totalDistanceForwardMeters(null)
                .totalDistanceBackwardMeters(null)
                .build();
    }

    public BusRoute clearForwardGeometry() {
        return this.toBuilder()
                .routeGeometryForward(null)
                .totalDistanceForwardMeters(null)
                .build();
    }

    public BusRoute clearBackwardGeometry() {
        return this.toBuilder()
                .routeGeometryBackward(null)
                .totalDistanceBackwardMeters(null)
                .build();
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

    public RouteGeometry getGeometryByDirection(RouteDirection direction) {
        return switch (direction) {
            case FORWARD -> getForwardGeometry();
            case BACKWARD -> getBackwardGeometry();
        };
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

    public int getTotalGeometryPoints() {
        int points = 0;
        RouteGeometry forward = getForwardGeometry();
        if (forward != null) points += forward.getPointCount();
        RouteGeometry backward = getBackwardGeometry();
        if (backward != null) points += backward.getPointCount();
        return points;
    }



    private static String validateAndNormalizeRouteNumber(String routeNumber) {
        if (routeNumber == null || routeNumber.trim().isEmpty()) {
            throw new RouteValidationException("routeNumber", "Route number cannot be null or empty");
        }
        String normalized = routeNumber.trim().toUpperCase();
        if (!normalized.matches("\\d{1,3}[A-Z]{0,3}")) {
            throw new RouteValidationException("routeNumber",
                    "Invalid route number format. Expected: '29', '7A' or '7AL', got: " + routeNumber);
        }
        return normalized;
    }

    private static String validateRouteName(String routeName) {
        if (routeName == null || routeName.trim().isEmpty()) {
            throw new RouteValidationException("routeName", "Route name cannot be null or empty");
        }
        return routeName.trim();
    }

    private static String validateAndNormalizeRouteColor(String routeColor) {
        if (routeColor == null || !routeColor.matches("^#[0-9A-Fa-f]{6}$")) {
            return "#1976D2";
        }
        return routeColor.toUpperCase();
    }



    @Override
    public BusRouteId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BusRoute{" +
                "id=" + id +
                ", routeNumber='" + routeNumber + '\'' +
                ", routeName='" + routeName + '\'' +
                ", isActive=" + isActive +
                ", hasGeometry=" + hasGeometry() +
                '}';
    }
}
