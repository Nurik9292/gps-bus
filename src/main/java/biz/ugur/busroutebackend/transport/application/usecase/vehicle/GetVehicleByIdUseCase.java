package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.VehicleData;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetVehicleByIdUseCase implements UseCase<Mono<String>, Mono<VehicleData>> {

    private final VehicleRepository vehicleRepository;

    @Override
    public Mono<VehicleData> execute(Mono<String> vehicleIdMono) {
        return vehicleIdMono.flatMap(vehicleId -> {
            log.debug("Getting vehicle by id: {}", vehicleId);

            return vehicleRepository.findById(VehicleId.of(vehicleId))
                    .switchIfEmpty(Mono.error(new VehicleNotFoundException(
                            "Vehicle not found with id: " + vehicleId)))
                    .map(VehicleData::fromDomain);
        });
    }
}
