package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Phase 1.2: DeleteBusRouteUseCase - Business Validation
 *
 * Use Case: Delete a bus route
 *
 * Business Rules:
 * 1. Route must exist
 * 2. Route must not have active vehicles assigned
 * 3. If route has active vehicles, deletion is prevented
 *
 * SOLID Principles:
 * - SRP: Single responsibility - delete route with validation
 * - DIP: Depends on abstractions (BusRouteRepository, VehicleRepository)
 *
 * Phase 2.3: Added Security Context Integration for audit logging
 */
@Service
@Slf4j
public class DeleteBusRouteUseCase extends BaseUseCase<String, Void> {

    private final BusRouteRepository busRouteRepository;
    private final VehicleRepository vehicleRepository;
    private final SecurityContextService securityContextService;

    public DeleteBusRouteUseCase(BusRouteRepository busRouteRepository,
                                 VehicleRepository vehicleRepository,
                                 SecurityContextService securityContextService,
                                 CorrelationContextService correlationContextService,
                                 EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.busRouteRepository = busRouteRepository;
        this.vehicleRepository = vehicleRepository;
        this.securityContextService = securityContextService;
    }


    @Override
    protected Mono<Void> process(String routeId) {
        return processInternal(routeId);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Void> processInternal(String routeId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {

            // Phase 2.3: Get current user for audit logging
            return securityContextService.getCurrentUsername()
                .defaultIfEmpty("system")
                .flatMap(username -> {
                    log.info("[DeleteBusRoute] CorrelationId: {} - User: {} - Deleting route: {}",
                            correlationId, username, routeId);

                    return busRouteRepository.findById(BusRouteId.of(routeId))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus route not found: " + routeId)))
                    .flatMap(busRoute -> {
                        log.debug("[DeleteBusRoute] Route found: {}, checking for active vehicles", routeId);

                        // Phase 1.2: Business Validation - Check if route has active vehicles
                        return vehicleRepository.findByAssignedRouteId(BusRouteId.of(routeId))
                                .filter(vehicle -> Boolean.TRUE.equals(vehicle.getIsActive()))
                                .count()
                                .flatMap(activeVehicleCount -> {
                                    if (activeVehicleCount > 0) {
                                        log.warn("[DeleteBusRoute] Cannot delete route {}: {} active vehicles assigned",
                                                routeId, activeVehicleCount);
                                        return Mono.error(new IllegalStateException(
                                                String.format("Cannot delete route %s: %d active vehicles are assigned to this route. " +
                                                        "Please deactivate or reassign vehicles before deleting the route.",
                                                        routeId, activeVehicleCount)
                                        ));
                                    }

                                    log.debug("[DeleteBusRoute] No active vehicles found, proceeding with deletion");
                                    return busRouteRepository.deleteById(BusRouteId.of(routeId));
                                });
                    })
                    .flatMap(v -> {
                        // Phase 2.3: Audit log successful deletion
                        return securityContextService.logAudit("DELETE_ROUTE", "route:" + routeId, correlationId.value())
                                .thenReturn(v);
                    })
                    .doOnSuccess(v -> log.info("[DeleteBusRoute] Route deleted successfully: {}", routeId))
                    .doOnError(error -> log.error("[DeleteBusRoute] Failed to delete route {}: {}",
                            routeId, error.getMessage()));
                });
        });
    }
}