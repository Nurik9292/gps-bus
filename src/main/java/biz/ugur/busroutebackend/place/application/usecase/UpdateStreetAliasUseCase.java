package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.StreetAliasResult;
import biz.ugur.busroutebackend.place.application.dto.UpdateAliasCommand;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetAliasId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UpdateStreetAliasUseCase extends BaseUseCase<Mono<UpdateAliasCommand>, StreetAliasResult> {

    private final StreetAliasRepository streetAliasRepository;

    public UpdateStreetAliasUseCase(StreetAliasRepository streetAliasRepository,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetAliasRepository = streetAliasRepository;
    }

    @Override
    protected Mono<StreetAliasResult> process(Mono<UpdateAliasCommand> request) {
        return request.flatMap(cmd ->
                streetAliasRepository.findById(StreetAliasId.of(cmd.id()))
                        .switchIfEmpty(Mono.error(new PlaceNotFoundException("StreetAlias", cmd.id())))
                        .flatMap(existing -> {
                            var updated = existing.updateAlias(cmd.alias(), cmd.language());
                            return streetAliasRepository.save(updated);
                        })
                        .map(StreetAliasResult::fromDomain)
        );
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
