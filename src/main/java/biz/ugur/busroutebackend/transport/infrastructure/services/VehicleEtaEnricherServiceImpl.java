package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TimePeriod;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.application.services.VehicleEtaEnricherService;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage.NextStopEta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class VehicleEtaEnricherServiceImpl implements VehicleEtaEnricherService {

    private static final int DEFAULT_MAX_STOPS = 3;

    private final BusStopRepository busStopRepository;
    private final DistanceCalculationService distanceCalculationService;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ETAProperties etaProperties;

    public VehicleEtaEnricherServiceImpl(
            BusStopRepository busStopRepository,
            DistanceCalculationService distanceCalculationService,
            ReactiveRedisTemplate<String, Object> redisTemplate,
            ETAProperties etaProperties) {
        this.busStopRepository = busStopRepository;
        this.distanceCalculationService = distanceCalculationService;
        this.redisTemplate = redisTemplate;
        this.etaProperties = etaProperties;
    }

    @Override
    public Mono<List<NextStopEta>> calculateNextStopsEta(
            String vehicleId,
            String routeNumber,
            Double latitude,
            Double longitude,
            Double speedKmh,
            int maxStops) {

        if (routeNumber == null || latitude == null || longitude == null) {
            return Mono.just(List.of());
        }

        double effectiveSpeed = getEffectiveSpeed(speedKmh);

        return busStopRepository.findStopsOnRouteAhead(routeNumber, latitude, longitude, maxStops)
                .map(stop -> {
                    double distanceMeters = distanceCalculationService.calculateDistance(
                            latitude, longitude,
                            stop.getLatitude().doubleValue(),
                            stop.getLongitude().doubleValue()
                    ).getMeters();

                    int etaMinutes = calculateEtaMinutes(distanceMeters, effectiveSpeed);

                    return new NextStopEta(
                            stop.getId().getValue(),
                            stop.getStopName(),
                            etaMinutes,
                            (int) Math.round(distanceMeters)
                    );
                })
                .collectList()
                .doOnNext(stops -> log.trace("Calculated ETA for vehicle {} on route {}: {} stops",
                        vehicleId, routeNumber, stops.size()))
                .onErrorResume(error -> {
                    log.debug("Failed to calculate ETA for vehicle {}: {}", vehicleId, error.getMessage());
                    return Mono.just(List.of());
                });
    }

    @Override
    public Mono<VehiclePositionWebSocketMessage> enrichWithEta(VehiclePositionWebSocketMessage message) {
        if (message.getRouteNumber() == null || message.getLatitude() == null) {
            return Mono.just(message);
        }

        return calculateNextStopsEta(
                message.getVehicleId(),
                message.getRouteNumber(),
                message.getLatitude(),
                message.getLongitude(),
                message.getSpeedKmh(),
                DEFAULT_MAX_STOPS
        ).map(nextStops -> {
            if (nextStops.isEmpty()) {
                return message;
            }

            return new VehiclePositionWebSocketMessage(
                    message.getVehicleId(),
                    message.getLicensePlate(),
                    message.getRouteNumber(),
                    message.getLatitude(),
                    message.getLongitude(),
                    message.getSpeedKmh(),
                    message.getIsInMotion(),
                    message.getTimestamp(),
                    message.getCourse(),
                    message.getLine(),
                    nextStops
            );
        }).onErrorResume(error -> {
            log.debug("ETA enrichment failed for vehicle {}: {}",
                    message.getVehicleId(), error.getMessage());
            return Mono.just(message);
        });
    }

    private double getEffectiveSpeed(Double actualSpeedKmh) {
        ETAProperties.SpeedConfig speedConfig = etaProperties.getSpeed();

        if (actualSpeedKmh == null || actualSpeedKmh < speedConfig.getMovingThresholdKmh()) {
            TimePeriod period = TimePeriod.fromDateTime(LocalDateTime.now());
            return period.getAverageSpeedKmh();
        }

        TimePeriod period = TimePeriod.fromDateTime(LocalDateTime.now());
        double maxSpeedForPeriod = getMaxSpeedForPeriod(period, speedConfig);

        return Math.min(actualSpeedKmh, maxSpeedForPeriod);
    }

    private double getMaxSpeedForPeriod(TimePeriod period, ETAProperties.SpeedConfig speedConfig) {
        return switch (period) {
            case MORNING_RUSH -> speedConfig.getMorningRushKmh();
            case EVENING_RUSH -> speedConfig.getEveningRushKmh();
            case DAYTIME -> speedConfig.getLunchTimeKmh();
            case NIGHT -> speedConfig.getNightKmh();
            case EVENING -> speedConfig.getNormalKmh();
        };
    }

    private int calculateEtaMinutes(double distanceMeters, double speedKmh) {
        TimePeriod period = TimePeriod.fromDateTime(LocalDateTime.now());
        boolean isWeekend = TimePeriod.isWeekend(LocalDateTime.now());

        if (speedKmh <= 0) {
            speedKmh = period.getAverageSpeedKmh();
        }

        double distanceKm = distanceMeters / 1000.0;
        double timeHours = distanceKm / speedKmh;
        int baseMinutes = (int) Math.ceil(timeHours * 60);

        double trafficMultiplier = period.getTrafficMultiplier(isWeekend);

        return (int) Math.ceil(baseMinutes * trafficMultiplier);
    }
}
