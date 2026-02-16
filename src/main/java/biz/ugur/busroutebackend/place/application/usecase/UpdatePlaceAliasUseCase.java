package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.AliasResult;
import biz.ugur.busroutebackend.place.application.dto.UpdateAliasCommand;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceAliasId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UpdatePlaceAliasUseCase extends BaseUseCase<Mono<UpdateAliasCommand>, AliasResult> {

    private final PlaceAliasRepository placeAliasRepository;

    public UpdatePlaceAliasUseCase(PlaceAliasRepository placeAliasRepository,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeAliasRepository = placeAliasRepository;
    }

    @Override
    protected Mono<AliasResult> process(Mono<UpdateAliasCommand> request) {
        return request.flatMap(cmd ->
                placeAliasRepository.findById(PlaceAliasId.of(cmd.id()))
                        .switchIfEmpty(Mono.error(new PlaceNotFoundException("PlaceAlias", cmd.id())))
                        .flatMap(existing -> {
                            var updated = existing.updateAlias(cmd.alias(), cmd.language());
                            return placeAliasRepository.save(updated);
                        })
                        .map(AliasResult::fromDomain)
        );
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
