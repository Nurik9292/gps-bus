package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.UpdateStreetCommand;
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
class UpdateStreetUseCaseTest {

    @InjectMocks
    private UpdateStreetUseCase useCase;

    @Mock
    private StreetRepository streetRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void updatesExistingStreetInfo() {
        Street existing = Street.create("Lenina", "Lenin St.", "Lenin köç.", "city-1");
        UpdateStreetCommand cmd = new UpdateStreetCommand(
                existing.getId().getValue(), "Nezavisimosti", "Independence", "Garaşsyzlyk", "city-2");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(streetRepository.save(any(Street.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> {
                    assertEquals("Nezavisimosti", result.name());
                    assertEquals("Independence", result.nameEn());
                    assertEquals("city-2", result.cityId());
                })
                .verifyComplete();

        verify(streetRepository).save(any(Street.class));
    }

    @Test
    void errorsWhenStreetNotFound() {
        UpdateStreetCommand cmd = new UpdateStreetCommand(
                StreetId.generate().getValue(), "name", "en", "tm", "city");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetRepository.findById(any(StreetId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(PlaceNotFoundException.class, err))
                .verify();

        verify(streetRepository, never()).save(any());
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
