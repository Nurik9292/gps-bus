package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllVehiclesWithPaginationUseCaseTest {

    @InjectMocks
    private GetAllVehiclesWithPaginationUseCase useCase;

    @Mock
    private VehicleRepository vehicleRepository;

    @Test
    void returnsPaginatedVehicleList() {
        Vehicle v1 = Vehicle.create("dev-1", "1234 AGH");
        Vehicle v2 = Vehicle.create("dev-2", "5678 AGH");

        when(vehicleRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
                .thenReturn(Flux.just(v1, v2));
        when(vehicleRepository.countBySpecification(any(Specification.class))).thenReturn(Mono.just(2L));
        when(vehicleRepository.countActiveVehicles()).thenReturn(Mono.just(2L));

        GetAllVehiclesWithPaginationUseCase.Query q =
                GetAllVehiclesWithPaginationUseCase.Query.fromParams(
                        1, 10, "created_at", "desc", null, null);

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .assertNext(result -> {
                    assertEquals(2, result.vehicles().size());
                    assertEquals(2L, result.totalCount());
                    assertEquals(2L, result.activeCount());
                })
                .verifyComplete();
    }

    @Test
    void appliesActiveFilter() {
        Vehicle v = Vehicle.create("dev-1", "1234 AGH");

        when(vehicleRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
                .thenReturn(Flux.just(v));
        when(vehicleRepository.countBySpecification(any(Specification.class))).thenReturn(Mono.just(1L));
        when(vehicleRepository.countActiveVehicles()).thenReturn(Mono.just(5L));

        GetAllVehiclesWithPaginationUseCase.Query q =
                GetAllVehiclesWithPaginationUseCase.Query.fromParams(
                        1, 10, "created_at", "asc", true, null);

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .assertNext(result -> assertEquals(1, result.vehicles().size()))
                .verifyComplete();
    }

    @Test
    void appliesSearchFilter() {
        Vehicle v = Vehicle.create("dev-1", "1234 AGH");

        when(vehicleRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
                .thenReturn(Flux.just(v));
        when(vehicleRepository.countBySpecification(any(Specification.class))).thenReturn(Mono.just(1L));
        when(vehicleRepository.countActiveVehicles()).thenReturn(Mono.just(5L));

        GetAllVehiclesWithPaginationUseCase.Query q =
                GetAllVehiclesWithPaginationUseCase.Query.fromParams(
                        1, 10, null, null, null, "1234");

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .assertNext(result -> assertEquals(1, result.vehicles().size()))
                .verifyComplete();
    }
}
