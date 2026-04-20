package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.UpdateAliasCommand;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceAliasId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdatePlaceAliasUseCaseTest {

    @InjectMocks
    private UpdatePlaceAliasUseCase useCase;

    @Mock
    private PlaceAliasRepository placeAliasRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void updatesAliasAndLanguage() {
        PlaceAlias existing = PlaceAlias.create("place-1", "OldAlias", "ru");

        UpdateAliasCommand cmd = new UpdateAliasCommand(
                existing.getId().getValue(), "NewAlias", "en");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeAliasRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(placeAliasRepository.save(any(PlaceAlias.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> {
                    assertEquals("NewAlias", result.alias());
                    assertEquals("en", result.language());
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenAliasNotFound() {
        UpdateAliasCommand cmd = new UpdateAliasCommand(
                PlaceAliasId.generate().getValue(), "x", "ru");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeAliasRepository.findById(any(PlaceAliasId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(PlaceNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
