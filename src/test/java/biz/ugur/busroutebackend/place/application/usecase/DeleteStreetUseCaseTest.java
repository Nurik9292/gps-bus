package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.model.Street;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class DeleteStreetUseCaseTest {

    @InjectMocks
    private DeleteStreetUseCase useCase;

    @Mock
    private StreetRepository streetRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deletesExistingStreet() {
        Street existing = Street.create("Lenina", "Lenin St.", "Lenin köç.", "city-1");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(streetRepository.deleteById(existing.getId())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(existing.getId().getValue())))
                .verifyComplete();

        verify(streetRepository).deleteById(existing.getId());
    }

    @Test
    void errorsWhenStreetNotFound() {
        String id = StreetId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetRepository.findById(any(StreetId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id)))
                .expectErrorSatisfies(err -> assertInstanceOf(PlaceNotFoundException.class, err))
                .verify();

        verify(streetRepository, never()).deleteById(any(StreetId.class));
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
