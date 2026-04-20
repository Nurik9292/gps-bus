package biz.ugur.busroutebackend.admin.application.usecase.dashboard;

import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
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
class GetDashboardStatisticsUseCaseTest {

    @InjectMocks
    private GetDashboardStatisticsUseCase useCase;

    @Mock
    private BusRouteRepository routeRepository;

    @Mock
    private BusStopRepository stopRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private CityRepository cityRepository;

    @Test
    void aggregatesAllStatsFromRepositories() {
        when(routeRepository.count()).thenReturn(Mono.just(20L));
        when(routeRepository.countActiveRoutes()).thenReturn(Mono.just(18L));
        when(stopRepository.count()).thenReturn(Mono.just(300L));
        when(stopRepository.countActiveStops()).thenReturn(Mono.just(290L));
        when(vehicleRepository.count()).thenReturn(Mono.just(50L));
        when(vehicleRepository.countActiveVehicles()).thenReturn(Mono.just(40L));
        when(vehicleRepository.findVehiclesInMotion()).thenReturn(Flux.empty());
        when(adminRepository.count()).thenReturn(Mono.just(5L));
        when(adminRepository.countActiveAdmins()).thenReturn(Mono.just(4L));
        when(cityRepository.count()).thenReturn(Mono.just(3L));
        when(cityRepository.countActiveCities()).thenReturn(Mono.just(3L));

        StepVerifier.create(useCase.execute())
                .assertNext(result -> {
                    assertEquals(20L, result.getTotalRoutes());
                    assertEquals(18L, result.getActiveRoutes());
                    assertEquals(300L, result.getTotalStops());
                    assertEquals(50L, result.getTotalVehicles());
                    assertEquals(5L, result.getTotalAdmins());
                    assertEquals(3L, result.getTotalCities());
                })
                .verifyComplete();
    }

    @Test
    void returnsZerosWhenRepositoriesError() {
        when(routeRepository.count()).thenReturn(Mono.error(new RuntimeException("db")));
        when(routeRepository.countActiveRoutes()).thenReturn(Mono.error(new RuntimeException("db")));
        when(stopRepository.count()).thenReturn(Mono.error(new RuntimeException("db")));
        when(stopRepository.countActiveStops()).thenReturn(Mono.error(new RuntimeException("db")));
        when(vehicleRepository.count()).thenReturn(Mono.error(new RuntimeException("db")));
        when(vehicleRepository.countActiveVehicles()).thenReturn(Mono.error(new RuntimeException("db")));
        when(vehicleRepository.findVehiclesInMotion()).thenReturn(Flux.error(new RuntimeException("db")));
        when(adminRepository.count()).thenReturn(Mono.error(new RuntimeException("db")));
        when(adminRepository.countActiveAdmins()).thenReturn(Mono.error(new RuntimeException("db")));
        when(cityRepository.count()).thenReturn(Mono.error(new RuntimeException("db")));
        when(cityRepository.countActiveCities()).thenReturn(Mono.error(new RuntimeException("db")));

        StepVerifier.create(useCase.execute())
                .assertNext(result -> {
                    assertEquals(0L, result.getTotalRoutes());
                    assertEquals(0L, result.getVehiclesInMotion());
                })
                .verifyComplete();
    }
}
