package biz.ugur.busroutebackend.transport.application.usecase.routeswap;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.OperatorReassignmentDTO;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository.AssignmentChange;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GetOperatorReassignmentsUseCase {

    private static final String BOUND_CONTEXT = "transport";

    private final RouteSwapAuditRepository auditRepository;
    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;
    private final Clock clock;

    public GetOperatorReassignmentsUseCase(RouteSwapAuditRepository auditRepository,
                                           BusRouteRepository busRouteRepository,
                                           CorrelationContextService correlationService,
                                           Clock clock) {
        this.auditRepository = auditRepository;
        this.busRouteRepository = busRouteRepository;
        this.correlationService = correlationService;
        this.clock = clock;
    }

    public Mono<List<OperatorReassignmentDTO>> activeReassignments() {
        Instant now = clock.instant();
        return ShiftType.operationalShiftAt(now)
                .map(shift -> shift.startInstantOn(ShiftType.operationalDateAt(now)))
                .map(this::activeReassignmentsSince)
                .orElseGet(() -> Mono.just(List.of()));
    }

    private Mono<List<OperatorReassignmentDTO>> activeReassignmentsSince(Instant shiftStart) {
        return correlationService.executeWithCorrelation(
                auditRepository.findOperatorChangesSince(shiftStart)
                        .collectList()
                        .map(GetOperatorReassignmentsUseCase::lastChangePerVehicle)
                        .flatMapMany(Flux::fromIterable)
                        .filter(change -> VehicleRouteReassignmentUseCase.OPERATOR_REASSIGN.equals(change.source()))
                        .concatMap(this::toDto)
                        .collectList(),
                BOUND_CONTEXT);
    }

    private static Collection<AssignmentChange> lastChangePerVehicle(List<AssignmentChange> changes) {
        Map<String, AssignmentChange> latest = new LinkedHashMap<>();
        for (AssignmentChange change : changes) {
            latest.merge(change.vehicleId(), change,
                    (previous, current) -> current.observedAt().isBefore(previous.observedAt()) ? previous : current);
        }
        return latest.values();
    }

    private Mono<OperatorReassignmentDTO> toDto(AssignmentChange change) {
        return Mono.zip(
                        routeNumberOf(change.previousRouteId()),
                        routeNumberOf(change.newRouteId()))
                .map(numbers -> new OperatorReassignmentDTO(
                        change.vehicleId(), change.licensePlate(),
                        numbers.getT1().isBlank() ? null : numbers.getT1(),
                        numbers.getT2().isBlank() ? null : numbers.getT2(),
                        change.actor(), change.observedAt()));
    }

    private Mono<String> routeNumberOf(String routeId) {
        if (routeId == null) {
            return Mono.just("");
        }
        return busRouteRepository.findById(new BusRouteId(routeId))
                .map(BusRoute::getRouteNumber)
                .defaultIfEmpty("");
    }
}
