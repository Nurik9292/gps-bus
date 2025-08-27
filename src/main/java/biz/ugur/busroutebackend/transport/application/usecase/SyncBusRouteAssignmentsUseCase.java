package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
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
public class SyncBusRouteAssignmentsUseCase extends BaseUseCase<List<BusInfoDTO>, SyncBusRouteAssignmentsUseCase.BusRouteAssignmentResult> {

    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;

    public SyncBusRouteAssignmentsUseCase(VehicleRepository vehicleRepository,
                                          BusRouteRepository busRouteRepository,
                                          CorrelationContextService correlationContextService,
                                          EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Mono<BusRouteAssignmentResult> process(List<BusInfoDTO> request) {
        return processInternal(request);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private  Mono<BusRouteAssignmentResult> processInternal(List<BusInfoDTO> busInfos) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Syncing route assignments for {} buses - CorrelationId: {}", busInfos.size(), correlationId);

            return Flux.fromIterable(busInfos)
                    .flatMap(this::assignBusToRoute)
                    .collectList()
                    .map(this::createResult)
                    .doOnSuccess(result -> log.info("Route assignment sync completed: {}", result));
        });
    }

    private Mono<AssignmentStatus> assignBusToRoute(BusInfoDTO busInfo) {
        return vehicleRepository.findByLicensePlate(busInfo.getCarNumber())
                .flatMap(vehicle ->
                        busRouteRepository.findByRouteNumber(busInfo.getRouteNumber())
                                .flatMap(busRoute -> {
                                    try {
                                        BusRouteId routeId = busRoute.getId();
                                        if (routeId.equals(vehicle.getAssignedRouteId()) &&
                                                busInfo.getRouteNumber().equals(vehicle.getRouteNumber())) {
                                            return Mono.just(AssignmentStatus.unchanged(vehicle.getLicensePlate(), routeId.getValue()));
                                        }
                                        vehicle.assignToRoute(routeId);
                                        vehicle.updateCachedRouteNumber(busInfo.getRouteNumber());

                                        return vehicleRepository.save(vehicle)
                                                .map(savedVehicle -> AssignmentStatus.assigned(
                                                        savedVehicle.getLicensePlate(),
                                                        busInfo.getRouteNumber()
                                                ));

                                    } catch (Exception e) {
                                        log.warn("Failed to assign vehicle {} to route {}: {}",
                                                busInfo.getCarNumber(), busInfo.getRouteNumber(), e.getMessage());
                                        return Mono.just(AssignmentStatus.failed(busInfo.getCarNumber(), e.getMessage()));
                                    }
                                })
                                .switchIfEmpty(Mono.fromCallable(() -> {
                                    log.warn("Route {} not found in system for vehicle {}",
                                            busInfo.getRouteNumber(), busInfo.getCarNumber());
                                    return AssignmentStatus.routeNotFound(busInfo.getCarNumber(), busInfo.getRouteNumber());
                                }))
                )
                .switchIfEmpty(Mono.just(AssignmentStatus.vehicleNotFound(busInfo.getCarNumber())));
    }

    private BusRouteAssignmentResult createResult(List<AssignmentStatus> statuses) {
        long assigned = statuses.stream().mapToLong(s -> s.isAssigned() ? 1 : 0).sum();
        long unchanged = statuses.stream().mapToLong(s -> s.isUnchanged() ? 1 : 0).sum();
        long failed = statuses.stream().mapToLong(s -> s.isFailed() ? 1 : 0).sum();
        long vehicleNotFound = statuses.stream().mapToLong(s -> s.isVehicleNotFound() ? 1 : 0).sum();
        long routeNotFound = statuses.stream().mapToLong(s -> s.isRouteNotFound() ? 1 : 0).sum();

        return new BusRouteAssignmentResult(assigned, unchanged, failed, vehicleNotFound, routeNotFound, Instant.now());
    }



    public record BusRouteAssignmentResult(
            long assignedCount,
            long unchangedCount,
            long failedCount,
            long vehicleNotFoundCount,
            long routeNotFoundCount,
            Instant processedAt) {

    }

    public static class AssignmentStatus {
        private final String licensePlate;
        private final String routeInfo; //
        private final String errorMessage;
        private final AssignmentType type;

        private AssignmentStatus(String licensePlate, String routeInfo, String errorMessage, AssignmentType type) {
            this.licensePlate = licensePlate;
            this.routeInfo = routeInfo;
            this.errorMessage = errorMessage;
            this.type = type;
        }

        public static AssignmentStatus assigned(String licensePlate, String routeNumber) {
            return new AssignmentStatus(licensePlate, routeNumber, null, AssignmentType.ASSIGNED);
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

        public static AssignmentStatus routeNotFound(String licensePlate, String routeNumber) {
            return new AssignmentStatus(licensePlate, routeNumber, "Route not found in system", AssignmentType.ROUTE_NOT_FOUND);
        }

        public boolean isAssigned() { return type == AssignmentType.ASSIGNED; }
        public boolean isUnchanged() { return type == AssignmentType.UNCHANGED; }
        public boolean isFailed() { return type == AssignmentType.FAILED; }
        public boolean isVehicleNotFound() { return type == AssignmentType.VEHICLE_NOT_FOUND; }
        public boolean isRouteNotFound() { return type == AssignmentType.ROUTE_NOT_FOUND; } // ✅ НОВЫЙ

        private enum AssignmentType {
            ASSIGNED, UNCHANGED, FAILED, VEHICLE_NOT_FOUND, ROUTE_NOT_FOUND
        }
    }
}