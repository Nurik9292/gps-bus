package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.services.VehicleAssignmentExpirationService;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ProcessExpiredAssignmentsUseCaseTest {

    @InjectMocks
    private ProcessExpiredAssignmentsUseCase useCase;

    @Mock
    private RouteAssignmentRepository assignmentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private VehicleAssignmentExpirationService expirationService;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsZeroWhenNoExpiredAssignments() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findExpiredAssignments()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(Mono.empty()))
                .assertNext(result -> {
                    assertEquals(0, result.processedCount());
                    assertEquals(0, result.errorCount());
                })
                .verifyComplete();
    }

    @Test
    void processesExpiredAssignmentAndClearsVehicleWithoutShiftBackup() {
        VehicleId vehicleId = VehicleId.generate();
        Vehicle vehicle = Vehicle.create("dev-1", "1234 AGH").toBuilder()
                .assignedRouteId(BusRouteId.generate())
                .routeNumber("29A")
                .build();
        RouteAssignment expired = RouteAssignment.create(
                vehicleId, BusRouteId.generate(),
                LocalDate.now().plusDays(1), ShiftType.FIRST,
                "admin", null, Instant.now().plusSeconds(3600));

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findExpiredAssignments()).thenReturn(Flux.just(expired));
        when(assignmentRepository.deactivateById(any(RouteAssignmentId.class))).thenReturn(Mono.empty());
        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(vehicle));
        when(assignmentRepository.findActiveByVehicleAndDateAndShift(any(VehicleId.class), any(LocalDate.class), any(ShiftType.class)))
                .thenReturn(Mono.empty());
        when(expirationService.clearExpiredAssignment(vehicle)).thenReturn(vehicle.clearRouteAssignment());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.empty()))
                .assertNext(result -> assertEquals(1, result.processedCount()))
                .verifyComplete();
    }

    @Test
    void retriesOnOptimisticLockConflictThenSucceeds() {
        VehicleId vehicleId = VehicleId.generate();
        Vehicle vehicle = Vehicle.create("dev-2", "5678 AGH").toBuilder()
                .assignedRouteId(BusRouteId.generate())
                .routeNumber("12")
                .build();
        RouteAssignment expired = RouteAssignment.create(
                vehicleId, BusRouteId.generate(),
                LocalDate.now().plusDays(1), ShiftType.FIRST,
                "admin", null, Instant.now().plusSeconds(3600));

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findExpiredAssignments()).thenReturn(Flux.just(expired));
        when(assignmentRepository.deactivateById(any(RouteAssignmentId.class))).thenReturn(Mono.empty());
        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(vehicle));
        when(assignmentRepository.findActiveByVehicleAndDateAndShift(any(VehicleId.class), any(LocalDate.class), any(ShiftType.class)))
                .thenReturn(Mono.empty());
        when(expirationService.clearExpiredAssignment(vehicle)).thenReturn(vehicle.clearRouteAssignment());
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenReturn(Mono.error(new org.springframework.dao.OptimisticLockingFailureException("concurrent GPS update")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.empty()))
                .assertNext(result -> {
                    assertEquals(1, result.processedCount());
                    assertEquals(0, result.errorCount());
                })
                .verifyComplete();
    }

    @Test
    void resultHasProcessedIsTrueWhenProcessedCountPositive() {
        ProcessExpiredAssignmentsUseCase.Result r = new ProcessExpiredAssignmentsUseCase.Result(5, 0);
        assertEquals(true, r.hasProcessed());

        ProcessExpiredAssignmentsUseCase.Result empty = new ProcessExpiredAssignmentsUseCase.Result(0, 0);
        assertEquals(false, empty.hasProcessed());
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
