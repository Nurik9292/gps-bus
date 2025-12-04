package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteVehicleUseCase implements UseCase<Mono<String>, Mono<Void>> {

    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional
    public Mono<Void> execute(Mono<String> vehicleIdMono) {
        return vehicleIdMono.flatMap(vehicleId -> {
            log.info("Deleting vehicle: {}", vehicleId);

            return vehicleRepository.findById(VehicleId.of(vehicleId))
                    .switchIfEmpty(Mono.error(new VehicleNotFoundException(
                            "Vehicle not found with id: " + vehicleId)))
                    .flatMap(vehicle -> vehicleRepository.deleteById(VehicleId.of(vehicleId)))
                    .doOnSuccess(v -> log.info("Vehicle deleted successfully: {}", vehicleId));
        });
    }
}
