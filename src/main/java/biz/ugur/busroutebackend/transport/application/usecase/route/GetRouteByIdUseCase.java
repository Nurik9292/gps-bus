package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetRouteByIdUseCase extends BaseUseCase<Mono<GetRouteByIdUseCase.Query>, RouteResult> {

    private final BusRouteRepository busRouteRepository;

    public GetRouteByIdUseCase(BusRouteRepository busRouteRepository,
                               CorrelationContextService correlationContextService,
                               EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.busRouteRepository = busRouteRepository;
    }



    @Override
    protected Mono<RouteResult> process(Mono<Query> query) {
        return query.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteResult> processInternal(Query query) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting route by id - Correlation {}: routeId={}", correlationId, query.routeId);

                    return busRouteRepository.findById(BusRouteId.of(query.routeId))
                            .map(RouteResult::fromDomain)
                            .doOnSuccess(result -> log.debug("Retrieved route: {}", result.routeNumber()))
                            .onErrorMap(error -> {
                                log.error("Failed to get route by id {}: {}", query.routeId, error.getMessage());
                                return new RuntimeException("Route not found: " + query.routeId);
                            });
                });
    }

    public record Query(String routeId) {
    }
}