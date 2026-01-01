package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Slf4j
public class ClearImmediateAssignmentUseCase extends BaseUseCase<Mono<String>, Void> {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;

    public ClearImmediateAssignmentUseCase(
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

    private Mono<Void> processInternal(String vehicleIdStr) {
        VehicleId vehicleId = VehicleId.of(vehicleIdStr);
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();

        log.info("Clearing immediate assignment for vehicle: {}, date={}, shift={}",
                vehicleIdStr, today, currentShift);

        return assignmentRepository.findActiveByVehicleAndDateAndShift(vehicleId, today, currentShift)
                .flatMap(assignment -> {
                    log.debug("Found assignment to deactivate: {}", assignment.getId());
                    return assignmentRepository.deactivateById(assignment.getId());
                })
                .then(clearVehicleRoute(vehicleId))
                .doOnSuccess(v -> log.info("Cleared immediate assignment for vehicle: {}", vehicleIdStr))
                .doOnError(error -> log.error("Failed to clear immediate assignment for vehicle: {}",
                        vehicleIdStr, error));
    }

    private Mono<Void> clearVehicleRoute(VehicleId vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> {
                    if (vehicle.getAssignedRouteId() == null) {
                        return Mono.empty();
                    }
                    return vehicleRepository.save(vehicle.clearRouteAssignment());
                })
                .then();
    }
}
