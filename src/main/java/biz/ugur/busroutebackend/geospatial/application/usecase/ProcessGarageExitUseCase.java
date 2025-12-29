package biz.ugur.busroutebackend.geospatial.application.usecase;

import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.repository.GarageRepository;
import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.event.VehicleLeftGarageEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRouteAutoChangedEvent;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;


@Service
@Slf4j
public class ProcessGarageExitUseCase extends BaseUseCase<ProcessGarageExitUseCase.Request, Vehicle> {

    private final VehicleRepository vehicleRepository;
    private final GarageRepository garageRepository;
    private final RouteAssignmentRepository routeAssignmentRepository;
    private final BusRouteRepository busRouteRepository;

    public ProcessGarageExitUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleRepository vehicleRepository,
            GarageRepository garageRepository,
            RouteAssignmentRepository routeAssignmentRepository,
            BusRouteRepository busRouteRepository) {
        super(correlationService, eventBus);
        this.vehicleRepository = vehicleRepository;
        this.garageRepository = garageRepository;
        this.routeAssignmentRepository = routeAssignmentRepository;
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Mono<Vehicle> process(Request request) {
        Vehicle vehicle = request.vehicle();

        if (!vehicle.isCurrentlyInGarage()) {
            log.debug("Vehicle {} is not in garage, skipping exit processing",
                    vehicle.getLicensePlate());
            return Mono.just(vehicle);
        }

        String lastGarageId = vehicle.getLastGarageId();
        if (lastGarageId == null) {
            log.warn("Vehicle {} is marked as in garage but has no lastGarageId",
                    vehicle.getLicensePlate());
            return Mono.just(vehicle.exitGarage());
        }

        return garageRepository.findById(new GarageId(UUID.fromString(lastGarageId)))
                .flatMap(garage -> processExit(vehicle, garage))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Garage not found: {}, processing exit without garage info", lastGarageId);
                    return processExitWithoutGarage(vehicle);
                }));
    }

    private Mono<Vehicle> processExit(Vehicle vehicle, Garage garage) {
        LocalDateTime exitTime = LocalDateTime.now();
        LocalDateTime entryTime = vehicle.getGarageEntryTime();

        Vehicle exitedVehicle = vehicle.exitGarage();

        return findAndApplyShiftAssignment(exitedVehicle)
                .flatMap(vehicleWithRoute -> vehicleRepository.save(vehicleWithRoute))
                .doOnSuccess(savedVehicle -> publishEvents(savedVehicle, garage, exitTime, entryTime, vehicle));
    }

    private Mono<Vehicle> processExitWithoutGarage(Vehicle vehicle) {
        Vehicle exitedVehicle = vehicle.exitGarage();

        return findAndApplyShiftAssignment(exitedVehicle)
                .flatMap(vehicleRepository::save)
                .doOnSuccess(savedVehicle -> {
                    VehicleLeftGarageEvent event = new VehicleLeftGarageEvent(
                            savedVehicle.getId().getValue(),
                            savedVehicle.getLicensePlate(),
                            vehicle.getLastGarageId(),
                            "Unknown",
                            savedVehicle.getCurrentLatitude(),
                            savedVehicle.getCurrentLongitude(),
                            LocalDateTime.now(),
                            vehicle.getGarageEntryTime(),
                            savedVehicle.getAssignedRouteId() != null
                                    ? savedVehicle.getAssignedRouteId().getValue() : null,
                            savedVehicle.getRouteNumber()
                    );
                    eventBus.publish(event);
                });
    }

    private Mono<Vehicle> findAndApplyShiftAssignment(Vehicle vehicle) {
        ShiftType currentShift = ShiftType.getCurrentShift();
        LocalDate today = LocalDate.now();

        return routeAssignmentRepository.findActiveByVehicleAndDateAndShift(vehicle.getId(), today, currentShift)
                .filter(RouteAssignment::isCurrentlyValid)
                .flatMap(assignment -> applyShiftAssignment(vehicle, assignment))
                .defaultIfEmpty(vehicle)
                .doOnNext(v -> {
                    if (v.hasAssignedRoute()) {
                        log.info("Vehicle {} assigned to route {} from shift assignment (shift: {})",
                                v.getLicensePlate(), v.getRouteNumber(), currentShift);
                    } else {
                        log.debug("No active shift assignment found for vehicle {} on shift {}",
                                v.getLicensePlate(), currentShift);
                    }
                });
    }

    private Mono<Vehicle> applyShiftAssignment(Vehicle vehicle, RouteAssignment assignment) {
        return busRouteRepository.findById(assignment.getRouteId())
                .map(route -> {
                    String previousRouteId = vehicle.getAssignedRouteId() != null
                            ? vehicle.getAssignedRouteId().getValue() : null;
                    String previousRouteNumber = vehicle.getRouteNumber();

                    Vehicle assignedVehicle = vehicle.toBuilder()
                            .assignedRouteId(assignment.getRouteId())
                            .routeNumber(route.getRouteNumber())
                            .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                            .routeConfidence(100)
                            .build();

                    if (shouldPublishRouteChange(previousRouteId, assignment.getRouteId().getValue())) {
                        VehicleRouteAutoChangedEvent event = new VehicleRouteAutoChangedEvent(
                                vehicle.getId().getValue(),
                                vehicle.getLicensePlate(),
                                previousRouteId,
                                previousRouteNumber,
                                assignment.getRouteId().getValue(),
                                route.getRouteNumber(),
                                RouteSource.SHIFT_ASSIGNMENT,
                                100,
                                "Assigned from shift assignment on garage exit"
                        );
                        eventBus.publish(event);
                    }

                    return assignedVehicle;
                })
                .defaultIfEmpty(vehicle);
    }

    private boolean shouldPublishRouteChange(String previousRouteId, String newRouteId) {
        if (previousRouteId == null && newRouteId != null) {
            return true;
        }
        return previousRouteId != null && !previousRouteId.equals(newRouteId);
    }

    private void publishEvents(Vehicle savedVehicle, Garage garage,
                               LocalDateTime exitTime, LocalDateTime entryTime, Vehicle originalVehicle) {
        VehicleLeftGarageEvent event = new VehicleLeftGarageEvent(
                savedVehicle.getId().getValue(),
                savedVehicle.getLicensePlate(),
                garage.getId().getValue().toString(),
                garage.getName(),
                savedVehicle.getCurrentLatitude(),
                savedVehicle.getCurrentLongitude(),
                exitTime,
                entryTime,
                savedVehicle.getAssignedRouteId() != null
                        ? savedVehicle.getAssignedRouteId().getValue() : null,
                savedVehicle.getRouteNumber()
        );
        eventBus.publish(event);

        log.info("Vehicle {} exited garage {} at {}{}",
                savedVehicle.getLicensePlate(),
                garage.getName(),
                exitTime,
                savedVehicle.hasAssignedRoute()
                        ? ", assigned to route " + savedVehicle.getRouteNumber()
                        : "");
    }

    @Override
    protected String getBoundContext() {
        return "geospatial";
    }

    public record Request(Vehicle vehicle) {}
}
