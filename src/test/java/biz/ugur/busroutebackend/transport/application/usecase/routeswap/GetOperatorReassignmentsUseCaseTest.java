package biz.ugur.busroutebackend.transport.application.usecase.routeswap;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository.AssignmentChange;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetOperatorReassignmentsUseCaseTest {

    private static final Instant DURING_FIRST_SHIFT = Instant.parse("2026-08-17T05:30:00Z");

    @Mock
    private RouteSwapAuditRepository auditRepository;
    @Mock
    private BusRouteRepository busRouteRepository;
    @Mock
    private CorrelationContextService correlationService;

    private GetOperatorReassignmentsUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(new BusRouteId("axis-old"))).thenReturn(Mono.just(route("axis-old", "100")));
        when(busRouteRepository.findById(new BusRouteId("axis-new"))).thenReturn(Mono.just(route("axis-new", "66")));
        useCase = new GetOperatorReassignmentsUseCase(auditRepository, busRouteRepository, correlationService,
                Clock.fixed(DURING_FIRST_SHIFT, ZoneId.of("UTC")));
    }

    private static BusRoute route(String id, String number) {
        return BusRoute.builder()
                .id(new BusRouteId(id))
                .routeNumber(number)
                .cityId("city-001")
                .isActive(true)
                .build();
    }

    private static AssignmentChange change(String vehicleId, String plate, String from, String to,
                                           String source, Instant at) {
        return new AssignmentChange(vehicleId, plate, from, to, source, "dispatcher-01", at);
    }

    @Test
    void activeReassignmentIsListedWithResolvedRouteNumbers() {
        when(auditRepository.findOperatorChangesSince(any())).thenReturn(Flux.just(
                change("veh-1", "1903 AGH", "axis-old", "axis-new", "OPERATOR_REASSIGN", DURING_FIRST_SHIFT)));

        StepVerifier.create(useCase.activeReassignments())
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("1903 AGH", list.get(0).licensePlate());
                    assertEquals("100", list.get(0).fromRouteNumber());
                    assertEquals("66", list.get(0).toRouteNumber());
                    assertEquals("dispatcher-01", list.get(0).actor());
                })
                .verifyComplete();
    }

    @Test
    void revertedVehicleDisappearsFromList() {
        when(auditRepository.findOperatorChangesSince(any())).thenReturn(Flux.just(
                change("veh-1", "1903 AGH", "axis-old", "axis-new", "OPERATOR_REASSIGN", DURING_FIRST_SHIFT),
                change("veh-1", "1903 AGH", "axis-new", "axis-old", "OPERATOR_REVERT",
                        DURING_FIRST_SHIFT.plusSeconds(600))));

        StepVerifier.create(useCase.activeReassignments())
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();
    }

    @Test
    void reassignmentAfterRevertIsListedAgain() {
        when(auditRepository.findOperatorChangesSince(any())).thenReturn(Flux.just(
                change("veh-1", "1903 AGH", "axis-old", "axis-new", "OPERATOR_REASSIGN", DURING_FIRST_SHIFT),
                change("veh-1", "1903 AGH", "axis-new", "axis-old", "OPERATOR_REVERT",
                        DURING_FIRST_SHIFT.plusSeconds(600)),
                change("veh-1", "1903 AGH", "axis-old", "axis-new", "OPERATOR_REASSIGN",
                        DURING_FIRST_SHIFT.plusSeconds(1200))));

        StepVerifier.create(useCase.activeReassignments())
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("66", list.get(0).toRouteNumber());
                })
                .verifyComplete();
    }

    @Test
    void listIsScopedToCurrentShiftStart() {
        when(auditRepository.findOperatorChangesSince(any())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.activeReassignments())
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();

        verify(auditRepository).findOperatorChangesSince(Instant.parse("2026-08-17T00:00:00Z"));
    }

    @Test
    void unknownRouteIdLeavesNumberNull() {
        when(busRouteRepository.findById(new BusRouteId("axis-old"))).thenReturn(Mono.empty());
        when(auditRepository.findOperatorChangesSince(any())).thenReturn(Flux.just(
                change("veh-1", "1903 AGH", "axis-old", "axis-new", "OPERATOR_REASSIGN", DURING_FIRST_SHIFT)));

        StepVerifier.create(useCase.activeReassignments())
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals(null, list.get(0).fromRouteNumber());
                    assertEquals("66", list.get(0).toRouteNumber());
                })
                .verifyComplete();
    }

    @Test
    void repositoryErrorIsPropagated() {
        when(auditRepository.findOperatorChangesSince(any()))
                .thenReturn(Flux.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.activeReassignments())
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalStateException.class, err))
                .verify();
    }

    @Test
    void outsideOperationalShiftListIsEmpty() {
        useCase = new GetOperatorReassignmentsUseCase(auditRepository, busRouteRepository, correlationService,
                Clock.fixed(Instant.parse("2026-08-17T20:00:00Z"), ZoneId.of("UTC")));

        StepVerifier.create(useCase.activeReassignments())
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();
    }
}
