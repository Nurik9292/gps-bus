package biz.ugur.busroutebackend.transport.application.usecase.immediate;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Use case for clearing immediate assignment and reverting to shift/GPS assignment.
 */
@Service
@Slf4j
public class ClearImmediateAssignmentUseCase extends BaseUseCase<String, Boolean> {

    private final ImmediateRouteAssignmentRepository immediateAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;
    private final BusRouteRepository busRouteRepository;

    public ClearImmediateAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            ImmediateRouteAssignmentRepository immediateAssignmentRepository,
            VehicleRepository vehicleRepository,
            VehicleShiftAssignmentRepository shiftAssignmentRepository,
            BusRouteRepository busRouteRepository) {
        super(correlationService, eventBus);
        this.immediateAssignmentRepository = immediateAssignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Mono<Boolean> process(String vehicleId) {
        VehicleId vId = new VehicleId(vehicleId);

        return immediateAssignmentRepository.deactivateByVehicleId(vId)
                .flatMap(deactivatedCount -> {
                    if (deactivatedCount == 0) {
                        return Mono.just(false);
                    }

                    // Apply next priority (shift assignment)
                    return vehicleRepository.findById(vId)
                            .flatMap(this::applyShiftAssignmentIfExists)
                            .thenReturn(true);
                })
                .doOnSuccess(cleared -> {
                    if (Boolean.TRUE.equals(cleared)) {
                        log.info("Cleared immediate assignment for vehicle {}", vehicleId);
                    }
                });
    }

    private Mono<Vehicle> applyShiftAssignmentIfExists(Vehicle vehicle) {
        ShiftType currentShift = ShiftType.getCurrentShift();

        return shiftAssignmentRepository.findByVehicleIdAndShiftType(vehicle.getId(), currentShift)
                .filter(assignment -> assignment.isCurrentlyActive())
                .flatMap(assignment -> busRouteRepository.findById(assignment.getRouteId())
                        .map(route -> vehicle.toBuilder()
                                .assignedRouteId(route.getId())
                                .routeNumber(route.getRouteNumber())
                                .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                                .routeConfidence(100)
                                .assignedBy(null)
                                .manualAssignmentReason(null)
                                .build())
                )
                .flatMap(vehicleRepository::save)
                .defaultIfEmpty(vehicle.toBuilder()
                        .routeSource(RouteSource.UNKNOWN)
                        .assignedBy(null)
                        .manualAssignmentReason(null)
                        .build())
                .flatMap(v -> {
                    if (v.getRouteSource() == RouteSource.UNKNOWN) {
                        return vehicleRepository.save(v);
                    }
                    return Mono.just(v);
                });
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }
}
