package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleAlreadyExistsException;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateVehicleUseCaseTest {

    @InjectMocks
    private CreateVehicleUseCase useCase;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Test
    void createsVehicleWithoutRoute() {
        CreateVehicleUseCase.Command cmd = new CreateVehicleUseCase.Command(
                "dev-1", "1234 AGH", null, true);

        when(vehicleRepository.existsByDeviceId("dev-1")).thenReturn(Mono.just(false));
        when(vehicleRepository.existsByLicensePlate("1234 AGH")).thenReturn(Mono.just(false));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNextCount(1)
                .verifyComplete();

        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void createsInactiveVehicleWhenIsActiveFalse() {
        CreateVehicleUseCase.Command cmd = new CreateVehicleUseCase.Command(
                "dev-1", "1234 AGH", null, false);

        when(vehicleRepository.existsByDeviceId("dev-1")).thenReturn(Mono.just(false));
        when(vehicleRepository.existsByLicensePlate("1234 AGH")).thenReturn(Mono.just(false));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void createsVehicleWithAssignedRoute() {
        BusRoute route = BusRoute.create("29A", "Main", "", "", "#FF5722", "ashgabat", 30);
        CreateVehicleUseCase.Command cmd = new CreateVehicleUseCase.Command(
                "dev-1", "1234 AGH", route.getId().getValue(), true);

        when(vehicleRepository.existsByDeviceId("dev-1")).thenReturn(Mono.just(false));
        when(vehicleRepository.existsByLicensePlate("1234 AGH")).thenReturn(Mono.just(false));
        when(busRouteRepository.findById(route.getId())).thenReturn(Mono.just(route));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void errorsWhenDeviceIdAlreadyExists() {
        CreateVehicleUseCase.Command cmd = new CreateVehicleUseCase.Command(
                "dev-1", "1234 AGH", null, true);

        when(vehicleRepository.existsByDeviceId("dev-1")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(VehicleAlreadyExistsException.class, err))
                .verify();
    }

    @Test
    void errorsWhenLicensePlateAlreadyExists() {
        CreateVehicleUseCase.Command cmd = new CreateVehicleUseCase.Command(
                "dev-1", "1234 AGH", null, true);

        when(vehicleRepository.existsByDeviceId("dev-1")).thenReturn(Mono.just(false));
        when(vehicleRepository.existsByLicensePlate("1234 AGH")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(VehicleAlreadyExistsException.class, err))
                .verify();
    }
}
