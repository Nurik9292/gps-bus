package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class RouteDataMapper {

    private final RouteGeometryPointsCache geometryCache;

    public RouteDataMapper(RouteGeometryPointsCache geometryCache) {
        this.geometryCache = geometryCache;
    }

    public Mono<RouteData> toRouteData(BusRoute busRoute) {
        return Mono.just(toRouteDataSync(busRoute));
    }

    public Mono<RouteData> toRouteDataWithStops(
            BusRoute busRoute,
            List<RouteStopDTO> forwardStops,
            List<RouteStopDTO> backwardStops,
            Long activeVehicleCount
    ) {
        return Mono.just(toRouteDataWithStopsSync(busRoute, forwardStops, backwardStops, activeVehicleCount));
    }

    public RouteData toRouteDataSync(BusRoute busRoute) {
        return createRouteData(busRoute, List.of(), List.of(), 0L);
    }

    public RouteData toRouteDataWithStopsSync(
            BusRoute busRoute,
            List<RouteStopDTO> forwardStops,
            List<RouteStopDTO> backwardStops,
            Long activeVehicleCount
    ) {
        return createRouteData(busRoute, forwardStops, backwardStops, activeVehicleCount);
    }

    private RouteData createRouteData(
            BusRoute busRoute,
            List<RouteStopDTO> forwardStops,
            List<RouteStopDTO> backwardStops,
            Long activeVehicleCount
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
                convertMetersToKm(busRoute.getTotalDistanceForwardMeters()),
                convertMetersToKm(busRoute.getTotalDistanceBackwardMeters()),
                geometryCache.backwardPoints(busRoute),
                geometryCache.forwardPoints(busRoute),
                activeVehicleCount != null ? activeVehicleCount : 0L,
                busRoute.getCreatedAt(),
                busRoute.getUpdatedAt(),
                forwardStops,
                backwardStops
        );
    }

    private BigDecimal convertMetersToKm(Integer meters) {
        if (meters == null) {
            return null;
        }
        return new BigDecimal(meters)
                .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);
    }

}
