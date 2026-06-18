package biz.ugur.busroutebackend.interfaces.rest.transport.V2.response;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RouteDetailV2(
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
        Long activeVehiclesCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<RouteStopV2> forwardStops,
        List<RouteStopV2> backwardStops
) {

    public static RouteDetailV2 fromRouteData(RouteData route) {
        return new RouteDetailV2(
                route.id(),
                route.routeNumber(),
                route.routeName(),
                route.nameTm(),
                route.nameEn(),
                route.routeColor(),
                route.cityId(),
                route.isActive(),
                route.estimatedDurationMinutes(),
                route.getForwardStopsCount(),
                route.getBackwardStopsCount(),
                route.totalDistanceForwardKm(),
                route.totalDistanceBackwardKm(),
                route.activeVehiclesCount(),
                route.createdAt(),
                route.updatedAt(),
                mapStops(route.forwardStops()),
                mapStops(route.backwardStops())
        );
    }

    private static List<RouteStopV2> mapStops(List<RouteStopDTO> stops) {
        return stops == null ? List.of() : stops.stream().map(RouteStopV2::fromDto).toList();
    }
}
