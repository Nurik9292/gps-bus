package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.repository.ETARepository;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TimePeriod;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.geospatial.domain.constants.GeoConstants;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;


@Service
@Slf4j
public class LiveETACalculationService implements ETACalculationService {

    private final VehicleRepository vehicleRepository;
    private final ETARepository etaRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final DistanceCalculationService distanceService;
    private final ETAProperties etaProperties;
    private final RealTimeETAService realTimeETAService;

    private static final double AVERAGE_WALKING_SPEED_M_PER_MIN = GeoConstants.AVERAGE_WALKING_SPEED_M_PER_MIN;
    private static final int MAX_REASONABLE_WALKING_TIME = GeoConstants.MAX_WALKING_TIME_MINUTES;
    private static final int MIN_WALKING_TIME = GeoConstants.MIN_WALKING_TIME_MINUTES;

    public LiveETACalculationService(VehicleRepository vehicleRepository,
                                     ETARepository etaRepository,
                                     ReactiveRedisTemplate<String, Object> redisTemplate,
                                     DistanceCalculationService distanceService,
                                     ETAProperties etaProperties,
                                     RealTimeETAService realTimeETAService) {
        this.vehicleRepository = vehicleRepository;
        this.etaRepository = etaRepository;
        this.redisTemplate = redisTemplate;
        this.distanceService = distanceService;
        this.etaProperties = etaProperties;
        this.realTimeETAService = realTimeETAService;
    }

    @Override
    public Mono<LocalDateTime> calculateEstimatedArrival(String routeId, String routeNumber, String fromStopName,
                                                         String toStopName, LocalDateTime departureTime) {
        log.debug("Calculating ETA for route {} from {} to {} departing at {}",
                routeNumber, fromStopName, toStopName, departureTime);

        return calculateTravelTimeMinutes(routeId, routeNumber, fromStopName, toStopName)
                .map(travelMinutes -> {
                    int waitingTime = calculateBaseWaitingTime(departureTime);
                    return departureTime.plusMinutes(waitingTime + travelMinutes);
                })
                .doOnNext(eta -> log.debug("Calculated ETA: {}", eta));
    }

    @Override
    public int calculateWalkingTimeMinutes(Coordinates from, Coordinates to) {
        double distanceMeters = distanceService.calculateDistance(
                from.getLatitudeAsDouble(), from.getLongitudeAsDouble(),
                to.getLatitudeAsDouble(), to.getLongitudeAsDouble()
        ).getMeters();

        int walkingMinutes = (int) Math.ceil(distanceMeters / AVERAGE_WALKING_SPEED_M_PER_MIN);

        walkingMinutes = applyWalkingConditionsCorrection(walkingMinutes, distanceMeters);

        walkingMinutes = Math.max(MIN_WALKING_TIME, Math.min(MAX_REASONABLE_WALKING_TIME, walkingMinutes));

        log.trace("Walking time from {} to {}: {} minutes ({}m)",
                from, to, walkingMinutes, Math.round(distanceMeters));

        return walkingMinutes;
    }

