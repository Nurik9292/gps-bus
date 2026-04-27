package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.geospatial.application.usecase.DetectGarageTransitionsUseCase;
import biz.ugur.busroutebackend.geospatial.application.usecase.ProcessGarageEntryUseCase;
import biz.ugur.busroutebackend.geospatial.application.usecase.ProcessGarageExitUseCase;
import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.service.VehicleDirectionDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DirectionGarageStage {

    private static final int GARAGE_DETECTION_CONCURRENCY = 2;

    private final VehicleDirectionDetectionService directionDetectionService;
    private final DetectGarageTransitionsUseCase garageTransitionsUseCase;
    private final ProcessGarageEntryUseCase processGarageEntryUseCase;
    private final ProcessGarageExitUseCase processGarageExitUseCase;

    public DirectionGarageStage(VehicleDirectionDetectionService directionDetectionService,
                                 DetectGarageTransitionsUseCase garageTransitionsUseCase,
                                 ProcessGarageEntryUseCase processGarageEntryUseCase,
                                 ProcessGarageExitUseCase processGarageExitUseCase) {
        this.directionDetectionService = directionDetectionService;
        this.garageTransitionsUseCase = garageTransitionsUseCase;
        this.processGarageEntryUseCase = processGarageEntryUseCase;
        this.processGarageExitUseCase = processGarageExitUseCase;
    }

    public Mono<List<Vehicle>> apply(List<Vehicle> vehiclesToUpdate, List<Vehicle> vehiclesForDetection) {
        Mono<List<Vehicle>> vehiclesWithDirections = vehiclesToUpdate.isEmpty()
                ? Mono.just(List.of())
                : directionDetectionService.updateVehicleDirectionsBatch(vehiclesForDetection)
                        .map(detectedVehicles -> {
                            Map<String, Vehicle> detectedById = detectedVehicles.stream()
                                    .collect(Collectors.toMap(v -> v.getId().getValue(), v -> v));
                            return vehiclesToUpdate.stream()
                                    .map(v -> {
                                        Vehicle detected = detectedById.get(v.getId().getValue());
                                        if (detected != null && detected.getCurrentDirection() != null) {
                                            return v.toBuilder()
                                                    .currentDirection(detected.getCurrentDirection())
                                                    .lastStopSequence(detected.getLastStopSequence())
                                                    .build();
                                        }
                                        return v;
                                    })
                                    .collect(Collectors.toList());
                        })
                        .doOnNext(updated -> log.debug("Direction detection completed for {} vehicles", updated.size()));

        return vehiclesWithDirections.flatMap(vehicles -> {
            if (vehicles.isEmpty()) {
                return Mono.just(List.of());
            }

            return Flux.fromIterable(vehicles)
                    .flatMap(this::detectAndHandleGarageTransition, GARAGE_DETECTION_CONCURRENCY)
                    .collectList()
                    .doOnNext(updated -> log.debug("Garage detection completed for {} vehicles", updated.size()));
        });
    }

    private Mono<Vehicle> detectAndHandleGarageTransition(Vehicle vehicle) {
        if (vehicle == null || !vehicle.hasPosition()) {
            return Mono.just(Objects.requireNonNull(vehicle));
        }

        Coordinates position = vehicle.toCoordinates();
        Double speed = vehicle.getSpeedKmh();

        boolean wasInGarage = vehicle.isCurrentlyInGarage();

        if (wasInGarage) {
            return checkGarageExit(vehicle, position, speed);
        } else {
            return checkGarageEntry(vehicle, position, speed);
        }
    }

    private Mono<Vehicle> checkGarageEntry(Vehicle vehicle, Coordinates position, Double speed) {
        return garageTransitionsUseCase.isVehicleEntryCandidate(position, speed)
                .flatMap(isCandidate -> {
                    if (!isCandidate) {
                        return Mono.just(vehicle);
                    }

                    return garageTransitionsUseCase.findGarageAtPosition(position)
                            .flatMap(garageOpt -> {
                                if (garageOpt.isPresent()) {
                                    Garage garage = garageOpt.get();
                                    log.info("Vehicle {} entering garage {} at speed {} km/h",
                                            vehicle.getLicensePlate(),
                                            garage.getName(),
                                            speed);
                                    return processGarageEntryUseCase.execute(
                                            new ProcessGarageEntryUseCase.Request(vehicle, garage.getId().getValue().toString())
                                    );
                                }
                                return Mono.just(vehicle);
                            });
                });
    }

    private Mono<Vehicle> checkGarageExit(Vehicle vehicle, Coordinates position, Double speed) {
        String lastGarageId = vehicle.getLastGarageId();
        if (lastGarageId == null) {
            return Mono.just(vehicle);
        }

        return garageTransitionsUseCase.getAllActiveGarages()
                .filter(garage -> garage.getId().getValue().toString().equals(lastGarageId))
                .next()
                .flatMap(garage -> garageTransitionsUseCase.hasVehicleExitedGarage(garage, position, speed)
                        .flatMap(hasExited -> {
                            if (hasExited) {
                                log.info("Vehicle {} exiting garage {} at speed {} km/h",
                                        vehicle.getLicensePlate(),
                                        garage.getName(),
                                        speed);
                                return processGarageExitUseCase.execute(
                                        new ProcessGarageExitUseCase.Request(vehicle)
                                );
                            }
                            return Mono.just(vehicle);
                        }))
                .defaultIfEmpty(vehicle);
    }
}
