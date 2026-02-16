package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
import biz.ugur.busroutebackend.suggestion.application.dto.GetAllSuggestionsInput;
import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionList;
import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionResult;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllSuggestionsUseCase extends BaseUseCase<Mono<GetAllSuggestionsInput>, SuggestionList> {

    private final AliasSuggestionRepository suggestionRepository;
    private final PlaceRepository placeRepository;
    private final StreetRepository streetRepository;

    public GetAllSuggestionsUseCase(AliasSuggestionRepository suggestionRepository,
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
    protected Mono<SuggestionList> process(Mono<GetAllSuggestionsInput> request) {
        return request.flatMap(this::processInternal);
    }

    private Mono<SuggestionList> processInternal(GetAllSuggestionsInput input) {
        Pageable pageable = createPageable(input);

        return suggestionRepository.findByFilters(input.getStatus(), input.getEntityType(), pageable)
                .map(SuggestionResult::fromDomain)
                .collectList()
                .flatMap(this::enrichWithEntityNames)
                .zipWith(suggestionRepository.countByFilters(input.getStatus(), input.getEntityType()))
                .zipWith(suggestionRepository.count())
                .map(tuple -> {
                    List<SuggestionResult> items = tuple.getT1().getT1();
                    long totalFiltered = tuple.getT1().getT2();
                    long totalCount = tuple.getT2();
                    return new SuggestionList(items, totalCount, input.getPage(), input.getSize(), totalFiltered);
                });
    }

    private Mono<List<SuggestionResult>> enrichWithEntityNames(List<SuggestionResult> results) {
        return Mono.just(results)
                .flatMap(list -> {
                    var enriched = list.stream()
                            .map(result -> enrichEntityName(result).defaultIfEmpty(result))
                            .toList();
                    return Mono.zip(enriched, objects -> {
                        @SuppressWarnings("unchecked")
                        var enrichedList = java.util.Arrays.stream(objects)
                                .map(o -> (SuggestionResult) o)
                                .toList();
                        return enrichedList;
                    });
                })
                .defaultIfEmpty(results);
    }

    private Mono<SuggestionResult> enrichEntityName(SuggestionResult result) {
        if (SuggestionEntityType.PLACE.name().equals(result.entityType())) {
            return placeRepository.findById(PlaceId.of(result.entityId()))
                    .map(place -> result.withEntityName(place.getName()))
                    .defaultIfEmpty(result);
        } else if (SuggestionEntityType.STREET.name().equals(result.entityType())) {
            return streetRepository.findById(StreetId.of(result.entityId()))
                    .map(street -> result.withEntityName(street.getName()))
                    .defaultIfEmpty(result);
        }
        return Mono.just(result);
    }

    private Pageable createPageable(GetAllSuggestionsInput input) {
        Sort.Direction direction = "desc".equalsIgnoreCase(input.getOrder())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, input.getSort() != null ? input.getSort() : "created_at");
        return PageRequest.of(input.getPage() - 1, input.getSize(), sort);
    }

    @Override
    protected String getBoundContext() {
        return "suggestion";
    }
}
