package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.AliasResult;
import biz.ugur.busroutebackend.place.application.dto.CreatePlaceAliasInput;
import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.place.domain.events.PlaceCatalogChangedEvent;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreatePlaceAliasUseCase extends BaseUseCase<Mono<CreatePlaceAliasInput>, AliasResult> {

    private final PlaceAliasRepository placeAliasRepository;

    public CreatePlaceAliasUseCase(PlaceAliasRepository placeAliasRepository,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeAliasRepository = placeAliasRepository;
    }

    @Override
    protected Mono<AliasResult> process(Mono<CreatePlaceAliasInput> request) {
        return request.flatMap(cmd -> {
            PlaceAlias alias = PlaceAlias.create(cmd.placeId(), cmd.alias(), cmd.language());
            return placeAliasRepository.save(alias)
                    .doOnNext(saved -> eventBus.publish(new PlaceCatalogChangedEvent(saved.getPlaceId())))
                    .map(AliasResult::fromDomain);
        });
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
