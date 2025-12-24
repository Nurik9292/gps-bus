package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class StopIsFavoriteUseCase implements UseCase<StopIsFavoriteUseCase.Request, Mono<Boolean>> {

    private final CorrelationContextService correlationService;
    private final StopFavoriteRepository stopFavoriteRepository;

    public StopIsFavoriteUseCase(CorrelationContextService correlationService, StopFavoriteRepository stopFavoriteRepository) {
        this.correlationService = correlationService;
        this.stopFavoriteRepository = stopFavoriteRepository;
    }

    @Override
    public Mono<Boolean> execute(Request request) {
        return correlationService.executeWithCorrelation(Mono.just(request).flatMap(this::executeWithCorrelation), "client");
    }

    private Mono<Boolean> executeWithCorrelation(Request request) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Checking if stop is favorite - CorrelationId: {} - StopId: {} - ClientId: {}",
                    correlationId, request.stopId, request.clientId);
            return stopFavoriteRepository.existsByClientIdAndStopId(ClientId.of(request.clientId), BusStopId.of(request.stopId));
        });
    }

    public record Request(String clientId, String stopId) {}
}
