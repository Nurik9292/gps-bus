package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.suggestion.application.dto.CreateSuggestionInput;
import biz.ugur.busroutebackend.suggestion.domain.exceptions.DuplicateSuggestionException;
import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionStatus;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
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
@DisplayName("CreateAliasSuggestionUseCase Tests")
class CreateAliasSuggestionUseCaseTest {

    @Mock
    private AliasSuggestionRepository suggestionRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private StreetRepository streetRepository;

    @Mock
    private CorrelationContextService correlationContextService;

    @Mock
    private EventBus eventBus;

    private CreateAliasSuggestionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateAliasSuggestionUseCase(suggestionRepository, placeRepository,
                streetRepository, correlationContextService, eventBus);

        when(correlationContextService.executeWithCorrelation(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Успешное создание предложения для места")
    void createSuggestionForPlaceSuccessfully() {
        CreateSuggestionInput input = new CreateSuggestionInput(
                SuggestionEntityType.PLACE, "place-1", "Русский базар", "ru", "client-1");

        when(placeRepository.existsById(any(PlaceId.class))).thenReturn(Mono.just(true));
        when(suggestionRepository.existsByEntityAndAlias(any(), any(), any())).thenReturn(Mono.just(false));
        when(suggestionRepository.save(any(AliasSuggestion.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("PLACE", result.entityType());
                    assertEquals("place-1", result.entityId());
                    assertEquals("Русский базар", result.suggestedAlias());
                    assertEquals("PENDING", result.status());
                })
                .verifyComplete();

        verify(suggestionRepository).save(any(AliasSuggestion.class));
    }

    @Test
    @DisplayName("Ошибка при дубликате предложения")
    void createSuggestionFailsWhenDuplicate() {
        CreateSuggestionInput input = new CreateSuggestionInput(
                SuggestionEntityType.PLACE, "place-1", "Русский базар", "ru", "client-1");

        when(placeRepository.existsById(any(PlaceId.class))).thenReturn(Mono.just(true));
        when(suggestionRepository.existsByEntityAndAlias(any(), any(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .expectError(DuplicateSuggestionException.class)
                .verify();
    }

    @Test
    @DisplayName("Ошибка если место не найдено")
    void createSuggestionFailsWhenEntityNotFound() {
        CreateSuggestionInput input = new CreateSuggestionInput(
                SuggestionEntityType.PLACE, "nonexistent", "Алиас", "ru", "client-1");

        when(placeRepository.existsById(any(PlaceId.class))).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .expectError()
                .verify();
    }
}
