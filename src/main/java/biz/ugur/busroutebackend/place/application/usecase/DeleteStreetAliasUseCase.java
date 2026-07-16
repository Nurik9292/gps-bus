package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetAliasId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.place.domain.events.StreetCatalogChangedEvent;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DeleteStreetAliasUseCase extends BaseUseCase<Mono<String>, Void> {

    private final StreetAliasRepository streetAliasRepository;

    public DeleteStreetAliasUseCase(StreetAliasRepository streetAliasRepository,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetAliasRepository = streetAliasRepository;
    }

    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(id -> streetAliasRepository.findById(StreetAliasId.of(id))
                .flatMap(alias -> streetAliasRepository.deleteById(StreetAliasId.of(id))
                        .doOnSuccess(v -> eventBus.publish(new StreetCatalogChangedEvent(alias.getStreetId())))));
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
