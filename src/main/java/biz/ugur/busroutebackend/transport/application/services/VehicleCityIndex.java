package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class VehicleCityIndex {

    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(15);

    private final VehicleRepository vehicleRepository;

    private volatile Map<String, String> cityByVehicleId = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public VehicleCityIndex(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public Optional<String> cityOf(String vehicleId) {
        if (vehicleId == null) {
            return Optional.empty();
        }
        refreshIfStale();
        String cityId = cityByVehicleId.get(vehicleId);
        return cityId == null || cityId.isBlank() ? Optional.empty() : Optional.of(cityId);
    }

    private void refreshIfStale() {
        if (Instant.now().isBefore(loadedAt.plus(REFRESH_INTERVAL))) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        vehicleRepository.findActiveVehicles()
                .collectMap(
                        vehicle -> vehicle.getId().getValue(),
                        vehicle -> vehicle.getCityId() != null ? vehicle.getCityId().getValue() : "")
                .timeout(Duration.ofSeconds(20))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        loaded -> {
                            cityByVehicleId = Map.copyOf(loaded);
                            loadedAt = Instant.now();
                            refreshing.set(false);
                            log.debug("Vehicle city index refreshed: {} vehicles", loaded.size());
                        },
                        error -> {
                            refreshing.set(false);
                            log.warn("Vehicle city index refresh failed: {}", error.getMessage());
                        });
    }
}
