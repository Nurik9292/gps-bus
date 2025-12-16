package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.repository.ETARepository;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
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

    private static final double AVERAGE_WALKING_SPEED_M_PER_MIN = GeoConstants.AVERAGE_WALKING_SPEED_M_PER_MIN;
    private static final int MAX_REASONABLE_WALKING_TIME = GeoConstants.MAX_WALKING_TIME_MINUTES;
    private static final int MIN_WALKING_TIME = GeoConstants.MIN_WALKING_TIME_MINUTES;

    public LiveETACalculationService(VehicleRepository vehicleRepository,
                                     ETARepository etaRepository,
                                     ReactiveRedisTemplate<String, Object> redisTemplate,
                                     DistanceCalculationService distanceService,
                                     ETAProperties etaProperties) {
        this.vehicleRepository = vehicleRepository;
        this.etaRepository = etaRepository;
        this.redisTemplate = redisTemplate;
        this.distanceService = distanceService;
        this.etaProperties = etaProperties;
    }

    @Override
    public Mono<LocalDateTime> calculateEstimatedArrival(String routeNumber, String fromStopName,
                                                         String toStopName, LocalDateTime departureTime) {
        log.debug("Calculating ETA for route {} from {} to {} departing at {}",
                routeNumber, fromStopName, toStopName, departureTime);

        return calculateTravelTimeMinutes(routeNumber, fromStopName, toStopName)
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
    public Mono<Integer> calculateWaitingTimeMinutes(String routeNumber, String stopName, LocalDateTime currentTime) {
        log.debug("Calculating wait time for route {} at stop {} at {}", routeNumber, stopName, currentTime);

        int timeSlot = (currentTime.getHour() * 4) + (currentTime.getMinute() / 15);
        String cacheKey = String.format("wait_time:%s:%s:%d", routeNumber, stopName, timeSlot);
        int cacheTtlMinutes = etaProperties.getCache().getWaitingTimeTtlMinutes();

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(Integer.class)
                .switchIfEmpty(
                        calculateWaitingTimeFromData(routeNumber, stopName, currentTime)
                                .flatMap(waitTime ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, waitTime, Duration.ofMinutes(cacheTtlMinutes))
                                                .thenReturn(waitTime)
                                )
                )
                .doOnNext(waitMinutes -> log.debug("Estimated wait time: {} minutes", waitMinutes));
    }

    @Override
    public Mono<Integer> calculateTravelTimeMinutes(String routeNumber, String fromStopName, String toStopName) {
        log.debug("Calculating travel time for route {} from {} to {}", routeNumber, fromStopName, toStopName);

        String cacheKey = String.format("travel_time:%s:%s:%s", routeNumber, fromStopName, toStopName);
        int cacheTtlMinutes = etaProperties.getCache().getTravelTimeTtlMinutes();

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(Integer.class)
                .switchIfEmpty(
                        calculateTravelTimeFromDatabase(routeNumber, fromStopName, toStopName)
                                .flatMap(travelTime ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, travelTime, Duration.ofMinutes(cacheTtlMinutes))
                                                .thenReturn(travelTime)
                                )
                )
                .doOnNext(travelMinutes -> log.debug("Estimated travel time: {} minutes", travelMinutes));
    }

    @Override
    public int calculateTransferTimeMinutes(String stopName, boolean isMajorStop) {
        int baseTransferTime = isMajorStop ? 3 : 5;

        int additionalTime = 0;

        if (stopName != null) {
            String lowerStopName = stopName.toLowerCase();

            if (lowerStopName.contains("аэропорт") || lowerStopName.contains("airport")) {
                additionalTime += 3;
            } else if (lowerStopName.contains("базар") || lowerStopName.contains("рынок") ||
                    lowerStopName.contains("bazaar") || lowerStopName.contains("market")) {
                additionalTime += 2;
            } else if (lowerStopName.contains("центр") || lowerStopName.contains("center")) {
                additionalTime += 2;
            }
        }

        int totalTransferTime = baseTransferTime + additionalTime;

        log.trace("Transfer time at {} (major: {}): {} minutes", stopName, isMajorStop, totalTransferTime);
        return totalTransferTime;
    }

    private Mono<Integer> calculateWaitingTimeFromData(String routeNumber, String stopName, LocalDateTime currentTime) {
        return getVehicleBasedWaitingTime(routeNumber, stopName)
                .switchIfEmpty(
                        getStatisticalWaitingTime(routeNumber, currentTime)
                )
                .switchIfEmpty(
                        getFrequencyBasedWaitingTime(routeNumber, currentTime)
                );
    }



    private Mono<Integer> getVehicleBasedWaitingTime(String routeNumber, String stopName) {
        return etaRepository.getVehicleBasedWaitingTime(routeNumber, stopName);
    }


    private Mono<Integer> getStatisticalWaitingTime(String routeNumber, LocalDateTime currentTime) {
        return etaRepository.getStatisticalWaitingTime(routeNumber, currentTime);
    }


    private Mono<Integer> getFrequencyBasedWaitingTime(String routeNumber, LocalDateTime currentTime) {
        return Mono.fromCallable(() -> {
            int hour = currentTime.getHour();
            int dayOfWeek = currentTime.getDayOfWeek().getValue();


            int baseWaitTime;
            if (hour >= 6 && hour <= 9) {
                baseWaitTime = 8;
            } else if (hour >= 17 && hour <= 20) {
                baseWaitTime = 10;
            } else if (hour >= 22 || hour <= 5) {
                baseWaitTime = 25;
            } else {
                baseWaitTime = 15;
            }

            if (dayOfWeek >= 6) {
                baseWaitTime += 5;
            }

            return Math.min(baseWaitTime, 30);
        });
    }



    private Mono<Integer> calculateTravelTimeFromDatabase(String routeNumber, String fromStopName, String toStopName) {
        return etaRepository.calculateTravelTimeFromDatabase(routeNumber, fromStopName, toStopName)
                .switchIfEmpty(Mono.fromCallable(() -> {
                    log.warn("No route data found for {} from {} to {}, using fallback",
                            routeNumber, fromStopName, toStopName);
                    return 15;
                }));
    }


    private int applyWalkingConditionsCorrection(int baseWalkingTime, double distanceMeters) {
        if (distanceMeters < 200) {
            return baseWalkingTime + 1;
        }

        if (distanceMeters < 500) {
            return baseWalkingTime + 2;
        }

        if (distanceMeters > 800) {
            return baseWalkingTime + 3;
        }

        return baseWalkingTime + 1;
    }

    private int calculateBaseWaitingTime(LocalDateTime departureTime) {
        int hour = departureTime.getHour();

        if (hour >= 6 && hour <= 9) {
            return 5;
        } else if (hour >= 17 && hour <= 20) {
            return 7;
        } else if (hour >= 22 || hour <= 5) {
            return 15;
        } else {
            return 10;
        }
    }
}