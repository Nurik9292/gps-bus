package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.model.Street;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllStreetsUseCaseTest {

    @InjectMocks
    private GetAllStreetsUseCase useCase;

    @Mock
    private StreetRepository streetRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsAllStreetsWhenNoCityFilter() {
        Street street = Street.create("Lenin", "Lenin St.", "Lenin köç.", "ashgabat");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(street));
        when(streetRepository.count()).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute(Mono.just(
                GetAllStreetsUseCase.Input.fromParams(1, 10, null, null, null))))
                .assertNext(list -> assertEquals(1, list.getStreets().size()))
                .verifyComplete();
    }

    @Test
    void filtersByCityWhenGiven() {
        Street street = Street.create("Nezavisimosti", "Independence", "Garaşsyzlyk", "ashgabat");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetRepository.findByCityId(anyString(), any(Pageable.class))).thenReturn(Flux.just(street));
        when(streetRepository.countByCityId("ashgabat")).thenReturn(Mono.just(1L));
        when(streetRepository.count()).thenReturn(Mono.just(10L));

        StepVerifier.create(useCase.execute(Mono.just(
                GetAllStreetsUseCase.Input.fromParams(1, 10, "name", "asc", "ashgabat"))))
                .assertNext(list -> assertEquals(1, list.getStreets().size()))
                .verifyComplete();
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
