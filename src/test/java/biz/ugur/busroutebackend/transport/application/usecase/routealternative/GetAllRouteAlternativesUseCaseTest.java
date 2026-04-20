package biz.ugur.busroutebackend.transport.application.usecase.routealternative;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllRouteAlternativesUseCaseTest {

    @InjectMocks
    private GetAllRouteAlternativesUseCase useCase;

    @Mock
    private RouteAlternativeRepository routeAlternativeRepository;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsEnrichedAlternatives() {
        BusRouteId primaryId = BusRouteId.generate();
        BusRouteId altId = BusRouteId.generate();
        BusRoute primary = BusRoute.create("1", "Main", "", "", "#FF0000", "ashgabat", 30);
        BusRoute alternative = BusRoute.create("2", "Alt", "", "", "#00FF00", "ashgabat", 30);

        RouteAlternative alt = RouteAlternative.create(
                primaryId, altId, 1, "fallback", null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(routeAlternativeRepository.findAll()).thenReturn(Flux.just(alt));
        when(busRouteRepository.findById(primaryId)).thenReturn(Mono.just(primary));
        when(busRouteRepository.findById(altId)).thenReturn(Mono.just(alternative));

        StepVerifier.create(useCase.execute(null))
                .assertNext(dto -> {
                    assertEquals(alt.getId().getValue(), dto.id());
                    assertEquals("1", dto.primaryRouteNumber());
                    assertEquals("2", dto.alternativeRouteNumber());
                })
                .verifyComplete();
    }

    @Test
    void usesFallbackRouteWhenRouteMissing() {
        BusRouteId primaryId = BusRouteId.generate();
        BusRouteId altId = BusRouteId.generate();
        RouteAlternative alt = RouteAlternative.create(
                primaryId, altId, 1, null, null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(routeAlternativeRepository.findAll()).thenReturn(Flux.just(alt));
        when(busRouteRepository.findById(primaryId)).thenReturn(Mono.empty());
        when(busRouteRepository.findById(altId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(null))
                .assertNext(dto -> assertEquals("?", dto.primaryRouteNumber()))
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenNoAlternatives() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(routeAlternativeRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(null))
                .verifyComplete();
    }
}
