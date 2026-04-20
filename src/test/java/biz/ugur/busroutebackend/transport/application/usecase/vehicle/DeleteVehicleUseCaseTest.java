package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class DeleteVehicleUseCaseTest {

    @InjectMocks
    private DeleteVehicleUseCase useCase;

    @Mock
    private VehicleRepository vehicleRepository;

    @Test
    void deletesExistingVehicle() {
        Vehicle vehicle = Vehicle.create("dev-1", "1234 AGH");

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Mono.just(vehicle));
        when(vehicleRepository.deleteById(vehicle.getId())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(vehicle.getId().getValue())))
                .verifyComplete();

        verify(vehicleRepository).deleteById(vehicle.getId());
    }

    @Test
    void errorsWhenVehicleNotFound() {
        String id = VehicleId.generate().getValue();
        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id)))
                .expectErrorSatisfies(err -> assertInstanceOf(VehicleNotFoundException.class, err))
                .verify();

        verify(vehicleRepository, never()).deleteById(any(VehicleId.class));
    }
}
