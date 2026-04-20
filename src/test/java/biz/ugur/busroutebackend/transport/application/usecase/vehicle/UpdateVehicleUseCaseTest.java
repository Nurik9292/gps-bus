package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateVehicleUseCaseTest {

    @InjectMocks
    private UpdateVehicleUseCase useCase;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Test
    void updatesDeviceIdAndSavesVehicle() {
        Vehicle vehicle = Vehicle.create("dev-old", "1234 AGH");
        UpdateVehicleUseCase.Command cmd = new UpdateVehicleUseCase.Command(
                vehicle.getId().getValue(), "dev-new", null, null, null);

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Mono.just(vehicle));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void deactivatesVehicleWhenIsActiveFalse() {
        Vehicle vehicle = Vehicle.create("dev-1", "1234 AGH");
        UpdateVehicleUseCase.Command cmd = new UpdateVehicleUseCase.Command(
                vehicle.getId().getValue(), null, null, null, false);

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Mono.just(vehicle));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void assignsRouteWhenProvided() {
        Vehicle vehicle = Vehicle.create("dev-1", "1234 AGH");
        BusRoute route = BusRoute.create("29A", "Main", "", "", "#FF5722", "ashgabat", 30);
        UpdateVehicleUseCase.Command cmd = new UpdateVehicleUseCase.Command(
                vehicle.getId().getValue(), null, null, route.getId().getValue(), null);

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Mono.just(vehicle));
        when(busRouteRepository.findById(route.getId())).thenReturn(Mono.just(route));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void clearsRouteWhenAssignedRouteIdIsBlank() {
        Vehicle vehicle = Vehicle.create("dev-1", "1234 AGH");
        UpdateVehicleUseCase.Command cmd = new UpdateVehicleUseCase.Command(
                vehicle.getId().getValue(), null, null, "", null);

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Mono.just(vehicle));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void errorsWhenVehicleNotFound() {
        UpdateVehicleUseCase.Command cmd = new UpdateVehicleUseCase.Command(
                VehicleId.generate().getValue(), null, null, null, null);

        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(VehicleNotFoundException.class, err))
                .verify();
    }
}
