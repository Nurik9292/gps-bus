package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class SyncBusRouteAssignmentsUseCase implements UseCase<List<BusInfoDTO>, Mono<SyncBusRouteAssignmentsUseCase.BusRouteAssignmentResult>> {

    private final VehicleRepository vehicleRepository;

    public SyncBusRouteAssignmentsUseCase(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public Mono<BusRouteAssignmentResult> execute(List<BusInfoDTO> busInfos) {
        log.info("Syncing route assignments for {} buses", busInfos.size());

        return Flux.fromIterable(busInfos)
                .flatMap(this::assignBusToRoute)
                .collectList()
                .map(this::createResult)
                .doOnSuccess(result -> log.info("Route assignment sync completed: {}", result));
    }

    private Mono<AssignmentStatus> assignBusToRoute(BusInfoDTO busInfo) {
        return vehicleRepository.findByLicensePlate(busInfo.getCarNumber())
                .flatMap(vehicle -> {
                    try {
                        BusRouteId routeId = BusRouteId.of(busInfo.getRouteNumber());

                        if (routeId.equals(vehicle.getAssignedRouteId())) {
                            return Mono.just(AssignmentStatus.unchanged(vehicle.getLicensePlate(), routeId.getValue()));
                        }

                        vehicle.assignToRoute(routeId);

                        return vehicleRepository.save(vehicle)
                                .map(savedVehicle -> AssignmentStatus.assigned(
                                        savedVehicle.getLicensePlate(),
                                        routeId.getValue()
                                ));

                    } catch (Exception e) {
                        log.warn("Failed to assign vehicle {} to route {}: {}",
                                busInfo.getCarNumber(), busInfo.getRouteNumber(), e.getMessage());
                        return Mono.just(AssignmentStatus.failed(busInfo.getCarNumber(), e.getMessage()));
                    }
                })
                .switchIfEmpty(Mono.just(AssignmentStatus.vehicleNotFound(busInfo.getCarNumber())));
    }

    private BusRouteAssignmentResult createResult(List<AssignmentStatus> statuses) {
        long assigned = statuses.stream().mapToLong(s -> s.isAssigned() ? 1 : 0).sum();
        long unchanged = statuses.stream().mapToLong(s -> s.isUnchanged() ? 1 : 0).sum();
        long failed = statuses.stream().mapToLong(s -> s.isFailed() ? 1 : 0).sum();
        long notFound = statuses.stream().mapToLong(s -> s.isVehicleNotFound() ? 1 : 0).sum();

        return new BusRouteAssignmentResult(assigned, unchanged, failed, notFound, Instant.now());
    }


    public record BusRouteAssignmentResult(
            long assignedCount,
            long unchangedCount,
            long failedCount,
            long vehicleNotFoundCount,
            Instant processedAt) {
        }

    public static class AssignmentStatus {
        private final String licensePlate;
        private final String routeId;
        private final String errorMessage;
        private final AssignmentType type;

        private AssignmentStatus(String licensePlate, String routeId, String errorMessage, AssignmentType type) {
            this.licensePlate = licensePlate;
            this.routeId = routeId;
            this.errorMessage = errorMessage;
            this.type = type;
        }

        public static AssignmentStatus assigned(String licensePlate, String routeId) {
            return new AssignmentStatus(licensePlate, routeId, null, AssignmentType.ASSIGNED);
        }

        public static AssignmentStatus unchanged(String licensePlate, String routeId) {
            return new AssignmentStatus(licensePlate, routeId, null, AssignmentType.UNCHANGED);
        }

        public static AssignmentStatus failed(String licensePlate, String errorMessage) {
            return new AssignmentStatus(licensePlate, null, errorMessage, AssignmentType.FAILED);
        }

        public static AssignmentStatus vehicleNotFound(String licensePlate) {
            return new AssignmentStatus(licensePlate, null, "Vehicle not found", AssignmentType.VEHICLE_NOT_FOUND);
        }

        public boolean isAssigned() { return type == AssignmentType.ASSIGNED; }
        public boolean isUnchanged() { return type == AssignmentType.UNCHANGED; }
        public boolean isFailed() { return type == AssignmentType.FAILED; }
        public boolean isVehicleNotFound() { return type == AssignmentType.VEHICLE_NOT_FOUND; }

        private enum AssignmentType {
            ASSIGNED, UNCHANGED, FAILED, VEHICLE_NOT_FOUND
        }
    }
}