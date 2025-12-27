package biz.ugur.busroutebackend.transport.application.usecase.shift;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.exceptions.ShiftAssignmentNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.VehicleShiftAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleShiftAssignmentId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteShiftAssignmentUseCase extends BaseUseCase<Mono<String>, Void> {

    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final ImmediateRouteAssignmentRepository immediateAssignmentRepository;

    public DeleteShiftAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleShiftAssignmentRepository shiftAssignmentRepository,
            VehicleRepository vehicleRepository,
            ImmediateRouteAssignmentRepository immediateAssignmentRepository) {
        super(correlationService, eventBus);
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.immediateAssignmentRepository = immediateAssignmentRepository;
    }

    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Void> processInternal(String id) {
        VehicleShiftAssignmentId assignmentId = VehicleShiftAssignmentId.of(id);

        return shiftAssignmentRepository.findById(assignmentId)
                .switchIfEmpty(Mono.error(ShiftAssignmentNotFoundException.byId(id)))
                .flatMap(assignment -> deleteAssignmentAndClearVehicleRoute(assignmentId, assignment))
                .doOnSuccess(v -> log.info("Deleted shift assignment: id={}", id))
                .doOnError(error -> log.error("Failed to delete shift assignment", error));
    }

    private Mono<Void> deleteAssignmentAndClearVehicleRoute(VehicleShiftAssignmentId assignmentId,
                                                             VehicleShiftAssignment assignment) {
        return shiftAssignmentRepository.deleteById(assignmentId)
                .then(clearVehicleRouteIfNoImmediateAssignment(assignment.getVehicleId()));
    }

    private Mono<Void> clearVehicleRouteIfNoImmediateAssignment(VehicleId vehicleId) {
        return immediateAssignmentRepository.findActiveByVehicleId(vehicleId)
                .hasElement()
                .flatMap(hasImmediateAssignment -> {
                    if (hasImmediateAssignment) {
                        log.debug("Vehicle {} has active immediate assignment, skipping route clear", vehicleId);
                        return Mono.empty();
                    }
                    return clearVehicleRoute(vehicleId);
                });
    }

    private Mono<Void> clearVehicleRoute(VehicleId vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> {
                    if (vehicle.getAssignedRouteId() == null) {
                        return Mono.empty();
                    }
                    return vehicleRepository.save(vehicle.clearRouteAssignment());
                })
                .doOnSuccess(v -> {
                    if (v != null) {
                        log.info("Cleared route assignment for vehicle: {}", v.getLicensePlate());
                    }
                })
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to clear route for vehicle {}: {}", vehicleId, error.getMessage());
                    return Mono.empty();
                });
    }
}
