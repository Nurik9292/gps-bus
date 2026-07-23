package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllBusRoutesWithPaginationUseCaseTest {

    @InjectMocks
    private GetAllBusRoutesWithPaginationUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private RouteStopsService routeStopsService;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RouteDataMapper routeDataMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private BusRoute sampleRoute() {
        return BusRoute.create("29A", "Main", "", "", "#FF5722", "ashgabat", 30);
    }

    @Test
    void returnsRoutesWithoutSearchQuery() {
        BusRoute route = sampleRoute();
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(route));
        when(busRouteRepository.count()).thenReturn(Mono.just(1L));
        when(busRouteRepository.countActiveRoutes()).thenReturn(Mono.just(1L));
        when(routeStopsService.getForwardStopsDTO(anyString())).thenReturn(Mono.just(List.of()));
        when(routeStopsService.getBackwardStopsDTO(anyString())).thenReturn(Mono.just(List.of()));
        when(vehicleRepository.countActiveVehiclesRouteNumber(anyString())).thenReturn(Mono.just(2L));
        when(routeDataMapper.toRouteDataWithStops(any(), any(), any(), any())).thenReturn(Mono.just(data));

        GetAllRoutePaginationQuery q = GetAllRoutePaginationQuery.fromParams(
                1, 10, null, null, null, null, null);

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .assertNext(list -> assertEquals(1, list.getRoutes().size()))
                .verifyComplete();
    }

    @Test
    void filtersByActiveOnly() {
        BusRoute route = sampleRoute();
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
                .thenReturn(Flux.just(route));
        when(busRouteRepository.countBySpecification(any(Specification.class))).thenReturn(Mono.just(1L));
        when(busRouteRepository.countActiveRoutes()).thenReturn(Mono.just(1L));
        when(routeStopsService.getForwardStopsDTO(anyString())).thenReturn(Mono.just(List.of()));
        when(routeStopsService.getBackwardStopsDTO(anyString())).thenReturn(Mono.just(List.of()));
        when(vehicleRepository.countActiveVehiclesRouteNumber(anyString())).thenReturn(Mono.just(0L));
        when(routeDataMapper.toRouteDataWithStops(any(), any(), any(), any())).thenReturn(Mono.just(data));

        GetAllRoutePaginationQuery q = GetAllRoutePaginationQuery.fromParams(
                1, 10, null, null, true, null, null);

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void usesSearchWithRelevanceWhenQueryProvided() {
        BusRoute route = sampleRoute();
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.searchWithRelevance(anyString(), any(), any(), any(Pageable.class)))
                .thenReturn(Flux.just(route));
        when(busRouteRepository.countBySearch(anyString(), any(), any())).thenReturn(Mono.just(1L));
        when(busRouteRepository.countActiveRoutes()).thenReturn(Mono.just(1L));
        when(routeStopsService.getForwardStopsDTO(anyString())).thenReturn(Mono.just(List.of()));
        when(routeStopsService.getBackwardStopsDTO(anyString())).thenReturn(Mono.just(List.of()));
        when(vehicleRepository.countActiveVehiclesRouteNumber(anyString()))
                .thenReturn(Mono.error(new RuntimeException("gps")));
        when(routeDataMapper.toRouteDataWithStops(any(), any(), any(), any())).thenReturn(Mono.just(data));

        GetAllRoutePaginationQuery q = GetAllRoutePaginationQuery.fromParams(
                1, 10, null, null, null, "29A", null);

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void filtersByCityId() {
        BusRoute route = sampleRoute();
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
                .thenReturn(Flux.just(route));
        when(busRouteRepository.countBySpecification(any(Specification.class))).thenReturn(Mono.just(1L));
        when(busRouteRepository.countActiveRoutes()).thenReturn(Mono.just(1L));
        when(routeStopsService.getForwardStopsDTO(anyString())).thenReturn(Mono.just(List.of()));
        when(routeStopsService.getBackwardStopsDTO(anyString())).thenReturn(Mono.just(List.of()));
        when(vehicleRepository.countActiveVehiclesRouteNumber(anyString())).thenReturn(Mono.just(0L));
        when(routeDataMapper.toRouteDataWithStops(any(), any(), any(), any())).thenReturn(Mono.just(data));

        GetAllRoutePaginationQuery q = GetAllRoutePaginationQuery.fromParams(
                1, 10, null, null, null, null, "ashgabat");

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
