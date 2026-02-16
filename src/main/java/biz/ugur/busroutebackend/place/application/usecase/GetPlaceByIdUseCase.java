package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.AliasResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceResult;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetPlaceByIdUseCase extends BaseUseCase<Mono<String>, PlaceResult> {

    private final PlaceRepository placeRepository;
    private final PlaceAliasRepository placeAliasRepository;

    public GetPlaceByIdUseCase(PlaceRepository placeRepository,
                               PlaceAliasRepository placeAliasRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeRepository = placeRepository;
        this.placeAliasRepository = placeAliasRepository;
    }

    @Override
    protected Mono<PlaceResult> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<PlaceResult> processInternal(String placeId) {
        return placeRepository.findById(PlaceId.of(placeId))
                .switchIfEmpty(Mono.error(new PlaceNotFoundException(placeId)))
                .flatMap(place -> placeAliasRepository.findByPlaceId(placeId)
                        .map(AliasResult::fromDomain)
                        .collectList()
                        .map(aliases -> PlaceResult.fromDomain(place, aliases))
                );
    }
}
