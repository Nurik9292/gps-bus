package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetRouteByIdUseCase implements UseCase<Mono<GetRouteByIdUseCase.Query>, Mono<RouteResult>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;

    @Override
    public Mono<RouteResult> execute(Mono<Query> routeIdMono) {
        return correlationService.executeWithCorrelation(
                routeIdMono.flatMap(this::executeWithCorrelation),
                "mobile"
        );
    }

    private Mono<RouteResult> executeWithCorrelation(Query query) {
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