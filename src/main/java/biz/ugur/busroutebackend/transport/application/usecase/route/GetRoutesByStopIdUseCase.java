package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.repository.R2dbcRouteStopRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Log4j2
@Service
public class GetRoutesByStopIdUseCase
        implements UseCase<Mono<GetRoutesByStopIdUseCase.Query>,  Mono<List<GetRoutesByStopIdUseCase.Response>>> {

    private final R2dbcRouteStopRepository  repository;
    private final CorrelationContextService correlationContextService;

    public GetRoutesByStopIdUseCase(R2dbcRouteStopRepository repository, CorrelationContextService correlationContextService) {
        this.repository = repository;
        this.correlationContextService = correlationContextService;
    }

    @Override
    public Mono<List<Response>> execute(Mono<Query> query) {
        return correlationContextService.executeWithCorrelation(query.flatMap(this::executeWithCorrelation), "transport");
    }

    private Mono<List<Response>> executeWithCorrelation(Query query) {
        return correlationContextService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Get Route Stop relation CorrelationId: {} - StopId: {} ", correlationId, query.stopId);
            return repository.getStopRoutesDetail(query.stopId, query.direction)
                    .map(route -> new Response(route.getRouteId(), route.getRouteName(), route.getRouteNumber()))
                    .collectList();
        });
    }

    public record Query(String stopId, int direction) {}

    public record Response(String routeId, String routeName, String routeNumber) {

    }
}
