package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.CreateAliasCommand;
import biz.ugur.busroutebackend.place.application.dto.CreatePlaceCommand;
import biz.ugur.busroutebackend.place.application.dto.PlaceResult;
import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreatePlaceUseCase Tests")
class CreatePlaceUseCaseTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceAliasRepository placeAliasRepository;

    @Mock
    private CorrelationContextService correlationContextService;

    @Mock
    private EventBus eventBus;

    private CreatePlaceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreatePlaceUseCase(placeRepository, placeAliasRepository,
                correlationContextService, eventBus);

        when(correlationContextService.executeWithCorrelation(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Создание места без алиасов")
    void createPlaceWithoutAliases() {
        CreatePlaceCommand cmd = new CreatePlaceCommand(
                "Беркарар ТЦ", "Berkarar Mall", "Berkarar", "Торговый центр",
                "пр. Битарап, 78", "MALL", "city-1",
                new BigDecimal("37.95"), new BigDecimal("58.38"), null, null
        );

        when(placeRepository.save(any(Place.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("Беркарар ТЦ", result.name());
                    assertEquals("Berkarar Mall", result.nameEn());
                    assertEquals("MALL", result.category());
                })
                .verifyComplete();

        verify(placeRepository).save(any(Place.class));
        verify(placeAliasRepository, never()).save(any(PlaceAlias.class));
    }

    @Test
    @DisplayName("Создание места с алиасами")
    void createPlaceWithAliases() {
        List<CreateAliasCommand> aliases = List.of(
                new CreateAliasCommand("Беркарар", "ru"),
                new CreateAliasCommand("Berkarar", "en")
        );

        CreatePlaceCommand cmd = new CreatePlaceCommand(
                "Беркарар ТЦ", "Berkarar Mall", null, null,
                null, "MALL", "city-1",
                new BigDecimal("37.95"), new BigDecimal("58.38"), null, aliases
        );

        when(placeRepository.save(any(Place.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(placeAliasRepository.save(any(PlaceAlias.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("Беркарар ТЦ", result.name());
                    assertNotNull(result.aliases());
                    assertEquals(2, result.aliases().size());
                })
                .verifyComplete();

        verify(placeRepository).save(any(Place.class));
        verify(placeAliasRepository, times(2)).save(any(PlaceAlias.class));
    }
}
