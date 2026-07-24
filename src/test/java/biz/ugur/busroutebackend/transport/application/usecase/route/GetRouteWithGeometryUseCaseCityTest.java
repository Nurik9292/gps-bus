package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDtoMappingService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteVehicleStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetRouteWithGeometryUseCaseCityTest {

    @InjectMocks
    private GetRouteWithGeometryUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private RouteDtoMappingService routeDtoMappingService;

    @Mock
    private CorrelationContextService correlationContextService;

    private BusRoute arkadagRoute;
    private RouteData routeData;

    @BeforeEach
    void setUp() {
        arkadagRoute = BusRoute.create("1", "Arkadag 1", "", "", "#FF0000", "city-006", 40);
        routeData = mock(RouteData.class);
        when(correlationContextService.getCurrentCorrelationId())
                .thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationContextService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.getRouteStopsInfoByRouteId(anyString(), anyInt()))
                .thenReturn(Flux.empty());
        when(busRouteRepository.getRouteVehicleStatistics(any()))
                .thenReturn(Mono.just(new RouteVehicleStatistics(0L, 0L, 0L)));
        when(routeDtoMappingService.toRouteWithFullDataDto(any(), any(), any(), any()))
                .thenReturn(routeData);
    }

    @Test
    void cityIdResolvesRouteWithinThatCity() {
        when(busRouteRepository.findByRouteNumberAndCityId("1", "city-006"))
                .thenReturn(Mono.just(arkadagRoute));

        StepVerifier.create(useCase.execute("1", "city-006"))
                .expectNext(routeData)
                .verifyComplete();

        verify(busRouteRepository).findByRouteNumberAndCityId("1", "city-006");
        verify(busRouteRepository, never()).findPreferredByRouteNumber(anyString());
    }

    @Test
    void missingCityIdFallsBackToPreferredRoute() {
        when(busRouteRepository.findPreferredByRouteNumber("1"))
                .thenReturn(Mono.just(arkadagRoute));

        StepVerifier.create(useCase.execute("1"))
                .expectNext(routeData)
                .verifyComplete();

        verify(busRouteRepository).findPreferredByRouteNumber("1");
        verify(busRouteRepository, never()).findByRouteNumberAndCityId(anyString(), anyString());
    }

    @Test
    void unknownCityYieldsEmpty() {
        when(busRouteRepository.findByRouteNumberAndCityId("1", "city-404"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("1", "city-404"))
                .verifyComplete();
    }
}
