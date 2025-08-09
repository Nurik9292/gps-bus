package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.RoutePoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public record RouteResult(
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
        List<RoutePoint> backwardGeometry,
        List<RoutePoint> forwardGeometry,
        Long activeVehiclesCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static RouteResult fromDomain(BusRoute busRoute) {
        return new RouteResult(
                busRoute.getId().getValue(),
                busRoute.getRouteNumber(),
                busRoute.getRouteName(),
                busRoute.getNameTm(),
                busRoute.getNameEn(),
                busRoute.getRouteColor(),
                busRoute.getCityId(),
                busRoute.getIsActive(),
                busRoute.getEstimatedDurationMinutes(),
                0,  // forward stops count - будет вычислено отдельно
                0, // backward stops count - будет вычислено отдельно
                busRoute.getTotalDistanceForwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceForwardMeters())
                                .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,

                busRoute.getTotalDistanceBackwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceBackwardMeters())
                            .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                busRoute.getBackwardGeometry().getPoints(),
                busRoute.getForwardGeometry().getPoints(),
                0L,
                busRoute.getCreatedAt(),
                busRoute.getUpdatedAt()

        );
    }
}
