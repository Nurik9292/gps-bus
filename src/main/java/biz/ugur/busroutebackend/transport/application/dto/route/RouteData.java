package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public record RouteData(
        String id,
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
        List<Coordinates> backwardGeometry,
        List<Coordinates> forwardGeometry,
        Long activeVehiclesCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<RouteStopDTO> forwardStops,
        List<RouteStopDTO> backwardStops
) {

    public static RouteData fromDomain(BusRoute busRoute) {
        return new RouteData(
                busRoute.getId().getValue(),
                busRoute.getRouteNumber(),
                busRoute.getRouteName(),
                busRoute.getNameTm(),
                busRoute.getNameEn(),
                busRoute.getRouteColor(),
                busRoute.getCityId(),
                busRoute.getIsActive(),
                busRoute.getEstimatedDurationMinutes(),
                0,
                0,
                busRoute.getTotalDistanceForwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceForwardMeters())
                                .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                busRoute.getTotalDistanceBackwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceBackwardMeters())
                                .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                busRoute.getBackwardGeometry() != null ? busRoute.getBackwardGeometry().getPoints() : List.of(),
                busRoute.getForwardGeometry() != null ? busRoute.getForwardGeometry().getPoints() : List.of(),
                0L,
                busRoute.getCreatedAt(),
                busRoute.getUpdatedAt(),
                List.of(),
                List.of()
        );
    }

    public static RouteData fromDomainWithStops(
            BusRoute busRoute,
            List<RouteStopDTO> forwardStops,
            List<RouteStopDTO> backwardStops,
            Long activeVehiclesCount
    ) {
        return new RouteData(
                busRoute.getId().getValue(),
                busRoute.getRouteNumber(),
                busRoute.getRouteName(),
                busRoute.getNameTm(),
                busRoute.getNameEn(),
                busRoute.getRouteColor(),
                busRoute.getCityId(),
                busRoute.getIsActive(),
                busRoute.getEstimatedDurationMinutes(),
                forwardStops.size(),
                backwardStops.size(),
                busRoute.getTotalDistanceForwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceForwardMeters())
                                .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                busRoute.getTotalDistanceBackwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceBackwardMeters())
                                .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                busRoute.getBackwardGeometry() != null ? busRoute.getBackwardGeometry().getPoints() : List.of(),
                busRoute.getForwardGeometry() != null ? busRoute.getForwardGeometry().getPoints() : List.of(),
                activeVehiclesCount != null ? activeVehiclesCount : 0L,
                busRoute.getCreatedAt(),
                busRoute.getUpdatedAt(),
                forwardStops,
                backwardStops
        );
    }

    public RouteData withStops(List<RouteStopDTO> forwardStops, List<RouteStopDTO> backwardStops) {
        return new RouteData(
                id, routeNumber, routeName, nameTm, nameEn, routeColor, cityId, isActive,
                estimatedDurationMinutes, forwardStops.size(), backwardStops.size(),
                totalDistanceForwardKm, totalDistanceBackwardKm, backwardGeometry, forwardGeometry,
                activeVehiclesCount, createdAt, updatedAt, forwardStops, backwardStops
        );
    }

    public RouteData withActiveVehiclesCount(Long activeVehiclesCount) {
        return new RouteData(
                id, routeNumber, routeName, nameTm, nameEn, routeColor, cityId, isActive,
                estimatedDurationMinutes, forwardStopsCount, backwardStopsCount,
                totalDistanceForwardKm, totalDistanceBackwardKm, backwardGeometry, forwardGeometry,
                activeVehiclesCount, createdAt, updatedAt, forwardStops, backwardStops
        );
    }

    public int getForwardStopsCount() {
        return forwardStopsCount != null ? forwardStopsCount :
                (forwardStops != null ? forwardStops.size() : 0);
    }


    public int getBackwardStopsCount() {
        return backwardStopsCount != null ? backwardStopsCount :
                (backwardStops != null ? backwardStops.size() : 0);
    }


    public boolean hasCompleteGeometry() {
        return forwardGeometry != null && !forwardGeometry.isEmpty() &&
                backwardGeometry != null && !backwardGeometry.isEmpty();
    }



    public boolean isActiveRoute() {
        return Boolean.TRUE.equals(isActive);
    }


    private static BigDecimal convertMetersToKm(Integer meters) {
        if (meters == null) {
            return null;
        }
        return new BigDecimal(meters)
                .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);
    }
}