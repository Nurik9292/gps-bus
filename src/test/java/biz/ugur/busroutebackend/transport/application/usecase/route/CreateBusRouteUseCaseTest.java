package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.route.CreateRoute;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateBusRouteUseCaseTest {

    @InjectMocks
    private CreateBusRouteUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private RouteStopsService routeStopsService;

    @Mock
    private RouteDataMapper routeDataMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsRouteWithoutStops() {
        CreateRoute cmd = new CreateRoute(
                "29A", "Main", "Main tm", "Main en",
                "#FF5722", 30, true, "ashgabat",
                List.of(), List.of(), null, null);
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.save(any(BusRoute.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(routeDataMapper.toRouteData(any(BusRoute.class))).thenReturn(Mono.just(data));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNext(data)
                .verifyComplete();
    }

    @Test
    void createsRouteAndSavesStopsWhenProvided() {
        CreateRoute cmd = new CreateRoute(
                "29A", "Main", "Main tm", "Main en",
                "#FF5722", 30, true, "ashgabat",
                List.of("stop-1", "stop-2"), List.of("stop-2", "stop-1"), null, null);
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.save(any(BusRoute.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(routeStopsService.saveRouteStops(anyString(), anyList(), anyList())).thenReturn(Mono.empty());
        when(routeDataMapper.toRouteData(any(BusRoute.class))).thenReturn(Mono.just(data));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNext(data)
                .verifyComplete();

        verify(routeStopsService).saveRouteStops(anyString(), anyList(), anyList());
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
