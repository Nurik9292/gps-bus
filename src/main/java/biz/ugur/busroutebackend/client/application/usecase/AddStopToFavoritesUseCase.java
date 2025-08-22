package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class AddStopToFavoritesUseCase extends BaseUseCase<Mono<AddStopToFavoritesUseCase.Command>, Boolean> {

    private final StopFavoriteRepository stopFavoriteRepository;

    public AddStopToFavoritesUseCase(StopFavoriteRepository stopFavoriteRepository,
                                     CorrelationContextService correlationContextService,
                                     EventBus  eventBus) {
        super(correlationContextService, eventBus);
        this.stopFavoriteRepository = stopFavoriteRepository;
    }


    @Override
    protected Mono<Boolean> process(Mono<Command> command) {
        return command.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }

    private Mono<Boolean> processInternal(Command command) {
        ClientId clientId = ClientId.of(command.clientId());
        BusStopId stopId = BusStopId.of(command.stopId());

        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Add stop to favorites CorrelationId: {} - CleintId: {} - StopId: {}",
                    correlationId, clientId, stopId);

            return stopFavoriteRepository.existsByClientIdAndStopId(clientId, stopId)
                    .flatMap(exists -> {
                        if (exists) {
                            return stopFavoriteRepository.deleteByClientIdAndStopId(clientId, stopId)
                                    .thenReturn(false);
                        } else {
                            StopFavorite favorite = StopFavorite.create(clientId, stopId);
                            return stopFavoriteRepository.save(favorite)
                                    .thenReturn(true);
                        }
                    });
        });
    }

    public record Command(String clientId, String stopId) {}
}