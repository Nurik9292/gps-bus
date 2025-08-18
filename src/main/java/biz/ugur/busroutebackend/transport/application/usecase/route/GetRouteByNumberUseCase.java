package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
@Slf4j
public class GetRouteByNumberUseCase implements UseCase<Mono<GetRouteByNumberUseCase.Query>, Mono<RouteResult>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;


    @Override
    public Mono<RouteResult> execute(Mono<Query> query) {
        return correlationService.executeWithCorrelation(
                query.flatMap(this::executeWithCorrelation),
                "mobile"
        );
    }

    private Mono<RouteResult> executeWithCorrelation(Query query) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting route by route number - Correlation {}: routeNumber={}", correlationId, query.routeNumber);

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
