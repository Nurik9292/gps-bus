package biz.ugur.busroutebackend.transport.application.usecase.immediate;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.model.ImmediateRouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


@Service
@Slf4j
public class ClearAllImmediateAssignmentsUseCase extends BaseUseCase<Void, ClearAllImmediateAssignmentsUseCase.Result> {

    public static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final ImmediateRouteAssignmentRepository immediateAssignmentRepository;
    private final VehicleRepository vehicleRepository;

    public ClearAllImmediateAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            ImmediateRouteAssignmentRepository immediateAssignmentRepository,
            VehicleRepository vehicleRepository) {
        super(correlationService, eventBus);
        this.immediateAssignmentRepository = immediateAssignmentRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    protected Mono<Result> process(Void input) {
        ZonedDateTime now = ZonedDateTime.now(ASHGABAT_ZONE);
        AtomicInteger vehiclesClearedCount = new AtomicInteger(0);

        return getImmediateAssignmentVehicleIds()
                .flatMap(vehicleIds -> clearRoutesForVehicles(vehicleIds, vehiclesClearedCount)
                        .then(immediateAssignmentRepository.deactivateAll())
                        .map(immediateCount -> {
                            log.info("Cleared {} immediate assignments and {} vehicle route assignments at {} (Ashgabat time)",
                                    immediateCount, vehiclesClearedCount.get(), now);
                            return new Result(immediateCount + vehiclesClearedCount.get(), now.toString(), true, null);
                        }))
                .onErrorResume(error -> {
                    log.error("Failed to clear assignments: {}", error.getMessage());
                    return Mono.just(new Result(0, now.toString(), false, error.getMessage()));
                });
    }

    private Mono<List<VehicleId>> getImmediateAssignmentVehicleIds() {
        return immediateAssignmentRepository.findAllActive()
                .map(ImmediateRouteAssignment::getVehicleId)
                .collectList();
    }

    private Mono<Void> clearRoutesForVehicles(List<VehicleId> vehicleIds, AtomicInteger clearedCount) {
        if (vehicleIds.isEmpty()) {
            log.debug("No vehicles with immediate assignments to clear");
            return Mono.empty();
        }

        log.debug("Clearing routes for {} vehicles with immediate assignments", vehicleIds.size());

        return reactor.core.publisher.Flux.fromIterable(vehicleIds)
                .flatMap(vehicleId -> clearRouteForVehicle(vehicleId, clearedCount))
                .then();
    }

    private Mono<Void> clearRouteForVehicle(VehicleId vehicleId, AtomicInteger clearedCount) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> {
                    if (vehicle.getAssignedRouteId() == null) {
                        return Mono.empty();
                    }
                    return vehicleRepository.save(vehicle.clearRouteAssignment())
                            .doOnSuccess(v -> {
                                clearedCount.incrementAndGet();
                                log.debug("Cleared immediate route assignment for vehicle: {}", v.getLicensePlate());
                            });
                })
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to clear route for vehicle {}: {}", vehicleId, error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    public record Result(
            int clearedCount,
            String clearedAt,
            boolean success,
            String errorMessage
    ) {}
}
