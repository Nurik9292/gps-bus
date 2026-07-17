package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.interfaces.rest.transport.V1.request.RouteGeometryRequest;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDtoMappingService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteVehicleStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetRouteWithGeometryUseCase implements UseCase<String, Mono<RouteData>> {

    private final BusRouteRepository busRouteRepository;
    private final RouteDtoMappingService routeDtoMappingService;
    private final CorrelationContextService correlationContextService;

    @Override
    public Mono<RouteData> execute(String routeNumber) {
        return correlationContextService.executeWithCorrelation(
                Mono.just(routeNumber).flatMap(this::executeWithCorrelation),
                "transport"
        );
    }

    private Mono<RouteData> executeWithCorrelation(String routeNumber) {
        return correlationContextService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting route with geometry - CorrelationId: {} - RouteNumber: {}",
                            correlationId, routeNumber);

                    return busRouteRepository.findPreferredByRouteNumber(routeNumber)
                            .flatMap(this::enrichWithStopsAndVehicles)
                            .doOnSuccess(route -> log.info("Route {} geometry retrieved - CorrelationId: {} - ForwardStops: {} - BackwardStops: {}",
                                    routeNumber, correlationId, route.getForwardStopsCount(), route.getBackwardStopsCount()))
                            .doOnError(error -> log.error("Failed to get route geometry - CorrelationId: {} - RouteNumber: {} - Error: {}",
                                    correlationId, routeNumber, error.getMessage()));
                });
    }



    public Flux<RouteData> getAllActiveRoutes() {
        return correlationContextService.executeWithCorrelation(
                        this.getAllActiveRoutesWithCorrelation(),
                        "transport"
                )
                .flatMapMany(Flux::fromIterable);
    }

    private Mono<List<RouteData>> getAllActiveRoutesWithCorrelation() {
        return correlationContextService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting all active routes - CorrelationId: {}", correlationId);

                    return busRouteRepository.findActiveRoutes()
                            .map(routeDtoMappingService::toRouteDto)
                            .collectList()
                            .doOnSuccess(routes -> log.debug("All active routes retrieved - CorrelationId: {} - Count: {}",
                                    correlationId, routes.size()));
                });
    }

    public Flux<RouteStopDTO> getRouteStops(String routeNumber, Integer direction) {
        return correlationContextService.executeWithCorrelation(
                this.getRouteStopsWithCorrelation(routeNumber, direction),
                "transport"
        ).flux().flatMap(Flux::fromIterable);
    }

    private Mono<List<RouteStopDTO>> getRouteStopsWithCorrelation(String routeNumber, Integer direction) {
        return correlationContextService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting route stops - CorrelationId: {} - RouteNumber: {} - Direction: {}",
                            correlationId, routeNumber, direction);

                    return busRouteRepository.findPreferredByRouteNumber(routeNumber)
                            .flatMapMany(route -> busRouteRepository.getRouteStopsInfoByRouteId(route.getId().getValue(), direction))
                            .map(routeDtoMappingService::toRouteStopDto)
                            .collectList()
                            .doOnSuccess(stops -> log.debug("Route stops retrieved - CorrelationId: {} - RouteNumber: {} - Direction: {} - Count: {}",
                                    correlationId, routeNumber, direction, stops.size()));
                });
    }

    public Mono<String> updateRouteGeometry(String routeNumber, RouteGeometryRequest request) {
        return correlationContextService.executeWithCorrelation(
                this.updateRouteGeometryWithCorrelation(routeNumber, request),
                "transport"
        );
    }

    private Mono<String> updateRouteGeometryWithCorrelation(String routeNumber, RouteGeometryRequest request) {
        return correlationContextService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Updating route geometry - CorrelationId: {} - RouteNumber: {}", correlationId, routeNumber);

                    return busRouteRepository.findPreferredByRouteNumber(routeNumber)
                            .flatMap(route -> {
                                try {
                                    var forwardGeometry = createRouteGeometry(request.getForwardCoordinates());
                                    var backwardGeometry = request.getBackwardCoordinates() != null ?
                                            createRouteGeometry(request.getBackwardCoordinates()) : null;

                                    route.updateRouteGeometry(forwardGeometry, backwardGeometry);

                                    return busRouteRepository.save(route)
                                            .map(savedRoute -> "Route geometry updated successfully")
                                            .doOnSuccess(result -> log.info("Route geometry updated successfully - CorrelationId: {} - RouteNumber: {}",
                                                    correlationId, routeNumber));

                                } catch (Exception e) {
                                    log.error("Failed to update route geometry - CorrelationId: {} - RouteNumber: {} - Error: {}",
                                            correlationId, routeNumber, e.getMessage());
                                    return Mono.error(new IllegalArgumentException("Invalid geometry data: " + e.getMessage()));
                                }
                            });
                });
    }



    private Mono<RouteData> enrichWithStopsAndVehicles(BusRoute busRoute) {
        String routeId = busRoute.getId().getValue();

        Mono<List<RouteStopInfo>> forwardStops = busRouteRepository
                .getRouteStopsInfoByRouteId(routeId, 0)
                .collectList();

        Mono<List<RouteStopInfo>> backwardStops = busRouteRepository
                .getRouteStopsInfoByRouteId(routeId, 1)
                .collectList();

        Mono<RouteVehicleStatistics> vehicleStats = busRouteRepository
                .getRouteVehicleStatistics(busRoute.getId())
                .switchIfEmpty(Mono.just(new RouteVehicleStatistics(0L, 0L, 0L)));

        return Mono.zip(forwardStops, backwardStops, vehicleStats)
                .map(tuple -> {
                    List<RouteStopInfo> forward = tuple.getT1();
                    List<RouteStopInfo> backward = tuple.getT2();
                    RouteVehicleStatistics stats = tuple.getT3();

                    return routeDtoMappingService.toRouteWithFullDataDto(busRoute, forward, backward, stats);
                });
    }

    private RouteGeometry createRouteGeometry(List<Double[]> coordinates) {
        return RouteGeometry.fromCoordinateArrays(coordinates);
    }
}
