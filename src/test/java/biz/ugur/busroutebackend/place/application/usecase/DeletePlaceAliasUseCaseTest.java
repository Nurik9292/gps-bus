package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.events.PlaceCatalogChangedEvent;
import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceAliasId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class DeletePlaceAliasUseCaseTest {

    @InjectMocks
    private DeletePlaceAliasUseCase useCase;

    @Mock
    private PlaceAliasRepository placeAliasRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deletesAliasByIdAndPublishesPlaceCatalogChange() {
        PlaceAliasId id = PlaceAliasId.generate();
        PlaceAlias alias = PlaceAlias.create("place-9", "Ориентир", "ru");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeAliasRepository.findById(id)).thenReturn(Mono.just(alias));
        when(placeAliasRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id.getValue())))
                .verifyComplete();

        verify(placeAliasRepository).deleteById(id);
        verify(eventBus).publish(new PlaceCatalogChangedEvent("place-9"));
    }

    @Test
    void missingAliasCompletesWithoutDeletionOrEvent() {
        PlaceAliasId id = PlaceAliasId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeAliasRepository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id.getValue())))
                .verifyComplete();

        verify(placeAliasRepository, never()).deleteById(any());
        verify(eventBus, never()).publish(any(DomainEvent.class));
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
