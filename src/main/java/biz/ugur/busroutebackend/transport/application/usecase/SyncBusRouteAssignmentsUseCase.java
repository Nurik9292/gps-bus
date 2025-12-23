package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private Mono<BusRouteAssignmentResult> processInternal(List<BusInfoDTO> busInfos) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Syncing route assignments for {} buses (diff-based) - CorrelationId: {}",
                    busInfos.size(), correlationId);

            Mono<Map<String, Vehicle>> currentAssignmentsMono = vehicleRepository.findAllWithRouteAssignment();
            Mono<Map<String, BusRoute>> routesMono = busRouteRepository.findActiveRoutes()
                    .collectMap(BusRoute::getRouteNumber);

            return Mono.zip(currentAssignmentsMono, routesMono)
                    .flatMap(tuple -> {
                        Map<String, Vehicle> currentAssignments = tuple.getT1();
                        Map<String, BusRoute> routesByNumber = tuple.getT2();

                        log.debug("Current DB assignments: {}, Active routes: {}",
                                currentAssignments.size(), routesByNumber.size());

                        Set<String> apiLicensePlates = busInfos.stream()
                                .map(BusInfoDTO::getCarNumber)
                                .filter(plate -> plate != null && !plate.isBlank())
                                .collect(Collectors.toSet());

                        Set<String> toUnassign = new HashSet<>(currentAssignments.keySet());
                        toUnassign.removeAll(apiLicensePlates);

                        log.info("Diff calculated: toAssign={}, toUnassign={}",
                                busInfos.size(), toUnassign.size());

                        Mono<AssignmentStats> assignMono = processAssignments(busInfos, routesByNumber);
                        Mono<Integer> unassignMono = processUnassignments(toUnassign);

                        return Mono.zip(assignMono, unassignMono)
                                .map(results -> createResult(results.getT1(), results.getT2()));
                    })
                    .doOnSuccess(result -> log.info("Route assignment sync completed: {}", result))
                    .doOnError(error -> log.error("Route assignment sync failed", error));
        });
    }

    private Mono<AssignmentStats> processAssignments(List<BusInfoDTO> busInfos, Map<String, BusRoute> routesByNumber) {
        return Flux.fromIterable(busInfos)
                .flatMap(busInfo -> assignBusToRoute(busInfo, routesByNumber))
                .reduce(new AssignmentStats(), AssignmentStats::merge);
    }

    private Mono<AssignmentStatus> assignBusToRoute(BusInfoDTO busInfo, Map<String, BusRoute> routesByNumber) {
        String licensePlate = busInfo.getCarNumber();
        String routeNumber = busInfo.getRouteNumber();

        if (licensePlate == null || licensePlate.isBlank()) {
            return Mono.just(AssignmentStatus.invalid("Empty license plate"));
        }

        if (routeNumber == null || routeNumber.isBlank()) {
            return Mono.just(AssignmentStatus.invalid("Empty route number for " + licensePlate));
        }

        BusRoute route = routesByNumber.get(routeNumber);
        if (route == null) {
            log.warn("Route {} not found in system for vehicle {}", routeNumber, licensePlate);
            return Mono.just(AssignmentStatus.routeNotFound(licensePlate, routeNumber));
        }

        BusRouteId routeId = route.getId();

        return vehicleRepository.updateRouteAssignment(licensePlate, routeId, routeNumber)
                .map(updated -> {
                    if (updated > 0) {
                        return AssignmentStatus.assigned(licensePlate, routeNumber);
                    } else {
                        return AssignmentStatus.vehicleNotFound(licensePlate);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to assign vehicle {} to route {}: {}",
                            licensePlate, routeNumber, error.getMessage());
                    return Mono.just(AssignmentStatus.failed(licensePlate, error.getMessage()));
                });
    }

    private Mono<Integer> processUnassignments(Set<String> licensePlates) {
        if (licensePlates.isEmpty()) {
            return Mono.just(0);
        }

        log.info("Unassigning {} vehicles that are no longer in API response: {}",
                licensePlates.size(), licensePlates);

        return vehicleRepository.clearRouteAssignmentsByLicensePlates(List.copyOf(licensePlates));
    }

    private BusRouteAssignmentResult createResult(AssignmentStats stats, int unassignedCount) {
        return new BusRouteAssignmentResult(
                stats.assignedCount,
                stats.vehicleNotFoundCount,
                stats.routeNotFoundCount,
                stats.failedCount,
                stats.invalidCount,
                unassignedCount,
                Instant.now()
        );
    }

    public record BusRouteAssignmentResult(
            long assignedCount,
            long vehicleNotFoundCount,
            long routeNotFoundCount,
            long failedCount,
            long invalidCount,
            long unassignedCount,
            Instant processedAt) {

    }

    private static class AssignmentStats {
        long assignedCount = 0;
        long vehicleNotFoundCount = 0;
        long routeNotFoundCount = 0;
        long failedCount = 0;
        long invalidCount = 0;

        AssignmentStats merge(AssignmentStatus status) {
            switch (status.type) {
                case ASSIGNED -> assignedCount++;
                case VEHICLE_NOT_FOUND -> vehicleNotFoundCount++;
                case ROUTE_NOT_FOUND -> routeNotFoundCount++;
                case FAILED -> failedCount++;
                case INVALID -> invalidCount++;
            }
            return this;
        }
    }

    private static class AssignmentStatus {
        private final String licensePlate;
        private final String routeNumber;
        private final String errorMessage;
        private final AssignmentType type;

        private AssignmentStatus(String licensePlate, String routeNumber, String errorMessage, AssignmentType type) {
            this.licensePlate = licensePlate;
            this.routeNumber = routeNumber;
            this.errorMessage = errorMessage;
            this.type = type;
        }

        public static AssignmentStatus assigned(String licensePlate, String routeNumber) {
            return new AssignmentStatus(licensePlate, routeNumber, null, AssignmentType.ASSIGNED);
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

        public static AssignmentStatus invalid(String message) {
            return new AssignmentStatus(null, null, message, AssignmentType.INVALID);
        }

        private enum AssignmentType {
            ASSIGNED, VEHICLE_NOT_FOUND, ROUTE_NOT_FOUND, FAILED, INVALID
        }
    }
}
