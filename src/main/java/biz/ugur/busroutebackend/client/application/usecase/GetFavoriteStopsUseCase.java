package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetBusStopByIdUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Log4j2
@Service
public class GetFavoriteStopsUseCase implements UseCase<String, Mono<List<StopData>>> {

    private final CorrelationContextService correlationService;
    private final StopFavoriteRepository stopFavoriteRepository;
    private final GetBusStopByIdUseCase getBusStopByIdUseCase;

    public GetFavoriteStopsUseCase(CorrelationContextService correlationService,
                                   StopFavoriteRepository stopFavoriteRepository,
                                   GetBusStopByIdUseCase getBusStopByIdUseCase) {
        this.correlationService = correlationService;
        this.stopFavoriteRepository = stopFavoriteRepository;
        this.getBusStopByIdUseCase = getBusStopByIdUseCase;
    }

    @Override
    public Mono<List<StopData>> execute(String clientId) {
        return correlationService.executeWithCorrelation(loadFavoriteStops(clientId), "client");
    }

    private Mono<List<StopData>> loadFavoriteStops(String clientId) {
        return stopFavoriteRepository.findByClientId(ClientId.of(clientId))
                .concatMap(favorite -> getBusStopByIdUseCase
                        .execute(Mono.just(new GetBusStopByIdUseCase.Query(favorite.getStopId().getValue())))
                        .onErrorResume(error -> {
                            log.debug("Favorite stop {} not loadable, skipping: {}",
                                    favorite.getStopId().getValue(), error.toString());
                            return Mono.empty();
                        }))
                .collectList();
    }
}
