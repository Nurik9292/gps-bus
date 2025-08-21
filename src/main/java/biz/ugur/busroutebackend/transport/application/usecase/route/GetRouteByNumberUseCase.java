package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class GetRouteByNumberUseCase implements UseCase<Mono<GetRouteByNumberUseCase.Query>, Mono<RouteData>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;
    private final RouteStopsService routeStopsService;
    private final VehicleRepository vehicleRepository;


    @Override
    public Mono<RouteData> execute(Mono<Query> query) {
        return correlationService.executeWithCorrelation(
                query.flatMap(this::executeWithCorrelation),
                "mobile"
        );
    }

    private Mono<RouteData> executeWithCorrelation(Query query) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting route by route number - Correlation {}: routeNumber={}", correlationId, query.routeNumber);

                    return busRouteRepository.findByRouteNumber(query.routeNumber)
                            .flatMap(this::enrichRouteWithStops)
                            .doOnSuccess(result -> log.debug("Retrieved route: {}", result.routeNumber()))
                            .onErrorMap(error -> {
                                log.error("Failed to get route by route number {}: {}", query.routeNumber, error.getMessage());
                                return new RuntimeException("Route not found: " +  query.routeNumber);
                            });
                });
    }


    private Mono<RouteData> enrichRouteWithStops(BusRoute route) {
        String routeId = route.getId().getValue();
        Mono<List<RouteStopDTO>> forwardStops = routeStopsService.getForwardStopsDTO(routeId);
        Mono<List<RouteStopDTO>> backwardStops = routeStopsService.getBackwardStopsDTO(routeId);
        Mono<Long> activeVehiclesCount = getActiveVehiclesCount(route.getRouteNumber());

        return Mono.zip(forwardStops, backwardStops, activeVehiclesCount)
                .map(tuple -> RouteData.fromDomainWithStops(
                        route,
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ))
                .doOnSuccess(result -> log.trace("Enriched route {} with stops", route.getRouteNumber()))
                .doOnError(error -> log.error("Failed to enrich route {} with stops", route.getRouteNumber(), error));
    }

    private Mono<Long> getActiveVehiclesCount(String routeNumber) {
        return vehicleRepository.countActiveVehiclesRouteNumber(routeNumber)
                .onErrorResume(error -> {
                    log.warn("Error counting active vehicles for route {}: {}", routeNumber, error.getMessage());
                    return Mono.just(0L);
                });
    }

    public record Query(String routeNumber) {}
}
