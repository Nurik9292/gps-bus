package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class FindRouteByIdUseCase implements UseCase<String, Mono<RouteResult>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService  correlationContextService;

    public FindRouteByIdUseCase(BusRouteRepository busRouteRepository, CorrelationContextService correlationContextService) {
        this.busRouteRepository = busRouteRepository;
        this.correlationContextService = correlationContextService;
    }

    @Override
    public Mono<RouteResult> execute(String routeId) {
        return correlationContextService
                .executeWithCorrelation(Mono.just(routeId).flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<RouteResult> executeWithCorrelation(String routeId) {
        return correlationContextService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Find route by id Correlation ID: {} RouteId: {}", correlationId, routeId);
            return busRouteRepository.findById(BusRouteId.of(routeId)).map(RouteResult::fromDomain);
        });
    }
}
