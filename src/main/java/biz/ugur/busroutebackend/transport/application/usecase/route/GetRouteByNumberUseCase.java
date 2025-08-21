package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class GetRouteByNumberUseCase extends BaseUseCase<Mono<GetRouteByNumberUseCase.Query>, RouteResult> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;

    public GetRouteByNumberUseCase(BusRouteRepository busRouteRepository,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.busRouteRepository = busRouteRepository;
        this.correlationService = correlationService;
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
                    log.debug("Getting route by route number - Correlation {}: routeNumber: {}", correlationId, query.routeNumber);

                    return busRouteRepository.findByRouteNumber(query.routeNumber)
                            .map(RouteResult::fromDomain)
                            .doOnSuccess(result -> log.debug("Retrieved route: {}", result.routeNumber()))
                            .onErrorMap(error -> {
                                log.error("Failed to get route by route number {}: {}", query.routeNumber, error.getMessage());
                                return new RuntimeException("Route not found: " +  query.routeNumber);
                            });
                });
    }

    public record Query(String routeNumber) {}
}
