package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeactivateVehicleUseCase extends BaseUseCase<DeactivateVehicleUseCase.Command, DeactivateVehicleUseCase.Result> {

    private final VehicleRepository vehicleRepository;
    private final RouteAssignmentRepository routeAssignmentRepository;

    public DeactivateVehicleUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleRepository vehicleRepository,
            RouteAssignmentRepository routeAssignmentRepository) {
        super(correlationService, eventBus);
        this.vehicleRepository = vehicleRepository;
        this.routeAssignmentRepository = routeAssignmentRepository;
    }

    @Override
    protected Mono<Result> process(Command command) {
        VehicleId vehicleId = new VehicleId(command.vehicleId());

        return vehicleRepository.findById(vehicleId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Vehicle not found: " + command.vehicleId())))
                .flatMap(vehicle -> {
                    if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
                        log.info("Vehicle {} is already inactive", vehicle.getLicensePlate());
                        return Mono.just(new Result(
                                vehicle.getId().getValue(),
                                vehicle.getLicensePlate(),
                                0, 0, true,
                                "Vehicle is already inactive"
                        ));
                    }

                    return cleanupAndDeactivate(vehicle, command.reason());
                });
    }

    private Mono<Result> cleanupAndDeactivate(Vehicle vehicle, String reason) {
        VehicleId vehicleId = vehicle.getId();

        return routeAssignmentRepository.deactivateAllByVehicleId(vehicleId)
                .doOnSuccess(count -> log.info("Deactivated {} route assignments for vehicle {}",
                        count, vehicle.getLicensePlate()))
                .flatMap(deactivatedCount -> {
                    Vehicle deactivatedVehicle = vehicle.deactivate();

                    return vehicleRepository.save(deactivatedVehicle)
                            .map(saved -> {
                                log.info("Vehicle {} deactivated. Reason: {}. Cleaned up {} route assignments",
                                        saved.getLicensePlate(), reason, deactivatedCount);

                                return new Result(
                                        saved.getId().getValue(),
                                        saved.getLicensePlate(),
                                        deactivatedCount,
                                        0,
                                        true,
                                        null
                                );
                            });
                })
                .onErrorResume(error -> {
                    log.error("Failed to deactivate vehicle {}: {}", vehicle.getLicensePlate(), error.getMessage());
                    return Mono.just(new Result(
                            vehicle.getId().getValue(),
                            vehicle.getLicensePlate(),
                            0, 0, false,
                            error.getMessage()
                    ));
                });
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    public record Command(
            String vehicleId,
            String reason
    ) {}

    public record Result(
            String vehicleId,
            String vehicleLicensePlate,
            int shiftAssignmentsDeleted,
            int immediateAssignmentsDeactivated,
            boolean success,
            String errorMessage
    ) {}
}
