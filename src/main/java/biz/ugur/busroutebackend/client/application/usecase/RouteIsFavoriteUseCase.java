package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class RouteIsFavoriteUseCase implements UseCase<RouteIsFavoriteUseCase.Request, Mono<Boolean>> {

    private final CorrelationContextService correlationService;
    private final RouteFavoriteRepository  routeFavoriteRepository;

    public RouteIsFavoriteUseCase(CorrelationContextService correlationService, RouteFavoriteRepository routeFavoriteRepository) {
        this.correlationService = correlationService;
        this.routeFavoriteRepository = routeFavoriteRepository;
    }

    @Override
    public Mono<Boolean> execute(Request request) {
        return correlationService.executeWithCorrelation(Mono.just(request).flatMap(this::executeWithCorrelation), "client");
    }

    private Mono<Boolean> executeWithCorrelation(Request request) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Route is favorite for client  Correlation Id: {} - RouteId: {} - ClientId: {}" ,
                    correlationId, request.routeId, request.clientId);
            return routeFavoriteRepository.existsByClientIdAndRouteId(ClientId.of(request.clientId), BusRouteId.of(request.routeId));
        });
    }

    public record Request(String clientId, String routeId) {}

}
