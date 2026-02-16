package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.StreetAliasResult;
import biz.ugur.busroutebackend.place.domain.model.StreetAlias;
import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CreateStreetAliasUseCase extends BaseUseCase<Mono<CreateStreetAliasUseCase.Input>, StreetAliasResult> {

    private final StreetAliasRepository streetAliasRepository;

    public CreateStreetAliasUseCase(StreetAliasRepository streetAliasRepository,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetAliasRepository = streetAliasRepository;
    }

    @Override
    protected Mono<StreetAliasResult> process(Mono<Input> request) {
        return request.flatMap(cmd -> {
            StreetAlias alias = StreetAlias.create(cmd.streetId(), cmd.alias(), cmd.language());
            return streetAliasRepository.save(alias).map(StreetAliasResult::fromDomain);
        });
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    public record Input(String streetId, String alias, String language) {}
}
