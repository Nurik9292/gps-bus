package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteAssignmentNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Slf4j
public class DeleteRouteAssignmentUseCase extends BaseUseCase<Mono<String>, Void> {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;

    public DeleteRouteAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            RouteAssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository) {
        super(correlationService, eventBus);
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
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
        RouteAssignmentId assignmentId = RouteAssignmentId.of(id);

        return assignmentRepository.findById(assignmentId)
                .switchIfEmpty(Mono.error(() -> RouteAssignmentNotFoundException.byId(id)))
                .flatMap(assignment -> deleteAssignmentAndClearVehicleIfNeeded(assignmentId, assignment))
                .doOnSuccess(v -> log.info("Deleted route assignment: id={}", id))
                .doOnError(error -> log.error("Failed to delete route assignment: id={}", id, error));
    }

    private Mono<Void> deleteAssignmentAndClearVehicleIfNeeded(RouteAssignmentId assignmentId,
                                                                 RouteAssignment assignment) {
        return assignmentRepository.deleteById(assignmentId)
                .then(clearVehicleRouteIfApplicable(assignment));
    }

    private Mono<Void> clearVehicleRouteIfApplicable(RouteAssignment deletedAssignment) {
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();

        if (!deletedAssignment.getEffectiveDate().equals(today) ||
            deletedAssignment.getShiftType() != currentShift) {
            log.debug("Deleted assignment is not for current shift (date={}, shift={}), not clearing vehicle route",
                    deletedAssignment.getEffectiveDate(), deletedAssignment.getShiftType());
            return Mono.empty();
        }

        log.debug("Deleted assignment was for current shift, checking if need to clear vehicle route for: {}",
                deletedAssignment.getVehicleId());

        return clearVehicleRoute(deletedAssignment.getVehicleId());
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
