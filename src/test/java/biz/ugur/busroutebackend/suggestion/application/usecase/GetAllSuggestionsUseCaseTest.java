package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.suggestion.application.dto.GetAllSuggestionsInput;
import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionStatus;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllSuggestionsUseCaseTest {

    @InjectMocks
    private GetAllSuggestionsUseCase useCase;

    @Mock
    private AliasSuggestionRepository suggestionRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private StreetRepository streetRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsEmptyListWhenNoSuggestions() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suggestionRepository.findByFilters(any(), any(), any(Pageable.class)))
                .thenReturn(Flux.empty());
        when(suggestionRepository.countByFilters(any(), any())).thenReturn(Mono.just(0L));
        when(suggestionRepository.count()).thenReturn(Mono.just(0L));

        GetAllSuggestionsInput input = GetAllSuggestionsInput.fromParams(1, 10, null, null, null, null);

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> assertEquals(0, list.getSuggestions().size()))
                .verifyComplete();
    }

    @Test
    void returnsSuggestionsFromRepository() {
        AliasSuggestion s = AliasSuggestion.create(
                SuggestionEntityType.STREET, "street-1", "Alt Name", "ru", "client-1");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suggestionRepository.findByFilters(any(), any(), any(Pageable.class)))
                .thenReturn(Flux.just(s));
        when(suggestionRepository.countByFilters(any(), any())).thenReturn(Mono.just(1L));
        when(suggestionRepository.count()).thenReturn(Mono.just(1L));
        when(streetRepository.findById(any())).thenReturn(Mono.empty());

        GetAllSuggestionsInput input = GetAllSuggestionsInput.fromParams(
                1, 10, "created_at", "desc", "PENDING", "STREET");

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> assertEquals(1, list.getSuggestions().size()))
                .verifyComplete();
    }

    @Test
    void exposesSuggestionBoundContext() {
        assertEquals("suggestion", useCase.getBoundContext());
    }
}
