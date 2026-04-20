package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetPlaceByIdUseCaseTest {

    @InjectMocks
    private GetPlaceByIdUseCase useCase;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceAliasRepository placeAliasRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsPlaceWithEmptyAliasesWhenFound() {
        Place place = Place.create(
                "Central", "Central", "Merkez",
                "desc", "Garaşsyzlyk 1",
                PlaceCategory.EDUCATION, "ashgabat",
                new BigDecimal("37.96"), new BigDecimal("58.33"),
                true
        );

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeRepository.findById(place.getId())).thenReturn(Mono.just(place));
        when(placeAliasRepository.findByPlaceId(place.getId().getValue())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(Mono.just(place.getId().getValue())))
                .assertNext(result -> {
                    assertEquals("Central", result.name());
                    assertEquals(0, result.aliases().size());
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenPlaceNotFound() {
        String id = PlaceId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeRepository.findById(any(PlaceId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id)))
                .expectErrorSatisfies(err -> assertInstanceOf(PlaceNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
