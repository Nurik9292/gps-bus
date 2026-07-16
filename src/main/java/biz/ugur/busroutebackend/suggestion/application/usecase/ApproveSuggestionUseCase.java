package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.model.StreetAlias;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.suggestion.application.dto.ReviewSuggestionInput;
import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionResult;
import biz.ugur.busroutebackend.suggestion.domain.exceptions.SuggestionNotFoundException;
import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
import biz.ugur.busroutebackend.suggestion.domain.valueobjects.AliasSuggestionId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.place.domain.events.PlaceCatalogChangedEvent;
import biz.ugur.busroutebackend.place.domain.events.StreetCatalogChangedEvent;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ApproveSuggestionUseCase extends BaseUseCase<Mono<ReviewSuggestionInput>, SuggestionResult> {

    private final AliasSuggestionRepository suggestionRepository;
    private final PlaceAliasRepository placeAliasRepository;
    private final StreetAliasRepository streetAliasRepository;

    public ApproveSuggestionUseCase(AliasSuggestionRepository suggestionRepository,
                                     PlaceAliasRepository placeAliasRepository,
                                     StreetAliasRepository streetAliasRepository,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.suggestionRepository = suggestionRepository;
        this.placeAliasRepository = placeAliasRepository;
        this.streetAliasRepository = streetAliasRepository;
    }

    @Override
    protected Mono<SuggestionResult> process(Mono<ReviewSuggestionInput> request) {
        return request.flatMap(cmd ->
                suggestionRepository.findById(AliasSuggestionId.of(cmd.suggestionId()))
                        .switchIfEmpty(Mono.error(new SuggestionNotFoundException(cmd.suggestionId())))
                        .flatMap(suggestion -> {
                            AliasSuggestion approved = suggestion.approve(cmd.reviewerId());
                            return suggestionRepository.save(approved)
                                    .flatMap(saved -> createAlias(saved).thenReturn(saved))
                                    .map(SuggestionResult::fromDomain);
                        })
        );
    }

    private Mono<Void> createAlias(AliasSuggestion suggestion) {
        if (suggestion.getEntityType() == SuggestionEntityType.PLACE) {
            PlaceAlias alias = PlaceAlias.create(
                    suggestion.getEntityId(), suggestion.getSuggestedAlias(), suggestion.getLanguage());
            return placeAliasRepository.save(alias)
                    .doOnNext(saved -> eventBus.publish(new PlaceCatalogChangedEvent(saved.getPlaceId())))
                    .then();
        } else {
            StreetAlias alias = StreetAlias.create(
                    suggestion.getEntityId(), suggestion.getSuggestedAlias(), suggestion.getLanguage());
            return streetAliasRepository.save(alias)
                    .doOnNext(saved -> eventBus.publish(new StreetCatalogChangedEvent(saved.getStreetId())))
                    .then();
        }
    }

    @Override
    protected String getBoundContext() {
        return "suggestion";
    }
}
