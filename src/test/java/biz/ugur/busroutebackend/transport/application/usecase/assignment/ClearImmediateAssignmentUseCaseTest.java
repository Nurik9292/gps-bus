package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ClearImmediateAssignmentUseCaseTest {

    @InjectMocks
    private ClearImmediateAssignmentUseCase useCase;

    @Mock
    private RouteAssignmentRepository assignmentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deactivatesAssignmentAndClearsVehicleRoute() {
        VehicleId vehicleId = VehicleId.generate();
        Vehicle vehicle = Vehicle.create("dev-1", "1234 AGH").toBuilder()
                .assignedRouteId(BusRouteId.generate())
                .routeNumber("29A")
                .build();

        RouteAssignment assignment = RouteAssignment.create(
                vehicleId, BusRouteId.generate(),
                LocalDate.now(), ShiftType.FULL_DAY,
                "admin", null, Instant.now().plusSeconds(3600));

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findActiveByVehicleAndDateAndShift(
                any(VehicleId.class), any(LocalDate.class), any(ShiftType.class)))
                .thenReturn(Mono.just(assignment));
        when(assignmentRepository.deactivateById(any(RouteAssignmentId.class))).thenReturn(Mono.empty());
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(vehicleId.getValue())))
                .verifyComplete();

        verify(assignmentRepository).deactivateById(any(RouteAssignmentId.class));
    }

    @Test
    void noOpWhenNoActiveAssignmentAndNoRouteOnVehicle() {
        VehicleId vehicleId = VehicleId.generate();
        Vehicle vehicle = Vehicle.create("dev-2", "9999 AGH");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findActiveByVehicleAndDateAndShift(
                any(VehicleId.class), any(LocalDate.class), any(ShiftType.class)))
                .thenReturn(Mono.empty());
        when(vehicleRepository.findById(vehicleId)).thenReturn(Mono.just(vehicle));

        StepVerifier.create(useCase.execute(Mono.just(vehicleId.getValue())))
                .verifyComplete();

        verify(assignmentRepository, never()).deactivateById(any(RouteAssignmentId.class));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
