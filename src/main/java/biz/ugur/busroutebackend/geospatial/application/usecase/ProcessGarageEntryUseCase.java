package biz.ugur.busroutebackend.geospatial.application.usecase;

import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.repository.GarageRepository;
import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.event.VehicleEnteredGarageEvent;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Use case for processing vehicle entry into a garage.
 *
 * This use case:
 * 1. Updates the vehicle's garage tracking fields
 * 2. Publishes VehicleEnteredGarageEvent
 */
@Service
@Slf4j
public class ProcessGarageEntryUseCase extends BaseUseCase<ProcessGarageEntryUseCase.Request, Vehicle> {

    private final VehicleRepository vehicleRepository;
    private final GarageRepository garageRepository;

    public ProcessGarageEntryUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleRepository vehicleRepository,
            GarageRepository garageRepository) {
        super(correlationService, eventBus);
        this.vehicleRepository = vehicleRepository;
        this.garageRepository = garageRepository;
    }

    @Override
    protected Mono<Vehicle> process(Request request) {
        return garageRepository.findById(new GarageId(UUID.fromString(request.garageId())))
                .flatMap(garage -> processEntry(request.vehicle(), garage))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Garage not found: {}", request.garageId());
                    return Mono.just(request.vehicle());
                }));
    }

    private Mono<Vehicle> processEntry(Vehicle vehicle, Garage garage) {
        if (vehicle.isCurrentlyInGarage()) {
            log.debug("Vehicle {} is already in garage, skipping entry processing",
                    vehicle.getLicensePlate());
            return Mono.just(vehicle);
        }

        LocalDateTime entryTime = LocalDateTime.now();
        Vehicle enteredVehicle = vehicle.enterGarage(garage.getId().getValue().toString());

        return vehicleRepository.save(enteredVehicle)
                .doOnSuccess(savedVehicle -> {
                    VehicleEnteredGarageEvent event = new VehicleEnteredGarageEvent(
                            savedVehicle.getId().getValue(),
                            savedVehicle.getLicensePlate(),
                            garage.getId().getValue().toString(),
                            garage.getName(),
                            savedVehicle.getCurrentLatitude(),
                            savedVehicle.getCurrentLongitude(),
                            entryTime
                    );
                    eventBus.publish(event);

                    log.info("Vehicle {} entered garage {} at {}",
                            savedVehicle.getLicensePlate(),
                            garage.getName(),
                            entryTime);
                });
    }

    @Override
    protected String getBoundContext() {
        return "geospatial";
    }

    public record Request(Vehicle vehicle, String garageId) {}
}
