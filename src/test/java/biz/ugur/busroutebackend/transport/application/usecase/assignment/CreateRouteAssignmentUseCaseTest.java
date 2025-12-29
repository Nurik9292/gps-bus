package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.transport.application.dto.assignment.CreateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.exceptions.AssignmentValidationException;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusRouteNotFoundException;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CreateRouteAssignmentUseCaseTest {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    @InjectMocks
    private CreateRouteAssignmentUseCase useCase;

    @Mock
    private RouteAssignmentRepository assignmentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private RouteAssignmentDataMapper dataMapper;

    @Mock
    private CorrelationContextService correlationContextService;

    @Mock
    private EventBus eventBus;

    private VehicleId vehicleId;
    private BusRouteId routeId;
    private Vehicle mockVehicle;
    private BusRoute mockRoute;

    @BeforeEach
    void setUp() {
        vehicleId = VehicleId.generate();
        routeId = BusRouteId.generate();

        mockVehicle = Vehicle.builder()
                .id(vehicleId)
                .deviceId("TEST-001")
                .licensePlate("1234 ABC")
                .isActive(true)
                .build();


        mockRoute = BusRoute.builder()
                .id(routeId)
                .routeNumber("5")
                .isActive(true)
                .build();


        when(correlationContextService.executeWithCorrelation(any(Mono.class), any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));


        lenient().doNothing().when(eventBus).publish(any(biz.ugur.busroutebackend.shared.domain.event.DomainEvent.class));


        lenient().when(dataMapper.toRouteAssignmentData(any(RouteAssignment.class)))
                .thenAnswer(invocation -> {
                    RouteAssignment assignment = invocation.getArgument(0);
                    RouteAssignmentData mockData = new RouteAssignmentData(
                            assignment.getId().getValue(),
                            assignment.getVehicleId().getValue(),
                            assignment.getRouteId().getValue(),
                            assignment.getEffectiveDate(),
                            assignment.getShiftType().toString(),
                            assignment.getAssignedBy(),
                            assignment.getReason(),
                            assignment.getExpiresAt(),
                            Boolean.TRUE.equals(assignment.getIsActive()),
                            assignment.isForCurrentShift(),
                            assignment.isExpired(),
                            null,
                            null,
                            0L
                    );
                    return Mono.just(mockData);
                });
    }

    @Test
    void execute_WithValidCommand_ShouldCreateAssignment() {

        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        CreateRouteAssignmentCommand command = new CreateRouteAssignmentCommand(
                vehicleId.getValue(),
                routeId.getValue(),
                tomorrow,
                "FIRST",
                "admin",
                "Test reason",
                null
        );

        RouteAssignment mockAssignment = RouteAssignment.create(
                vehicleId, routeId, tomorrow, ShiftType.FIRST, "admin", "Test reason", null
        );

        RouteAssignmentData mockData = new RouteAssignmentData(
                mockAssignment.getId().getValue(),
                vehicleId.getValue(),
                routeId.getValue(),
                tomorrow,
                "FIRST",
                "admin",
                "Test reason",
                null,
                true,
                false,
                false,
                null,
                null,
                0L
        );

        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(mockVehicle));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.just(mockRoute));
        when(assignmentRepository.existsActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.just(false));
        when(assignmentRepository.save(any(RouteAssignment.class))).thenReturn(Mono.just(mockAssignment));
        when(dataMapper.toRouteAssignmentData(any(RouteAssignment.class))).thenReturn(Mono.just(mockData));


        Mono<RouteAssignmentData> result = useCase.execute(Mono.just(command));


        StepVerifier.create(result)
                .expectNextMatches(data -> {
                    return data.vehicleId().equals(vehicleId.getValue()) &&
                           data.routeId().equals(routeId.getValue()) &&
                           data.effectiveDate().equals(tomorrow) &&
                           data.shiftType().equals("FIRST");
                })
                .verifyComplete();

        verify(vehicleRepository).findById(any(VehicleId.class));
        verify(busRouteRepository).findById(any(BusRouteId.class));
        verify(assignmentRepository).existsActiveByVehicleAndDateAndShift(any(), any(), any());
        verify(assignmentRepository).save(any(RouteAssignment.class));
    }

    @Test
    void execute_WithNonExistentVehicle_ShouldThrowException() {

        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        CreateRouteAssignmentCommand command = new CreateRouteAssignmentCommand(
                vehicleId.getValue(),
                routeId.getValue(),
                tomorrow,
                "FIRST",
                "admin",
                null,
                null
        );

        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.empty());


        StepVerifier.create(useCase.execute(Mono.just(command)))
                .expectError(VehicleNotFoundException.class)
                .verify();

        verify(vehicleRepository).findById(any(VehicleId.class));
        verify(busRouteRepository, never()).findById(any());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void execute_WithInactiveVehicle_ShouldThrowException() {

        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        CreateRouteAssignmentCommand command = new CreateRouteAssignmentCommand(
                vehicleId.getValue(),
                routeId.getValue(),
                tomorrow,
                "FIRST",
                "admin",
                null,
                null
        );

        Vehicle inactiveVehicle = mockVehicle.toBuilder()
                .isActive(false)
                .build();

        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(inactiveVehicle));


        StepVerifier.create(useCase.execute(Mono.just(command)))
                .expectError(AssignmentValidationException.class)
                .verify();

        verify(vehicleRepository).findById(any(VehicleId.class));
        verify(busRouteRepository, never()).findById(any());
    }

    @Test
    void execute_WithNonExistentRoute_ShouldThrowException() {

        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        CreateRouteAssignmentCommand command = new CreateRouteAssignmentCommand(
                vehicleId.getValue(),
                routeId.getValue(),
                tomorrow,
                "FIRST",
                "admin",
                null,
                null
        );

        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(mockVehicle));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.empty());


        StepVerifier.create(useCase.execute(Mono.just(command)))
                .expectError(BusRouteNotFoundException.class)
                .verify();

        verify(vehicleRepository).findById(any(VehicleId.class));
        verify(busRouteRepository).findById(any(BusRouteId.class));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void execute_WithConflictingAssignment_ShouldThrowException() {

        LocalDate tomorrow = LocalDate.now(ASHGABAT_ZONE).plusDays(1);
        CreateRouteAssignmentCommand command = new CreateRouteAssignmentCommand(
                vehicleId.getValue(),
                routeId.getValue(),
                tomorrow,
                "FIRST",
                "admin",
                null,
                null
        );

        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(mockVehicle));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.just(mockRoute));
        when(assignmentRepository.existsActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.just(true));


        StepVerifier.create(useCase.execute(Mono.just(command)))
                .expectError(AssignmentValidationException.class)
                .verify();

        verify(vehicleRepository).findById(any(VehicleId.class));
        verify(busRouteRepository).findById(any(BusRouteId.class));
        verify(assignmentRepository).existsActiveByVehicleAndDateAndShift(any(), any(), any());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void execute_WithPastDate_ShouldThrowException() {

        LocalDate yesterday = LocalDate.now(ASHGABAT_ZONE).minusDays(1);
        CreateRouteAssignmentCommand command = new CreateRouteAssignmentCommand(
                vehicleId.getValue(),
                routeId.getValue(),
                yesterday,
                "FIRST",
                "admin",
                null,
                null
        );

        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(mockVehicle));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.just(mockRoute));
        when(assignmentRepository.existsActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.just(false));


        StepVerifier.create(useCase.execute(Mono.just(command)))
                .expectError(AssignmentValidationException.class)
                .verify();
    }
}
