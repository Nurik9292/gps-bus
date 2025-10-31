package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetRouteByIdUseCase implements UseCase<Mono<GetRouteByIdUseCase.Query>, Mono<RouteData>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;
    private final RouteStopsService routeStopsService;
    private final VehicleRepository vehicleRepository;
    private final RouteDataMapper routeDataMapper;

    @Override
    public Mono<RouteData> execute(Mono<Query> routeIdMono) {
        return correlationService.executeWithCorrelation(
                routeIdMono.flatMap(this::executeWithCorrelation),
                "mobile"
        );
    }

    private Mono<RouteData> executeWithCorrelation(Query query) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting route by id - Correlation {}: routeId={}", correlationId, query.routeId);

                    return busRouteRepository.findById(BusRouteId.of(query.routeId))
                            .flatMap(this::enrichRouteWithStops)
                            .doOnSuccess(result -> log.debug("Retrieved route: {}", result.routeNumber()))
                            .onErrorMap(error -> {
                                log.error("Failed to get route by id {}: {}", query.routeId, error.getMessage());
                                return new RuntimeException("Route not found: " + query.routeId);
                            });
                });
    }

    private Mono<RouteData> enrichRouteWithStops(BusRoute route) {
        String routeId = route.getId().getValue();
        Mono<List<RouteStopDTO>> forwardStops = routeStopsService.getForwardStopsDTO(routeId);
        Mono<List<RouteStopDTO>> backwardStops = routeStopsService.getBackwardStopsDTO(routeId);
        Mono<Long> activeVehiclesCount = getActiveVehiclesCount(route.getRouteNumber());

        return Mono.zip(forwardStops, backwardStops, activeVehiclesCount)
                .flatMap(tuple -> routeDataMapper.toRouteDataWithStops(
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

    public record Query(String routeId) {
    }
}