    @Override
    public Mono<Integer> calculateWaitingTimeMinutes(String routeId, String routeNumber, String stopId,
                                                     String stopName, LocalDateTime currentTime) {
        log.debug("Calculating wait time for route {} at stop {} at {}", routeNumber, stopName, currentTime);

        TimePeriod period = TimePeriod.fromDateTime(currentTime);
        boolean isWeekend = TimePeriod.isWeekend(currentTime);
        String periodKey = period.name() + (isWeekend ? "_WE" : "_WD");
        String cacheKey = String.format("wait_time:%s:%s:%s", routeId, stopName, periodKey);
        int cacheTtlMinutes = etaProperties.getCache().getWaitingTimeTtlMinutes();

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(Integer.class)
                .timeout(Duration.ofSeconds(2), Mono.empty())
                .switchIfEmpty(
                        calculateWaitingTimeFromData(routeId, routeNumber, stopId, stopName, currentTime)
                                .timeout(Duration.ofSeconds(3), getFrequencyBasedWaitingTime(routeNumber, currentTime))
                                .flatMap(waitTime ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, waitTime, Duration.ofMinutes(cacheTtlMinutes))
                                                .timeout(Duration.ofSeconds(1), Mono.just(true))
                                                .thenReturn(waitTime)
                                )
                )
                .doOnNext(waitMinutes -> log.debug("Estimated wait time: {} minutes (period: {})",
                        waitMinutes, periodKey));
    }

    @Override
    public Mono<Integer> calculateTravelTimeMinutes(String routeId, String routeNumber, String fromStopName, String toStopName) {
        log.debug("Calculating travel time for route {} from {} to {}", routeNumber, fromStopName, toStopName);

        LocalDateTime now = LocalDateTime.now();
        TimePeriod period = TimePeriod.fromDateTime(now);
        boolean isWeekend = TimePeriod.isWeekend(now);
        double trafficMult = period.getTrafficMultiplier(isWeekend);

        String cacheKey = String.format("travel_time:%s:%s:%s:%s", routeId, fromStopName, toStopName, period.name());
        int cacheTtlMinutes = etaProperties.getCache().getTravelTimeTtlMinutes();

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(Integer.class)
                .timeout(Duration.ofSeconds(2), Mono.empty())
                .switchIfEmpty(
                        calculateTravelTimeFromDatabase(routeId, routeNumber, fromStopName, toStopName)
                                .timeout(Duration.ofSeconds(3), Mono.fromCallable(() -> etaProperties.getFallback().getDefaultTravelTimeMinutes()))
                                .map(baseTravelTime -> {
                                    int adjusted = (int) Math.ceil(baseTravelTime * trafficMult);
                                    log.debug("Travel time adjusted: base={}min × {}({}) = {}min",
                                            baseTravelTime, trafficMult, period.name(), adjusted);
                                    return adjusted;
                                })
                                .flatMap(travelTime ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, travelTime, Duration.ofMinutes(cacheTtlMinutes))
                                                .timeout(Duration.ofSeconds(1), Mono.just(true))
                                                .thenReturn(travelTime)
                                )
                )
                .doOnNext(travelMinutes -> log.debug("Estimated travel time: {} minutes (period={})", travelMinutes, period.name()));
    }

    @Override
    public int calculateTransferTimeMinutes(String stopName, boolean isMajorStop) {
        ETAProperties.TransferConfig transferConfig = etaProperties.getTransfer();

        int baseTransferTime = isMajorStop
                ? transferConfig.getMajorStopMinutes()
                : transferConfig.getRegularStopMinutes();

        int additionalTime = 0;

        if (stopName != null) {
            String lowerStopName = stopName.toLowerCase();

            if (lowerStopName.contains("аэропорт") || lowerStopName.contains("airport")) {
                additionalTime += transferConfig.getAirportPenaltyMinutes();
            } else if (lowerStopName.contains("базар") || lowerStopName.contains("рынок") ||
                    lowerStopName.contains("bazaar") || lowerStopName.contains("market")) {
                additionalTime += transferConfig.getMarketPenaltyMinutes();
            } else if (lowerStopName.contains("центр") || lowerStopName.contains("center")) {
                additionalTime += transferConfig.getCenterPenaltyMinutes();
            }
        }

        int totalTransferTime = baseTransferTime + additionalTime;

        log.trace("Transfer time at {} (major: {}): {} minutes", stopName, isMajorStop, totalTransferTime);
        return totalTransferTime;
    }

    private Mono<Integer> calculateWaitingTimeFromData(String routeId, String routeNumber, String stopId,
                                                       String stopName, LocalDateTime currentTime) {
        return getVehicleBasedWaitingTime(routeId, routeNumber, stopId, stopName)
                .onErrorResume(e -> {
                    log.warn("Vehicle-based wait time failed for route {}: {}", routeNumber, e.getMessage());
                    return Mono.empty();
                })
                .switchIfEmpty(
                        getStatisticalWaitingTime(routeId, currentTime)
                                .onErrorResume(e -> {
                                    log.warn("Statistical wait time failed for route {}: {}", routeNumber, e.getMessage());
                                    return Mono.empty();
                                })
                )
                .switchIfEmpty(
                        getFrequencyBasedWaitingTime(routeNumber, currentTime)
                );
    }



    private Mono<Integer> getVehicleBasedWaitingTime(String routeId, String routeNumber, String stopId,
                                                     String stopName) {
        return realTimeETAService.getWaitingTimeMinutes(routeId, routeNumber, stopId)
                .switchIfEmpty(
                        etaRepository.getVehicleBasedWaitingTime(routeId, stopName)
                );
    }


    private Mono<Integer> getStatisticalWaitingTime(String routeId, LocalDateTime currentTime) {
        return etaRepository.getStatisticalWaitingTime(routeId, currentTime);
    }


    private Mono<Integer> getFrequencyBasedWaitingTime(String routeNumber, LocalDateTime currentTime) {
        return Mono.fromCallable(() -> {
            TimePeriod period = TimePeriod.fromDateTime(currentTime);
            boolean isWeekend = TimePeriod.isWeekend(currentTime);

            int baseWaitTime = period.getBaseWaitingMinutes(isWeekend);
            int maxWaitTime = etaProperties.getFallback().getMaxWaitingTimeMinutes();

            log.trace("Frequency-based wait time: period={}, weekend={}, base={}min",
                    period, isWeekend, baseWaitTime);

            return Math.min(baseWaitTime, maxWaitTime);
        });
    }



    private Mono<Integer> calculateTravelTimeFromDatabase(String routeId, String routeNumber, String fromStopName, String toStopName) {
        return etaRepository.calculateTravelTimeFromDatabase(routeId, fromStopName, toStopName)
                .switchIfEmpty(
                    etaRepository.countStopsBetween(routeId, fromStopName, toStopName)
                        .map(stopCount -> {
                            int perStop = etaProperties.getFallback().getMinutesPerStop();
                            int estimated = Math.max(3, stopCount * perStop);
                            log.warn("No travel time data for route {} from {} to {}, estimated from {} stops × {}min = {}min",
                                    routeNumber, fromStopName, toStopName, stopCount, perStop, estimated);
                            return estimated;
                        })
                        .switchIfEmpty(Mono.fromCallable(() -> {
                            int fallbackTime = etaProperties.getFallback().getDefaultTravelTimeMinutes();
                            log.warn("No route data at all for {} from {} to {}, using default fallback: {}min",
                                    routeNumber, fromStopName, toStopName, fallbackTime);
                            return fallbackTime;
                        }))
                );
    }


    private int applyWalkingConditionsCorrection(int baseWalkingTime, double distanceMeters) {
        int correction;

        if (distanceMeters < 200) {
            correction = 1;
        } else if (distanceMeters < 500) {
            correction = 2;
        } else if (distanceMeters <= 800) {
            correction = 2;
        } else {
            correction = 3;
        }

        log.trace("Walking correction: distance={}m, base={}min, correction=+{}min",
                Math.round(distanceMeters), baseWalkingTime, correction);

        return baseWalkingTime + correction;
    }

    private int calculateBaseWaitingTime(LocalDateTime departureTime) {
        TimePeriod period = TimePeriod.fromDateTime(departureTime);
        boolean isWeekend = TimePeriod.isWeekend(departureTime);

        int baseTime = period.getBaseWaitingMinutes(isWeekend) / 2;

        log.trace("Base waiting time: period={}, weekend={}, base={}min",
                period, isWeekend, baseTime);

        return Math.max(baseTime, 3);
    }
}