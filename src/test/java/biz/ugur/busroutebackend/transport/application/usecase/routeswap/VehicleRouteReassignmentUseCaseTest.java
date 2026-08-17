package biz.ugur.busroutebackend.transport.application.usecase.routeswap;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.transport.application.dto.assignment.CreateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.dto.assignment.UpdateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.ReassignVehicleCommand;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.CreateRouteAssignmentUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.UpdateRouteAssignmentUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.exceptions.AssignmentValidationException;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusRouteNotFoundException;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VehicleRouteReassignmentUseCaseTest {

    private static final Instant DURING_FIRST_SHIFT = Instant.parse("2026-08-17T05:30:00Z");
    private static final LocalDate OPERATIONAL_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private BusRouteRepository busRouteRepository;
    @Mock
    private RouteAssignmentRepository assignmentRepository;
    @Mock
    private CreateRouteAssignmentUseCase createUseCase;
    @Mock
    private UpdateRouteAssignmentUseCase updateUseCase;
    @Mock
    private RouteSwapAuditRepository auditRepository;
    @Mock
    private SecurityContextService securityContextService;
    @Mock
    private CorrelationContextService correlationService;

    private VehicleRouteReassignmentUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(securityContextService.getCurrentUsername()).thenReturn(Mono.just("dispatcher-01"));
        when(vehicleRepository.findById(any())).thenReturn(Mono.just(vehicle("axis-old")));
        when(busRouteRepository.findByRouteNumberAndCityId("66", "city-001"))
                .thenReturn(Mono.just(route("axis-new", "66")));
        when(assignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.empty());
        when(createUseCase.execute(any())).thenReturn(Mono.just(assignmentData("axis-new", "66")));
        when(updateUseCase.execute(any())).thenReturn(Mono.just(assignmentData("axis-new", "66")));
        when(auditRepository.logOperatorAssignmentChange(anyString(), anyString(), any(), anyString(),
                anyString(), anyString())).thenReturn(Mono.empty());
        useCase = new VehicleRouteReassignmentUseCase(vehicleRepository, busRouteRepository,
                assignmentRepository, createUseCase, updateUseCase, auditRepository,
                securityContextService, correlationService,
                Clock.fixed(DURING_FIRST_SHIFT, ZoneId.of("UTC")));
    }

    private static Vehicle vehicle(String assignedRouteId) {
        return Vehicle.builder()
                .id(VehicleId.of("veh-1"))
                .licensePlate("1903 AGH")
                .deviceId("dev-1")
                .isActive(true)
                .cityId(biz.ugur.busroutebackend.transport.domain.valueobject.CityId.of("city-001"))
                .assignedRouteId(assignedRouteId == null ? null : new BusRouteId(assignedRouteId))
                .routeNumber(assignedRouteId == null ? null : "100")
                .build();
    }

    private static BusRoute route(String id, String number) {
        return BusRoute.builder()
                .id(new BusRouteId(id))
                .routeNumber(number)
                .cityId("city-001")
                .isActive(true)
                .build();
    }

    private static RouteAssignmentData assignmentData(String routeId, String routeNumber) {
        return new RouteAssignmentData("asg-1", "veh-1", "1903 AGH", "dev-1", routeId, routeNumber,
                null, OPERATIONAL_DATE, "FIRST", null, null, "dispatcher-01", "перекидка", null,
                true, true, false, null, null, 1L);
    }

    private static RouteAssignment existingAssignment() {
        return RouteAssignment.restore(
                RouteAssignmentId.of("asg-1"), VehicleId.of("veh-1"), new BusRouteId("axis-old"),
                OPERATIONAL_DATE, ShiftType.FIRST, "scheduler", "наряд",
                Instant.parse("2026-08-17T09:00:00Z"), true, null, null, 1L);
    }

    @Test
    void existingShiftAssignmentIsRepointedToFactualRoute() {
        when(assignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.just(existingAssignment()));

        StepVerifier.create(useCase.reassign(new ReassignVehicleCommand("veh-1", "66", "едет по 66-й")))
                .assertNext(result -> {
                    assertEquals("1903 AGH", result.licensePlate());
                    assertEquals("66", result.newRouteNumber());
                    assertEquals("100", result.previousRouteNumber());
                })
                .verifyComplete();

        ArgumentCaptor<Mono<UpdateRouteAssignmentCommand>> captor = ArgumentCaptor.forClass(Mono.class);
        verify(updateUseCase).execute(captor.capture());
        UpdateRouteAssignmentCommand command = captor.getValue().block();
        assertEquals("asg-1", command.id());
        assertEquals("axis-new", command.routeId());
        verify(createUseCase, never()).execute(any());
    }

    @Test
    void missingShiftAssignmentIsCreatedForCurrentShift() {
        StepVerifier.create(useCase.reassign(new ReassignVehicleCommand("veh-1", "66", "едет по 66-й")))
                .assertNext(result -> assertEquals("66", result.newRouteNumber()))
                .verifyComplete();

        ArgumentCaptor<Mono<CreateRouteAssignmentCommand>> captor = ArgumentCaptor.forClass(Mono.class);
        verify(createUseCase).execute(captor.capture());
        CreateRouteAssignmentCommand command = captor.getValue().block();
        assertEquals("axis-new", command.routeId());
        assertEquals("FIRST", command.shiftType());
        assertEquals(OPERATIONAL_DATE, command.effectiveDate());
        assertEquals("dispatcher-01", command.assignedBy());
        assertEquals(Instant.parse("2026-08-17T09:00:00Z"), command.expiresAt());
    }

    @Test
    void reassignmentIsLoggedWithOperatorSourceAndActor() {
        StepVerifier.create(useCase.reassign(new ReassignVehicleCommand("veh-1", "66", "едет по 66-й")))
                .expectNextCount(1)
                .verifyComplete();

        verify(auditRepository).logOperatorAssignmentChange("veh-1", "1903 AGH", "axis-old", "axis-new",
                "OPERATOR_REASSIGN", "dispatcher-01");
    }

    @Test
    void unknownRouteNumberInVehicleCityIsRejected() {
        when(busRouteRepository.findByRouteNumberAndCityId("77", "city-001")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.reassign(new ReassignVehicleCommand("veh-1", "77", "проверка")))
                .expectErrorSatisfies(err -> assertInstanceOf(BusRouteNotFoundException.class, err))
                .verify();

        verify(createUseCase, never()).execute(any());
        verify(updateUseCase, never()).execute(any());
    }

    @Test
    void unknownVehicleIsRejected() {
        when(vehicleRepository.findById(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.reassign(new ReassignVehicleCommand("veh-404", "66", "проверка")))
                .expectErrorSatisfies(err -> assertInstanceOf(VehicleNotFoundException.class, err))
                .verify();

        verify(auditRepository, never()).logOperatorAssignmentChange(anyString(), anyString(), any(),
                anyString(), anyString(), anyString());
    }

    @Test
    void reassignmentOutsideOperationalShiftIsRejected() {
        useCase = new VehicleRouteReassignmentUseCase(vehicleRepository, busRouteRepository,
                assignmentRepository, createUseCase, updateUseCase, auditRepository,
                securityContextService, correlationService,
                Clock.fixed(Instant.parse("2026-08-17T20:00:00Z"), ZoneId.of("UTC")));

        StepVerifier.create(useCase.reassign(new ReassignVehicleCommand("veh-1", "66", "ночью")))
                .expectErrorSatisfies(err -> assertInstanceOf(AssignmentValidationException.class, err))
                .verify();

        verify(createUseCase, never()).execute(any());
    }

    @Test
    void vehicleWithoutCityCannotResolveRouteNumber() {
        when(vehicleRepository.findById(any())).thenReturn(Mono.just(Vehicle.builder()
                .id(VehicleId.of("veh-1"))
                .licensePlate("1903 AGH")
                .deviceId("dev-1")
                .isActive(true)
                .build()));

        StepVerifier.create(useCase.reassign(new ReassignVehicleCommand("veh-1", "66", "без города")))
                .expectErrorSatisfies(err -> assertInstanceOf(AssignmentValidationException.class, err))
                .verify();
    }

    @Test
    void revertReturnsVehicleToRouteRecordedBeforeReassignment() {
        when(auditRepository.findLastOperatorReassign(eq("veh-1"), any()))
                .thenReturn(Mono.just(new RouteSwapAuditRepository.AssignmentChange(
                        "veh-1", "1903 AGH", "axis-old", "axis-new", "OPERATOR_REASSIGN",
                        "dispatcher-01", DURING_FIRST_SHIFT)));
        when(busRouteRepository.findById(new BusRouteId("axis-old")))
                .thenReturn(Mono.just(route("axis-old", "100")));
        when(assignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.just(existingAssignment()));
        when(updateUseCase.execute(any())).thenReturn(Mono.just(assignmentData("axis-old", "100")));

        StepVerifier.create(useCase.revert("veh-1"))
                .assertNext(result -> {
                    assertEquals("100", result.newRouteNumber());
                    assertEquals("1903 AGH", result.licensePlate());
                })
                .verifyComplete();

        verify(auditRepository).logOperatorAssignmentChange("veh-1", "1903 AGH", "axis-new", "axis-old",
                "OPERATOR_REVERT", "dispatcher-01");
    }

    @Test
    void revertWithoutOperatorReassignInShiftIsRejected() {
        when(auditRepository.findLastOperatorReassign(eq("veh-1"), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.revert("veh-1"))
                .expectErrorSatisfies(err -> assertInstanceOf(AssignmentValidationException.class, err))
                .verify();

        verify(updateUseCase, never()).execute(any());
        verify(createUseCase, never()).execute(any());
    }

    @Test
    void revertOfReassignmentWithoutPreviousRouteIsRejected() {
        when(auditRepository.findLastOperatorReassign(eq("veh-1"), any()))
                .thenReturn(Mono.just(new RouteSwapAuditRepository.AssignmentChange(
                        "veh-1", "1903 AGH", null, "axis-new", "OPERATOR_REASSIGN",
                        "dispatcher-01", DURING_FIRST_SHIFT)));

        StepVerifier.create(useCase.revert("veh-1"))
                .expectErrorSatisfies(err -> assertInstanceOf(AssignmentValidationException.class, err))
                .verify();

        verify(updateUseCase, never()).execute(any());
    }

    @Test
    void revertLooksOnlyAtCurrentShiftWindow() {
        when(auditRepository.findLastOperatorReassign(eq("veh-1"), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.revert("veh-1"))
                .expectError(AssignmentValidationException.class)
                .verify();

        verify(auditRepository).findLastOperatorReassign("veh-1", Instant.parse("2026-08-17T00:00:00Z"));
    }

    @Test
    void vehicleWithoutPreviousAssignmentLogsNullPreviousRoute() {
        when(vehicleRepository.findById(any())).thenReturn(Mono.just(vehicle(null)));

        StepVerifier.create(useCase.reassign(new ReassignVehicleCommand("veh-1", "66", "первый наряд")))
                .expectNextCount(1)
                .verifyComplete();

        verify(auditRepository).logOperatorAssignmentChange(eq("veh-1"), eq("1903 AGH"), isNull(),
                eq("axis-new"), eq("OPERATOR_REASSIGN"), eq("dispatcher-01"));
    }
}
