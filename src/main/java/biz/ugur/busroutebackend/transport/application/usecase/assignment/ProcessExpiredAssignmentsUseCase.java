package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.services.VehicleAssignmentExpirationService;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ProcessExpiredAssignmentsUseCase extends BaseUseCase<Mono<Void>, ProcessExpiredAssignmentsUseCase.Result> {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final VehicleAssignmentExpirationService expirationService;

    public ProcessExpiredAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            RouteAssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository,
            VehicleAssignmentExpirationService expirationService) {
        super(correlationService, eventBus);
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.expirationService = expirationService;
    }

    @Override
    protected Mono<Result> process(Mono<Void> request) {
        return request.then(processInternal());
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Result> processInternal() {
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        return assignmentRepository.findExpiredAssignments()
                .flatMap(assignment -> processExpiredAssignment(assignment, processedCount, errorCount))
                .collectList()
                .map(results -> new Result(processedCount.get(), errorCount.get()))
                .doOnSuccess(result -> {
                    if (result.processedCount() > 0 || result.errorCount() > 0) {
                        log.info("Processed expired assignments: cleared={}, errors={}",
                                result.processedCount(), result.errorCount());
                    }
                });
    }

    private Mono<Vehicle> processExpiredAssignment(RouteAssignment assignment,
                                                    AtomicInteger processedCount,
                                                    AtomicInteger errorCount) {
        log.debug("Processing expired assignment: vehicleId={}, routeId={}, expiresAt={}",
                assignment.getVehicleId(), assignment.getRouteId(), assignment.getExpiresAt());

        return assignmentRepository.deactivateById(assignment.getId())
                .then(revertToShiftAssignmentOrClear(assignment.getVehicleId()))
                .doOnSuccess(vehicle -> processedCount.incrementAndGet())
                .doOnError(error -> {
                    errorCount.incrementAndGet();
                    log.error("Failed to process expired assignment for vehicle {}: {}",
                            assignment.getVehicleId(), error.getMessage());
                })
                .onErrorResume(error -> Mono.empty());
    }

    private Mono<Vehicle> revertToShiftAssignmentOrClear(VehicleId vehicleId) {
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();

        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle ->
                    assignmentRepository.findActiveByVehicleAndDateAndShift(vehicleId, today, currentShift)
                            .filter(RouteAssignment::isCurrentlyValid)
                            .flatMap(assignment -> applyShiftAssignment(vehicle, assignment))
                            .switchIfEmpty(Mono.defer(() -> clearVehicleRoute(vehicle)))
                );
    }

    private Mono<Vehicle> applyShiftAssignment(Vehicle vehicle, RouteAssignment assignment) {
        return busRouteRepository.findById(assignment.getRouteId())
                .map(route -> vehicle.toBuilder()
                        .assignedRouteId(route.getId())
                        .routeNumber(route.getRouteNumber())
                        .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                        .routeConfidence(100)
                        .build())
                .flatMap(vehicleRepository::save)
                .doOnSuccess(v -> log.info("Reverted vehicle {} to shift assignment: route={}",
                        v.getLicensePlate(), v.getRouteNumber()));
    }

    private Mono<Vehicle> clearVehicleRoute(Vehicle vehicle) {
        Vehicle clearedVehicle = expirationService.clearExpiredAssignment(vehicle);

        if (clearedVehicle == vehicle) {
            return Mono.just(vehicle);
        }

        return vehicleRepository.save(clearedVehicle)
                .doOnSuccess(v -> log.info("Cleared route assignment for vehicle {} (no active shift assignment)",
                        v.getLicensePlate()));
    }

    public record Result(int processedCount, int errorCount) {
        public boolean hasProcessed() {
            return processedCount > 0;
        }
    }
}
