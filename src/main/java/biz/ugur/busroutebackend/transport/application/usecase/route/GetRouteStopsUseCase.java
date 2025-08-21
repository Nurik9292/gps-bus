package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteStops;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetRouteStopsUseCase extends BaseUseCase<Mono<String>, RouteStops> {

    private final BusRouteRepository busRouteRepository;

    public GetRouteStopsUseCase(BusRouteRepository busRouteRepository,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Mono<RouteStops> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteStops> processInternal(String routeId) {
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
