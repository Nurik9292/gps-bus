package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetRouteByIdUseCaseTest {

    @InjectMocks
    private GetRouteByIdUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private RouteStopsService routeStopsService;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RouteDataMapper routeDataMapper;

    @Test
    void returnsEnrichedRouteData() {
        BusRoute route = BusRoute.create("29A", "Main", "", "", "#FF5722", "ashgabat", 30);
        RouteData data = mock(RouteData.class);

        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(busRouteRepository.findById(route.getId())).thenReturn(Mono.just(route));
        when(routeStopsService.getForwardStopsDTO(route.getId().getValue())).thenReturn(Mono.just(List.of()));
        when(routeStopsService.getBackwardStopsDTO(route.getId().getValue())).thenReturn(Mono.just(List.of()));
        when(vehicleRepository.countActiveVehiclesRouteNumber("29A")).thenReturn(Mono.just(2L));
        when(routeDataMapper.toRouteDataWithStops(any(BusRoute.class), any(), any(), any()))
                .thenReturn(Mono.just(data));

        StepVerifier.create(useCase.execute(Mono.just(
                new GetRouteByIdUseCase.Query(route.getId().getValue()))))
                .expectNext(data)
                .verifyComplete();
    }

    @Test
    void completesEmptyWhenRouteNotFound() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new GetRouteByIdUseCase.Query(BusRouteId.generate().getValue()))))
                .verifyError(RuntimeException.class);
    }
}
