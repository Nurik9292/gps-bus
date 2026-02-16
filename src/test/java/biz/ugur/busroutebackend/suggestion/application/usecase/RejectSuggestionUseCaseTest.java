package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.suggestion.application.dto.ReviewSuggestionInput;
import biz.ugur.busroutebackend.suggestion.domain.exceptions.SuggestionNotFoundException;
import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
import biz.ugur.busroutebackend.suggestion.domain.valueobjects.AliasSuggestionId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RejectSuggestionUseCase Tests")
class RejectSuggestionUseCaseTest {

    @Mock
    private AliasSuggestionRepository suggestionRepository;

    @Mock
    private CorrelationContextService correlationContextService;

    @Mock
    private EventBus eventBus;

    private RejectSuggestionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RejectSuggestionUseCase(suggestionRepository, correlationContextService, eventBus);

        when(correlationContextService.executeWithCorrelation(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Успешное отклонение предложения")
    void rejectSuggestionSuccessfully() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.PLACE, "place-1", "Русский базар", "ru", "client-1");

        when(suggestionRepository.findById(any(AliasSuggestionId.class))).thenReturn(Mono.just(suggestion));
        when(suggestionRepository.save(any(AliasSuggestion.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ReviewSuggestionInput input = new ReviewSuggestionInput(
                suggestion.getId().getValue(), "admin-1", "Некорректное название");

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("REJECTED", result.status());
                    assertEquals("Некорректное название", result.reviewComment());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Отклонение без комментария")
    void rejectSuggestionWithoutComment() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.STREET, "street-1", "Свободы", "ru", "client-1");

        when(suggestionRepository.findById(any(AliasSuggestionId.class))).thenReturn(Mono.just(suggestion));
        when(suggestionRepository.save(any(AliasSuggestion.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ReviewSuggestionInput input = new ReviewSuggestionInput(
                suggestion.getId().getValue(), "admin-1", null);

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("REJECTED", result.status());
                    assertNull(result.reviewComment());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Ошибка если предложение не найдено")
    void rejectSuggestionFailsWhenNotFound() {
        when(suggestionRepository.findById(any(AliasSuggestionId.class))).thenReturn(Mono.empty());

        ReviewSuggestionInput input = new ReviewSuggestionInput("nonexistent", "admin-1", "comment");

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .expectError(SuggestionNotFoundException.class)
                .verify();
    }
}
