package biz.ugur.busroutebackend.transport.scheduler;

import biz.ugur.busroutebackend.transport.application.usecase.DetectRouteByGpsUseCase;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


@Slf4j
@Component
@ConditionalOnProperty(prefix = "business.gps-route-detection", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GpsRouteDetectionScheduler {

    private final VehicleRepository vehicleRepository;
    private final DetectRouteByGpsUseCase detectRouteByGpsUseCase;
    private final VehicleGpsHistoryService gpsHistoryService;

    @Value("${business.gps-route-detection.min-gps-points:5}")
    private int minGpsPoints;

    private final AtomicBoolean detectionInProgress = new AtomicBoolean(false);
    private final AtomicLong lastDetectionTime = new AtomicLong(0);
    private final AtomicLong routeChangesCount = new AtomicLong(0);
    private final AtomicLong checkCount = new AtomicLong(0);

    public GpsRouteDetectionScheduler(VehicleRepository vehicleRepository,
                                       DetectRouteByGpsUseCase detectRouteByGpsUseCase,
                                       VehicleGpsHistoryService gpsHistoryService) {
        this.vehicleRepository = vehicleRepository;
        this.detectRouteByGpsUseCase = detectRouteByGpsUseCase;
        this.gpsHistoryService = gpsHistoryService;
    }


    @Scheduled(cron = "0/30 * * * * *")
    public void detectRoutesByGps() {
        if (!detectionInProgress.compareAndSet(false, true)) {
            log.debug("GPS route detection already in progress, skipping");
            return;
        }

        Instant startTime = Instant.now();
        log.debug("Starting GPS route detection cycle");

        vehicleRepository.findActiveVehicles()
                .filter(this::isEligibleForDetection)
                .flatMap(vehicle ->
                        gpsHistoryService.hasEnoughPoints(vehicle.getDeviceId(), minGpsPoints)
                                .filter(Boolean::booleanValue)
                                .flatMap(hasPoints -> processVehicle(vehicle))
                                .onErrorResume(error -> {
                                    log.warn("Error processing vehicle {}: {}",
                                            vehicle.getLicensePlate(), error.getMessage());
                                    return Mono.empty();
                                }),
                        4
                )
                .collectList()
                .timeout(Duration.ofSeconds(25))
                .doOnSuccess(results -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    long changes = results.stream()
                            .filter(DetectRouteByGpsUseCase.Result::isRouteChanged)
                            .count();

                    if (changes > 0) {
                        log.info("GPS route detection completed: checked={}, changes={}, duration={}ms",
                                results.size(), changes, duration.toMillis());
                        routeChangesCount.addAndGet(changes);
                    } else {
                        log.debug("GPS route detection completed: checked={}, no changes, duration={}ms",
                                results.size(), duration.toMillis());
                    }

                    checkCount.addAndGet(results.size());
                    lastDetectionTime.set(Instant.now().toEpochMilli());
                })
                .doOnError(error -> log.error("GPS route detection failed: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .doFinally(signal -> detectionInProgress.set(false))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private boolean isEligibleForDetection(Vehicle vehicle) {
        if (vehicle == null) return false;

        if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
            return false;
        }

        if (!Boolean.TRUE.equals(vehicle.getGpsDetectionEnabled())) {
            return false;
        }

        if (!vehicle.hasPosition()) {
            return false;
        }

        if (vehicle.isCurrentlyInGarage()) {
            return false;
        }

        return vehicle.hasRecentPosition(120);
    }

    private Mono<DetectRouteByGpsUseCase.Result> processVehicle(Vehicle vehicle) {
        return detectRouteByGpsUseCase.execute(new DetectRouteByGpsUseCase.Request(vehicle))
                .doOnNext(result -> {
                    if (result.isRouteChanged()) {
                        log.info("Route auto-changed for vehicle {}: {} -> {} (confidence: {}%)",
                                vehicle.getLicensePlate(),
                                result.previousRouteNumber() != null ? result.previousRouteNumber() : "none",
                                result.newRouteNumber(),
                                result.confidence());
                    }
                });
    }

}
