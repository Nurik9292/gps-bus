package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.transport.application.mapper.VehicleResponseMapper;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.infrastructure.cache.ActiveVehicleCacheRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetActiveVehiclesUseCaseCityCacheTest {

    @InjectMocks
    private GetActiveVehiclesUseCase useCase;

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private BusRouteRepository busRouteRepository;
    @Mock
    private ActiveVehicleCacheRepository cacheRepository;
    @Mock
    private VehicleResponseMapper responseMapper;
    @Mock
    private CorrelationContextService correlationContextService;
    @Mock
    private EventBus eventBus;

    private BusRoute arkadagRoute;

    @BeforeEach
    void setUp() {
        arkadagRoute = BusRoute.create("1", "Arkadag 1", "", "", "#FF0000", "city-006", 40);
        when(cacheRepository.getCached(anyString())).thenReturn(Flux.empty());
        when(cacheRepository.cache(anyString(), any(), any())).thenReturn(Mono.empty());
        when(vehicleRepository.findByAssignedRouteId(any())).thenReturn(Flux.empty());
    }

    @Test
    void cityScopedQueryUsesCityCacheKeyAndCityResolution() {
        when(busRouteRepository.findByRouteNumberAndCityId("1", "city-006"))
                .thenReturn(Mono.just(arkadagRoute));

        StepVerifier.create(useCase.execute(GetActiveVehiclesUseCase.Query.byRoute("1", "city-006")))
                .verifyComplete();

        verify(cacheRepository).getCached("active_vehicles:route:city-006:1");
        verify(busRouteRepository).findByRouteNumberAndCityId("1", "city-006");
        verify(busRouteRepository, never()).findPreferredByRouteNumber(anyString());
    }

    @Test
    void cityLessQueryKeepsPreferredResolutionUnderAnyKey() {
        when(busRouteRepository.findPreferredByRouteNumber("1"))
                .thenReturn(Mono.just(arkadagRoute));

        StepVerifier.create(useCase.execute(GetActiveVehiclesUseCase.Query.byRoute("1")))
                .verifyComplete();

        verify(cacheRepository).getCached("active_vehicles:route:any:1");
        verify(busRouteRepository).findPreferredByRouteNumber("1");
        verify(busRouteRepository, never()).findByRouteNumberAndCityId(anyString(), anyString());
    }
}
