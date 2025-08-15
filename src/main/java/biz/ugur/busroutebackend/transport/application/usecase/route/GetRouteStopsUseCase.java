package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteStops;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetRouteStopsUseCase implements UseCase<Mono<String>, Mono<RouteStops>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;

    @Override
    public Mono<RouteStops> execute(Mono<String> routeIdMono) {
        return correlationService.executeWithCorrelation(
                routeIdMono.flatMap(this::executeWithCorrelation),
                "mobile"
        );
    }

    private Mono<RouteStops> executeWithCorrelation(String routeId) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting route stops - Correlation {}: routeId={}", correlationId, routeId);

                    return busRouteRepository.getRouteStopsInfo(BusRouteId.of(routeId))
                            .collectList()
                            .map(stops -> new RouteStops(
                                    routeId,
                                    stops,
                                    stops.size()
                            ))
                            .doOnSuccess(result -> log.debug("Retrieved {} stops for route {}",
                                    result.totalStops(), routeId))
                            .onErrorMap(error -> {
                                log.error("Failed to get stops for route {}: {}", routeId, error.getMessage());
                                return new RuntimeException("Route stops not found: " + routeId);
                            });
                });
    }
}
