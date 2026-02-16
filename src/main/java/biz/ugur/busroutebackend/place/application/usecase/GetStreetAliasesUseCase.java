package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.StreetAliasResult;
import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetStreetAliasesUseCase extends BaseUseCase<Mono<String>, List<StreetAliasResult>> {

    private final StreetAliasRepository streetAliasRepository;

    public GetStreetAliasesUseCase(StreetAliasRepository streetAliasRepository,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetAliasRepository = streetAliasRepository;
    }

    @Override
    protected Mono<List<StreetAliasResult>> process(Mono<String> request) {
        return request.flatMap(streetId ->
                streetAliasRepository.findByStreetId(streetId)
                        .map(StreetAliasResult::fromDomain)
                        .collectList()
        );
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
