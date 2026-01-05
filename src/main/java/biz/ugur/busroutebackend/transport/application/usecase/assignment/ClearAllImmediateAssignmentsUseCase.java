package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.assignment.ClearImmediateResult;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Slf4j
public class ClearAllImmediateAssignmentsUseCase extends BaseUseCase<Mono<Void>, ClearImmediateResult> {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;

    public ClearAllImmediateAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            RouteAssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository) {
        super(correlationService, eventBus);
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    protected Mono<ClearImmediateResult> process(Mono<Void> request) {
        return request.then(processInternal());
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<ClearImmediateResult> processInternal() {
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();

        log.info("Clearing all immediate assignments for date={}, shift={}", today, currentShift);

        return assignmentRepository.findActiveByDateAndShift(today, currentShift)
                .flatMap(this::clearVehicleRoute)
                .collectList()
                .flatMap(cleared -> assignmentRepository.deleteByEffectiveDateAndShift(today, currentShift))
                .map(deletedCount -> new ClearImmediateResult(
                        deletedCount,
                        Instant.now(),
                        true,
                        null
                ))
                .doOnSuccess(result -> log.info("Deleted {} immediate assignments", result.clearedCount()))
                .onErrorResume(error -> {
                    log.error("Failed to clear all immediate assignments", error);
                    return Mono.just(new ClearImmediateResult(
                            0,
                            Instant.now(),
                            false,
                            error.getMessage()
                    ));
                });
    }

    private Mono<Void> clearVehicleRoute(RouteAssignment assignment) {
        return vehicleRepository.findById(assignment.getVehicleId())
                .flatMap(vehicle -> {
                    if (vehicle.getAssignedRouteId() == null) {
                        return Mono.empty();
                    }
                    return vehicleRepository.save(vehicle.clearRouteAssignment());
                })
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to clear route for vehicle {}: {}",
                            assignment.getVehicleId(), error.getMessage());
                    return Mono.empty();
                });
    }
}
