package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceAliasId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.place.domain.events.PlaceCatalogChangedEvent;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DeletePlaceAliasUseCase extends BaseUseCase<Mono<String>, Void> {

    private final PlaceAliasRepository placeAliasRepository;

    public DeletePlaceAliasUseCase(PlaceAliasRepository placeAliasRepository,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeAliasRepository = placeAliasRepository;
    }

    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(id -> placeAliasRepository.findById(PlaceAliasId.of(id))
                .flatMap(alias -> placeAliasRepository.deleteById(PlaceAliasId.of(id))
                        .doOnSuccess(v -> eventBus.publish(new PlaceCatalogChangedEvent(alias.getPlaceId())))));
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
