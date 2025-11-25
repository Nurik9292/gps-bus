package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.infrastructure.external.GpsApiClient;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Use case for fetching GPS positions of all vehicles
 * Retrieves device IDs from database and fetches positions from external GPS API
 */
@Service
@Slf4j
public class GetAllVehiclePositionsUseCase {

    private final VehicleRepository vehicleRepository;
    private final GpsApiClient gpsApiClient;

    public GetAllVehiclePositionsUseCase(VehicleRepository vehicleRepository,
                                         GpsApiClient gpsApiClient) {
        this.vehicleRepository = vehicleRepository;
        this.gpsApiClient = gpsApiClient;
    }

    /**
     * Execute the use case with optional filtering
     *
     * @param limit Maximum number of vehicles (null = all)
     * @param active Filter for active vehicles only (null = all)
     * @return List of GPS positions
     */
    public Mono<List<GpsPositionDTO>> execute(Integer limit, Boolean active) {
        log.debug("Fetching vehicle positions - limit: {}, active: {}", limit, active);

        return vehicleRepository.findAllDeviceIds()
                .collectList()
                .flatMap(deviceIds -> {
                    if (deviceIds.isEmpty()) {
                        log.warn("No device IDs found in database");
                        return Mono.<List<GpsPositionDTO>>just(List.of());
                    }

                    log.info("Found {} device IDs, fetching GPS positions", deviceIds.size());

                    // Apply limit before fetching from external API (to reduce API calls)
                    List<String> limitedDeviceIds = limit != null && limit > 0 && limit < deviceIds.size()
                            ? deviceIds.subList(0, limit)
                            : deviceIds;

                    return gpsApiClient.fetchVehiclePositionsByIds(limitedDeviceIds)
                            .flatMapMany(Flux::fromIterable)
                            .filter(position -> {
                                // Filter by active status if requested
                                if (active != null && active) {
                                    return position.getAttributes() != null &&
                                           Boolean.TRUE.equals(position.getAttributes().getMotion());
                                }
                                return true;
                            })
                            .collectList();
                })
                .doOnSuccess(positions -> log.info("Successfully fetched {} vehicle positions", positions.size()))
                .doOnError(error -> log.error("Failed to fetch vehicle positions", error));
    }
}
