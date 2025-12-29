package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.VehicleData;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleAlreadyExistsException;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateVehicleUseCase implements UseCase<Mono<CreateVehicleUseCase.Command>, Mono<VehicleData>> {

    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;

    public record Command(
            String deviceId,
            String licensePlate,
            String assignedRouteId,
            Boolean isActive
    ) {}

    @Override
    @Transactional
    public Mono<VehicleData> execute(Mono<Command> commandMono) {
        return commandMono.flatMap(command -> {
            log.info("Creating vehicle with device ID: {} and license plate: {}",
                    command.deviceId(), command.licensePlate());

            return vehicleRepository.existsByDeviceId(command.deviceId())
                    .flatMap(existsByDevice -> {
                        if (existsByDevice) {
                            return Mono.error(new VehicleAlreadyExistsException(
                                    "Vehicle with device ID '" + command.deviceId() + "' already exists"));
                        }
                        return vehicleRepository.existsByLicensePlate(command.licensePlate());
                    })
                    .flatMap(existsByPlate -> {
                        if (existsByPlate) {
                            return Mono.error(new VehicleAlreadyExistsException(
                                    "Vehicle with license plate '" + command.licensePlate() + "' already exists"));
                        }
                        return createVehicle(command);
                    });
        });
    }

    private Mono<VehicleData> createVehicle(Command command) {
        Vehicle vehicle = Vehicle.create(command.deviceId(), command.licensePlate());

        if (Boolean.FALSE.equals(command.isActive())) {
            vehicle = vehicle.deactivate();
        }

        if (command.assignedRouteId() != null && !command.assignedRouteId().isEmpty()) {
            BusRouteId routeId = BusRouteId.of(command.assignedRouteId());
            Vehicle finalVehicle = vehicle;

            return busRouteRepository.findById(routeId)
                    .flatMap(route -> {
                        Vehicle vehicleWithRoute = finalVehicle.toBuilder()
                                .assignedRouteId(routeId)
                                .routeNumber(route.getRouteNumber())
                                .build();
                        return vehicleRepository.save(vehicleWithRoute);
                    })
                    .switchIfEmpty(vehicleRepository.save(finalVehicle))
                    .map(VehicleData::fromDomain)
                    .doOnSuccess(v -> log.info("Vehicle created successfully: {}", v.id()));
        }

        return vehicleRepository.save(vehicle)
                .map(VehicleData::fromDomain)
                .doOnSuccess(v -> log.info("Vehicle created successfully: {}", v.id()));
    }
}
