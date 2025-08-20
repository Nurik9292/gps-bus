package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteVehicleStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
public class RouteDtoMappingService {


    public RouteData toRouteDto(BusRoute busRoute) {
        log.debug("Mapping BusRoute to DTO: {}", busRoute.getRouteNumber());

        return RouteData.fromDomain(busRoute);
    }


    public RouteData toRouteWithFullDataDto(
            BusRoute busRoute,
            List<RouteStopInfo> forwardStops,
            List<RouteStopInfo> backwardStops,
            RouteVehicleStatistics vehicleStats) {

        log.debug("Mapping BusRoute with full data to DTO: {}", busRoute.getRouteNumber());

        List<RouteStopDTO> forwardStopDtos = forwardStops.stream()
                .map(this::toRouteStopDto)
                .toList();

        List<RouteStopDTO> backwardStopDtos = backwardStops.stream()
                .map(this::toRouteStopDto)
                .toList();

        return RouteData.fromDomainWithStops(
                busRoute,
                forwardStopDtos,
                backwardStopDtos,
                vehicleStats.getActiveVehiclesCount()
        );
    }


    public RouteStopDTO toRouteStopDto(RouteStopInfo routeStopInfo) {
        return new RouteStopDTO(
                routeStopInfo.getStopId(),
                routeStopInfo.getStopName(),
                routeStopInfo.getStopCode(),
                routeStopInfo.getSequence(),
                routeStopInfo.getDirection(),
                routeStopInfo.getEstimatedTravelTimeMinutes(),
                routeStopInfo.getDistanceFromStartMeters(),
                routeStopInfo.getLatitude(),
                routeStopInfo.getLongitude(),
                routeStopInfo.getIsMajorStop()
        );
    }


    public List<RouteStopDTO> toRouteStopDtos(List<RouteStopInfo> routeStopInfos) {
        return routeStopInfos.stream()
                .map(this::toRouteStopDto)
                .toList();
    }


    public List<RouteData> toRouteDtos(List<BusRoute> busRoutes) {
        return busRoutes.stream()
                .map(this::toRouteDto)
                .toList();
    }
}