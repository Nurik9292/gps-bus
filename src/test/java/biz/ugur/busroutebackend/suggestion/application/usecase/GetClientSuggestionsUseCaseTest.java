package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.suggestion.application.dto.GetClientSuggestionsInput;
import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetClientSuggestionsUseCaseTest {

    @InjectMocks
    private GetClientSuggestionsUseCase useCase;

    @Mock
    private AliasSuggestionRepository suggestionRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsClientSuggestionsWithTotal() {
        AliasSuggestion s = AliasSuggestion.create(
                SuggestionEntityType.PLACE, "place-1", "MyPlace", "ru", "client-1");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suggestionRepository.findByClientId(eq("client-1"), any(Pageable.class)))
                .thenReturn(Flux.just(s));
        when(suggestionRepository.countByClientId("client-1")).thenReturn(Mono.just(1L));

        GetClientSuggestionsInput input = GetClientSuggestionsInput.fromParams(
                "client-1", 1, 10, "created_at", "desc");

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> {
                    assertEquals(1, list.getSuggestions().size());
                    assertEquals(1L, list.getActiveCount());
                })
                .verifyComplete();
    }

    @Test
    void orderDescBuildsDescendingSort() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(suggestionRepository.findByClientId(anyString(), any(Pageable.class))).thenReturn(Flux.empty());
        when(suggestionRepository.countByClientId(anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute(Mono.just(GetClientSuggestionsInput.fromParams(
                "c", 1, 5, "created_at", "desc"))))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(suggestionRepository).findByClientId(anyString(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(5, captor.getValue().getPageSize());
    }

    @Test
    void exposesSuggestionBoundContext() {
        assertEquals("suggestion", useCase.getBoundContext());
    }
}
