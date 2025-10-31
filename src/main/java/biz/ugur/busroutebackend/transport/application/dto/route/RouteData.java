package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;

import java.math.BigDecimal;
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
}