package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.admin.domain.exceptions.BusStopException;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.application.dto.NearbyStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.PerformanceLogRepository;
import biz.ugur.busroutebackend.transport.application.services.BusStopRealTimeService;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BusStopRealTimeServiceImpl implements BusStopRealTimeService {

    private final BusStopRepository busStopRepository;
    private final PerformanceLogRepository performanceLogRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final DistanceCalculationService distanceCalculationService;
    private final ObjectMapper objectMapper;
    private final ETAProperties etaProperties;

    public BusStopRealTimeServiceImpl(
            BusStopRepository busStopRepository,
            PerformanceLogRepository performanceLogRepository,
            ReactiveRedisTemplate<String, Object> redisTemplate,
            DistanceCalculationService distanceCalculationService,
            ObjectMapper objectMapper,
            ETAProperties etaProperties) {
        this.busStopRepository = busStopRepository;
        this.performanceLogRepository = performanceLogRepository;
        this.redisTemplate = redisTemplate;
        this.distanceCalculationService = distanceCalculationService;
        this.objectMapper = objectMapper;
        this.etaProperties = etaProperties;
    }

    public Mono<BusStopArrivalsResponse> getStopArrivals(String stopId) {
        String cacheKey = "stop_arrivals:" + stopId;
        int cacheTtlSeconds = etaProperties.getCache().getStopArrivalsTtlSeconds();

        long startTime = System.currentTimeMillis();

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .flatMap(cached -> {
                    try {
                        BusStopArrivalsResponse response = objectMapper.convertValue(cached, BusStopArrivalsResponse.class);
                        log.debug("Cache HIT for stop {}", stopId);
                        return Mono.just(response);
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to deserialize cached data for stop {}: {}", stopId, e.getMessage());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(
                        calculateStopArrivals(stopId)
                                .flatMap(response -> {
                                    long calculationTime = System.currentTimeMillis() - startTime;

                                    logETAPerformance(stopId, response.getArrivals().size(),
                                            0, calculationTime, false);

                                    return redisTemplate.opsForValue()
                                            .set(cacheKey, response, Duration.ofSeconds(cacheTtlSeconds))
                                            .thenReturn(response);
                                })
                                .doOnNext(calculated -> log.debug("Cache MISS for stop {}, calculated {} routes",
                                        stopId, calculated.getArrivals().size()))
                )
                .doOnNext(response -> log.debug("Stop {} has {} unique routes with arrivals",
                        stopId, response.getArrivals().size()));
    }


    private void logETAPerformance(String stopId, int routesCount, int vehiclesProcessed,
                                   long calculationTimeMs, boolean cacheHit) {
        performanceLogRepository.logETAPerformance(
                stopId,
                routesCount,
                vehiclesProcessed,
                calculationTimeMs,
                cacheHit
        ).subscribe();
    }

    private Mono<BusStopArrivalsResponse> calculateStopArrivals(String stopId) {
        return busStopRepository.findById(BusStopId.of(stopId))
                .switchIfEmpty(Mono.error(new BusStopException("BUS_STOP_EXCEPTION", "Stop not found: " + stopId) {
                }))
                .flatMap(busStop -> {
                    return findArrivingVehicles(busStop)
                            .collectList()
                            .map(arrivals -> new BusStopArrivalsResponse(
                                    busStop.getId().getValue(),
                                    busStop.getStopName(),
                                    busStop.getLatitude().doubleValue(),
                                    busStop.getLongitude().doubleValue(),
                                    arrivals,
                                    LocalDateTime.now()
                            ));
                });
    }

    private Flux<BusArrivalInfo> findArrivingVehicles(BusStop targetStop) {
        return busStopRepository.findArrivingVehicles(
                targetStop.getId(),
                targetStop.getLatitude().doubleValue(),
                targetStop.getLongitude().doubleValue()
        );
    }


    public Flux<NearbyStopArrivalsResponse> getNearbyStopArrivals(Double lat, Double lon, Integer radiusMeters) {
        return busStopRepository.findStopsWithinRadius(lat, lon, radiusMeters / 1000.0)
                .switchIfEmpty(Mono.error(new BusStopException("BUS_STOP_NOT_FOUND", "No stops found nearby") {
                }))
                .flatMap(nearestStop ->
                        getStopArrivals(nearestStop.getId().getValue())
                                .map(arrivals -> new NearbyStopArrivalsResponse(
                                        nearestStop.getId().getValue(),
                                        nearestStop.getStopName(),
                                        distanceCalculationService.calculateDistance(
                                                lat, lon,
                                                nearestStop.getLatitude().doubleValue(),
                                                nearestStop.getLongitude().doubleValue()
                                        ).getMeters(),
                                        arrivals
                                ))
                );
    }

    public Flux<BusStopArrivalsResponse> streamStopArrivals(String stopId) {
        return Flux.interval(Duration.ofSeconds(10))
                .flatMap(tick -> getStopArrivals(stopId))
                .distinctUntilChanged(response -> {

                    return response.getArrivals().stream()
                            .map(arrival -> String.format("%s:%d:%s",
                                    arrival.getRouteNumber(),
                                    arrival.getEstimatedArrivalMinutes(),
                                    arrival.getArrivalStatus()))
                            .sorted()
                            .collect(Collectors.joining(";"));
                })
                .doOnNext(arrivals -> log.trace("Streaming update for stop {}: {} unique routes",
                        stopId, arrivals.getArrivals().size()))
                .doOnSubscribe(sub -> log.debug("Started streaming arrivals for stop {}", stopId))
                .doOnCancel(() -> log.debug("Stopped streaming arrivals for stop {}", stopId));
    }


}
