package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.place.domain.events.PlaceCatalogChangedEvent;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeletePlaceUseCase extends BaseUseCase<Mono<String>, Void> {

    private final PlaceRepository placeRepository;

    public DeletePlaceUseCase(PlaceRepository placeRepository,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeRepository = placeRepository;
    }

    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<Void> processInternal(String placeId) {
        return placeRepository.findById(PlaceId.of(placeId))
                .switchIfEmpty(Mono.error(new PlaceNotFoundException(placeId)))
                .flatMap(place -> placeRepository.deleteById(place.getId()))
                .doOnSuccess(v -> log.info("Place deleted: {}", placeId))
                .doOnSuccess(v -> eventBus.publish(new PlaceCatalogChangedEvent(placeId)));
    }
}
