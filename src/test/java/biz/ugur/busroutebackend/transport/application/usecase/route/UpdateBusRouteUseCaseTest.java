package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.dto.route.UpdateRoute;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateBusRouteUseCaseTest {

    @InjectMocks
    private UpdateBusRouteUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private biz.ugur.busroutebackend.transport.application.services.NameHistoryRecorder nameHistoryRecorder;

    @org.junit.jupiter.api.BeforeEach
    void stubNameHistory() {
        org.mockito.Mockito.lenient().when(nameHistoryRecorder.recordRouteChanges(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(reactor.core.publisher.Mono.empty());
    }

    @Mock
    private RouteStopsService routeStopsService;

    @Mock
    private RouteDataMapper routeDataMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void updatesExistingRoute() {
        BusRoute route = BusRoute.create("29A", "Main", "", "", "#FF5722", "ashgabat", 30);
        UpdateRoute cmd = new UpdateRoute(
                route.getId().getValue(), "29B", "Updated", "", "",
                "#00FF00", 40, true, "ashgabat",
                List.of(), List.of(), null, null);
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(route.getId())).thenReturn(Mono.just(route));
        when(busRouteRepository.save(any(BusRoute.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(routeStopsService.updateRouteStops(anyString(), anyList(), anyList())).thenReturn(Mono.empty());
        when(routeDataMapper.toRouteData(any(BusRoute.class))).thenReturn(Mono.just(data));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNext(data)
                .verifyComplete();
    }

    @Test
    void deactivatesRouteWhenIsActiveFalse() {
        BusRoute route = BusRoute.create("29A", "Main", "", "", "#FF5722", "ashgabat", 30);
        UpdateRoute cmd = new UpdateRoute(
                route.getId().getValue(), "29A", "Main", "", "",
                "#FF5722", 30, false, "ashgabat",
                List.of(), List.of(), null, null);
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(route.getId())).thenReturn(Mono.just(route));
        when(busRouteRepository.save(any(BusRoute.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(routeStopsService.updateRouteStops(anyString(), anyList(), anyList())).thenReturn(Mono.empty());
        when(routeDataMapper.toRouteData(any(BusRoute.class))).thenReturn(Mono.just(data));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNext(data)
                .verifyComplete();
    }

    @Test
    void errorsWhenRouteNotFound() {
        UpdateRoute cmd = new UpdateRoute(
                BusRouteId.generate().getValue(), "29A", "Main", "", "",
                "#FF5722", 30, true, "ashgabat",
                List.of(), List.of(), null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
