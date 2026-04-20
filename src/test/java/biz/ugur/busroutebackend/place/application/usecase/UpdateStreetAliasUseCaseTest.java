package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.UpdateAliasCommand;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.model.StreetAlias;
import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetAliasId;
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
class UpdateStreetAliasUseCaseTest {

    @InjectMocks
    private UpdateStreetAliasUseCase useCase;

    @Mock
    private StreetAliasRepository streetAliasRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void updatesStreetAlias() {
        StreetAlias existing = StreetAlias.create("street-1", "OldName", "ru");

        UpdateAliasCommand cmd = new UpdateAliasCommand(
                existing.getId().getValue(), "NewName", "en");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetAliasRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(streetAliasRepository.save(any(StreetAlias.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> assertEquals("NewName", result.alias()))
                .verifyComplete();
    }

    @Test
    void errorsWhenAliasNotFound() {
        UpdateAliasCommand cmd = new UpdateAliasCommand(
                StreetAliasId.generate().getValue(), "x", "ru");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetAliasRepository.findById(any(StreetAliasId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(PlaceNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
