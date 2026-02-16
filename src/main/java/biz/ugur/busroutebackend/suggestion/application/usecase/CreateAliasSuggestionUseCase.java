package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
import biz.ugur.busroutebackend.suggestion.application.dto.CreateSuggestionInput;
import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionResult;
import biz.ugur.busroutebackend.suggestion.domain.exceptions.DuplicateSuggestionException;
import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateAliasSuggestionUseCase extends BaseUseCase<Mono<CreateSuggestionInput>, SuggestionResult> {

    private final AliasSuggestionRepository suggestionRepository;
    private final PlaceRepository placeRepository;
    private final StreetRepository streetRepository;

    public CreateAliasSuggestionUseCase(AliasSuggestionRepository suggestionRepository,
                                         PlaceRepository placeRepository,
                                         StreetRepository streetRepository,
                                         CorrelationContextService correlationService,
                                         EventBus eventBus) {
        super(correlationService, eventBus);
        this.suggestionRepository = suggestionRepository;
        this.placeRepository = placeRepository;
        this.streetRepository = streetRepository;
    }

    @Override
    protected Mono<SuggestionResult> process(Mono<CreateSuggestionInput> request) {
        return request.flatMap(cmd ->
                verifyEntityExists(cmd.entityType(), cmd.entityId())
                        .then(checkDuplicate(cmd))
                        .then(Mono.defer(() -> {
                            AliasSuggestion suggestion = AliasSuggestion.create(
                                    cmd.entityType(), cmd.entityId(),
                                    cmd.suggestedAlias(), cmd.language(), cmd.clientId()
                            );
                            return suggestionRepository.save(suggestion)
                                    .map(SuggestionResult::fromDomain);
                        }))
        );
    }

    private Mono<Void> verifyEntityExists(SuggestionEntityType entityType, String entityId) {
        return switch (entityType) {
            case PLACE -> placeRepository.existsById(PlaceId.of(entityId))
                    .flatMap(exists -> exists
                            ? Mono.empty()
                            : Mono.error(new PlaceNotFoundException(entityId)));
            case STREET -> streetRepository.existsById(StreetId.of(entityId))
                    .flatMap(exists -> exists
                            ? Mono.empty()
                            : Mono.error(new PlaceNotFoundException("Street", entityId)));
        };
    }

    private Mono<Void> checkDuplicate(CreateSuggestionInput cmd) {
        return suggestionRepository.existsByEntityAndAlias(cmd.entityType(), cmd.entityId(), cmd.suggestedAlias())
                .flatMap(exists -> exists
                        ? Mono.error(new DuplicateSuggestionException(cmd.suggestedAlias()))
                        : Mono.empty());
    }

    @Override
    protected String getBoundContext() {
        return "suggestion";
    }
}
