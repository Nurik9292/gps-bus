package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class DeactivateVehicleUseCaseTest {

    @InjectMocks
    private DeactivateVehicleUseCase useCase;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RouteAssignmentRepository routeAssignmentRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deactivatesActiveVehicleAndCleansUpAssignments() {
        Vehicle active = Vehicle.create("dev-1", "1234 AGH");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(vehicleRepository.findById(active.getId())).thenReturn(Mono.just(active));
        when(routeAssignmentRepository.deactivateAllByVehicleId(active.getId())).thenReturn(Mono.just(3));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(new DeactivateVehicleUseCase.Command(
                active.getId().getValue(), "owner request")))
                .assertNext(result -> {
                    assertTrue(result.success());
                    assertEquals(3, result.shiftAssignmentsDeleted());
                })
                .verifyComplete();
    }

    @Test
    void returnsAlreadyInactiveWhenVehicleAlreadyInactive() {
        Vehicle inactive = Vehicle.create("dev-1", "1234 AGH").deactivate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(vehicleRepository.findById(inactive.getId())).thenReturn(Mono.just(inactive));

        StepVerifier.create(useCase.execute(new DeactivateVehicleUseCase.Command(
                inactive.getId().getValue(), "reason")))
                .assertNext(result -> {
                    assertTrue(result.success());
                    assertEquals("Vehicle is already inactive", result.errorMessage());
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenVehicleNotFound() {
        String id = VehicleId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new DeactivateVehicleUseCase.Command(id, "reason")))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
