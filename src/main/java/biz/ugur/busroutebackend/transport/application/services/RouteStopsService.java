package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RouteStopsService {

    Mono<Void> saveRouteStops(String routeId, List<String> forwardStops, List<String> backwardStops);
    Mono<Void> updateRouteStops(String routeId, List<String> forwardStops, List<String> backwardStops);
    Mono<Void> deleteRouteStops(String routeId);

    Flux<RouteStopInfo> getRouteStops(String routeId, int direction);

    Mono<List<RouteStopDTO>> getForwardStopsDTO(String routeId);

    Mono<List<RouteStopDTO>> getBackwardStopsDTO(String routeId);

    Mono<RouteStopsDataDTO> getAllRouteStopsDTO(String routeId);

    record RouteStopsDataDTO(
            List<RouteStopDTO> forwardStops,
            List<RouteStopDTO> backwardStops
    ) {}
}
