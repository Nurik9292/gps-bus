package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
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
@DisplayName("ApproveSuggestionUseCase Tests")
class ApproveSuggestionUseCaseTest {

    @Mock
    private AliasSuggestionRepository suggestionRepository;

    @Mock
    private PlaceAliasRepository placeAliasRepository;

    @Mock
    private StreetAliasRepository streetAliasRepository;

    @Mock
    private CorrelationContextService correlationContextService;

    @Mock
    private EventBus eventBus;

    private ApproveSuggestionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ApproveSuggestionUseCase(suggestionRepository, placeAliasRepository,
                streetAliasRepository, correlationContextService, eventBus);

        when(correlationContextService.executeWithCorrelation(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Успешное одобрение предложения для места")
    void approveSuggestionSuccessfully() {
        AliasSuggestion suggestion = AliasSuggestion.create(
                SuggestionEntityType.PLACE, "place-1", "Русский базар", "ru", "client-1");

        when(suggestionRepository.findById(any(AliasSuggestionId.class))).thenReturn(Mono.just(suggestion));
        when(suggestionRepository.save(any(AliasSuggestion.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(placeAliasRepository.save(any(PlaceAlias.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ReviewSuggestionInput input = new ReviewSuggestionInput(
                suggestion.getId().getValue(), "admin-1", null);

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("APPROVED", result.status());
                })
                .verifyComplete();

        verify(placeAliasRepository).save(any(PlaceAlias.class));
    }

    @Test
    @DisplayName("Ошибка если предложение не найдено")
    void approveSuggestionFailsWhenNotFound() {
        when(suggestionRepository.findById(any(AliasSuggestionId.class))).thenReturn(Mono.empty());

        ReviewSuggestionInput input = new ReviewSuggestionInput("nonexistent", "admin-1", null);

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .expectError(SuggestionNotFoundException.class)
                .verify();
    }
}
