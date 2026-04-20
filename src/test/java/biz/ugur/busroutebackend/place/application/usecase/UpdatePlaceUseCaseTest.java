package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.UpdatePlaceCommand;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdatePlaceUseCaseTest {

    @InjectMocks
    private UpdatePlaceUseCase useCase;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void updatesPlaceFields() {
        Place existing = Place.create(
                "Old", "Old", "Köne", "desc", "addr",
                PlaceCategory.PARK, "ashgabat",
                new BigDecimal("37.96"), new BigDecimal("58.33"), true);

        UpdatePlaceCommand cmd = new UpdatePlaceCommand(
                existing.getId().getValue(),
                "New Name", "New Name EN", "Täze", "new desc",
                "new address", "MALL", "ashgabat",
                new BigDecimal("37.97"), new BigDecimal("58.34"));

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(placeRepository.save(any(Place.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> assertEquals("New Name", result.name()))
                .verifyComplete();
    }

    @Test
    void errorsWhenPlaceNotFound() {
        UpdatePlaceCommand cmd = new UpdatePlaceCommand(
                PlaceId.generate().getValue(), "x", null, null,
                null, null, null, null, null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeRepository.findById(any(PlaceId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(PlaceNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
