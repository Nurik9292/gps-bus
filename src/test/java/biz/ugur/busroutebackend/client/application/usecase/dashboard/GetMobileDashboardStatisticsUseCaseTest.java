package biz.ugur.busroutebackend.client.application.usecase.dashboard;

import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
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
class GetMobileDashboardStatisticsUseCaseTest {

    @InjectMocks
    private GetMobileDashboardStatisticsUseCase useCase;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Test
    void aggregatesActiveClientsAndVehiclesInMotion() {
        Vehicle v1 = Vehicle.create("dev-1", "1234 AGH");
        Vehicle v2 = Vehicle.create("dev-2", "5678 AGH");

        when(clientRepository.countActiveClients()).thenReturn(Mono.just(100L));
        when(vehicleRepository.findVehiclesInMotion()).thenReturn(Flux.just(v1, v2));

        StepVerifier.create(useCase.execute())
                .assertNext(result -> {
                    assertEquals(100L, result.getActiveClients());
                    assertEquals(2L, result.getActiveVehicles());
                })
                .verifyComplete();
    }

    @Test
    void returnsZeroWhenRepositoriesError() {
        when(clientRepository.countActiveClients()).thenReturn(Mono.error(new RuntimeException("db")));
        when(vehicleRepository.findVehiclesInMotion()).thenReturn(Flux.error(new RuntimeException("gps")));

        StepVerifier.create(useCase.execute())
                .assertNext(result -> {
                    assertEquals(0L, result.getActiveClients());
                    assertEquals(0L, result.getActiveVehicles());
                })
                .verifyComplete();
    }
}
