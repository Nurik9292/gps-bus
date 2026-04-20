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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateRouteAlternativeUseCaseTest {

    @InjectMocks
    private CreateRouteAlternativeUseCase useCase;

    @Mock
    private RouteAlternativeRepository routeAlternativeRepository;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsAlternativeWhenRoutesExistAndLinkIsNew() {
        BusRoute primary = BusRoute.create("1", "Main", "", "", "#FF0000", "ashgabat", 30);
        BusRoute alt = BusRoute.create("2", "Alt", "", "", "#00FF00", "ashgabat", 30);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(primary.getId())).thenReturn(Mono.just(primary));
        when(busRouteRepository.findById(alt.getId())).thenReturn(Mono.just(alt));
        when(routeAlternativeRepository.existsByPrimaryAndAlternative(primary.getId(), alt.getId()))
                .thenReturn(Mono.just(false));
        when(routeAlternativeRepository.save(any(RouteAlternative.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(new CreateRouteAlternativeUseCase.Request(
                primary.getId().getValue(), alt.getId().getValue(),
                1, "fallback", null, null)))
                .assertNext(dto -> {
                    assertEquals("1", dto.primaryRouteNumber());
                    assertEquals("2", dto.alternativeRouteNumber());
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenLinkAlreadyExists() {
        BusRoute primary = BusRoute.create("1", "Main", "", "", "#FF0000", "ashgabat", 30);
        BusRoute alt = BusRoute.create("2", "Alt", "", "", "#00FF00", "ashgabat", 30);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(primary.getId())).thenReturn(Mono.just(primary));
        when(busRouteRepository.findById(alt.getId())).thenReturn(Mono.just(alt));
        when(routeAlternativeRepository.existsByPrimaryAndAlternative(primary.getId(), alt.getId()))
                .thenReturn(Mono.just(true));
        when(routeAlternativeRepository.save(any(RouteAlternative.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(new CreateRouteAlternativeUseCase.Request(
                primary.getId().getValue(), alt.getId().getValue(),
                1, null, null, null)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalStateException.class, err))
                .verify();
    }

    @Test
    void errorsWhenPrimaryRouteNotFound() {
        BusRouteId primaryId = BusRouteId.generate();
        BusRouteId altId = BusRouteId.generate();
        BusRoute alt = BusRoute.create("2", "Alt", "", "", "#00FF00", "ashgabat", 30);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(primaryId)).thenReturn(Mono.empty());
        when(busRouteRepository.findById(altId)).thenReturn(Mono.just(alt));
        when(routeAlternativeRepository.existsByPrimaryAndAlternative(any(BusRouteId.class), any(BusRouteId.class)))
                .thenReturn(Mono.just(false));
        when(routeAlternativeRepository.save(any(RouteAlternative.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(new CreateRouteAlternativeUseCase.Request(
                primaryId.getValue(), altId.getValue(),
                1, null, null, null)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
