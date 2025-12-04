package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.VehicleData;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateVehicleUseCase implements UseCase<Mono<UpdateVehicleUseCase.Command>, Mono<VehicleData>> {

    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;

    public record Command(
            String vehicleId,
            String deviceId,
            String licensePlate,
            String assignedRouteId,
            Boolean isActive
    ) {}

    @Override
    @Transactional
    public Mono<VehicleData> execute(Mono<Command> commandMono) {
        return commandMono.flatMap(command -> {
            log.info("Updating vehicle: {}", command.vehicleId());

            return vehicleRepository.findById(VehicleId.of(command.vehicleId()))
                    .switchIfEmpty(Mono.error(new VehicleNotFoundException(
                            "Vehicle not found with id: " + command.vehicleId())))
                    .flatMap(vehicle -> updateVehicle(vehicle, command));
        });
    }

    private Mono<VehicleData> updateVehicle(Vehicle vehicle, Command command) {
        Vehicle updatedVehicle = vehicle;

        // Update device ID if provided
        if (command.deviceId() != null && !command.deviceId().equals(vehicle.getDeviceId())) {
            updatedVehicle = updatedVehicle.updateDeviceId(command.deviceId());
        }

        // Update active status
        if (command.isActive() != null) {
            if (command.isActive() && !Boolean.TRUE.equals(vehicle.getIsActive())) {
                updatedVehicle = updatedVehicle.activate();
            } else if (!command.isActive() && Boolean.TRUE.equals(vehicle.getIsActive())) {
                updatedVehicle = updatedVehicle.deactivate();
            }
        }

        // Handle route assignment
        if (command.assignedRouteId() != null && !command.assignedRouteId().isEmpty()) {
            BusRouteId routeId = BusRouteId.of(command.assignedRouteId());
            Vehicle finalUpdatedVehicle = updatedVehicle;

            return busRouteRepository.findById(routeId)
                    .flatMap(route -> {
                        Vehicle vehicleWithRoute = finalUpdatedVehicle
                                .assignToRoute(routeId)
                                .updateCachedRouteNumber(route.getRouteNumber());
                        return vehicleRepository.save(vehicleWithRoute);
                    })
                    .switchIfEmpty(vehicleRepository.save(finalUpdatedVehicle))
                    .map(VehicleData::fromDomain)
                    .doOnSuccess(v -> log.info("Vehicle updated successfully: {}", v.id()));
        } else if (command.assignedRouteId() != null && command.assignedRouteId().isEmpty()) {
            // Unassign from route if empty string provided
            updatedVehicle = updatedVehicle.unassignFromRoute();
        }

        return vehicleRepository.save(updatedVehicle)
                .map(VehicleData::fromDomain)
                .doOnSuccess(v -> log.info("Vehicle updated successfully: {}", v.id()));
    }
}
