package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetRouteStopsUseCaseTest {

    @InjectMocks
    private GetRouteStopsUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsRouteStopsList() {
        BusRouteId routeId = BusRouteId.generate();
        RouteStopInfo stop1 = mock(RouteStopInfo.class);
        RouteStopInfo stop2 = mock(RouteStopInfo.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.getRouteStopsInfo(routeId)).thenReturn(Flux.just(stop1, stop2));

        StepVerifier.create(useCase.execute(Mono.just(routeId.getValue())))
                .assertNext(result -> {
                    assertEquals(2, result.totalStops());
                    assertEquals(routeId.getValue(), result.routeId());
                })
                .verifyComplete();
    }

    @Test
    void returnsEmptyStopsListWhenNone() {
        BusRouteId routeId = BusRouteId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.getRouteStopsInfo(routeId)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(Mono.just(routeId.getValue())))
                .assertNext(result -> assertEquals(0, result.totalStops()))
                .verifyComplete();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
