package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.AliasResult;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetPlaceAliasesUseCase extends BaseUseCase<Mono<String>, List<AliasResult>> {

    private final PlaceAliasRepository placeAliasRepository;

    public GetPlaceAliasesUseCase(PlaceAliasRepository placeAliasRepository,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeAliasRepository = placeAliasRepository;
    }

    @Override
    protected Mono<List<AliasResult>> process(Mono<String> request) {
        return request.flatMap(placeId ->
                placeAliasRepository.findByPlaceId(placeId)
                        .map(AliasResult::fromDomain)
                        .collectList()
        );
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
