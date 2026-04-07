package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.admin.domain.exceptions.BusStopException;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TimePeriod;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.application.dto.NearbyStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.PerformanceLogRepository;
import biz.ugur.busroutebackend.transport.application.services.BusStopRealTimeService;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.OptionalDouble;
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
    private final VehiclePositionPredictionService predictionService;
    private final RouteGeometryCache routeGeometryCache;

    public BusStopRealTimeServiceImpl(
            BusStopRepository busStopRepository,
            PerformanceLogRepository performanceLogRepository,
            ReactiveRedisTemplate<String, Object> redisTemplate,
            DistanceCalculationService distanceCalculationService,
            ObjectMapper objectMapper,
            ETAProperties etaProperties,
            VehiclePositionPredictionService predictionService,
            RouteGeometryCache routeGeometryCache) {
        this.busStopRepository = busStopRepository;
        this.performanceLogRepository = performanceLogRepository;
        this.redisTemplate = redisTemplate;
        this.distanceCalculationService = distanceCalculationService;
        this.objectMapper = objectMapper;
        this.etaProperties = etaProperties;
        this.predictionService = predictionService;
        this.routeGeometryCache = routeGeometryCache;
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

                                    Mono<Void> logMono = logETAPerformance(stopId, response.getArrivals().size(), calculationTime);

                                    Mono<Boolean> cacheMono = redisTemplate.opsForValue()
                                            .set(cacheKey, response, Duration.ofSeconds(cacheTtlSeconds));

                                    return Mono.zip(cacheMono, logMono.thenReturn(true))
                                            .thenReturn(response);
                                })
                                .doOnNext(calculated -> log.debug("Cache MISS for stop {}, calculated {} routes",
                                        stopId, calculated.getArrivals().size()))
                )
                .doOnNext(response -> log.debug("Stop {} has {} unique routes with arrivals",
                        stopId, response.getArrivals().size()));
    }


    private Mono<Void> logETAPerformance(String stopId, int routesCount, long calculationTimeMs) {
        return performanceLogRepository.logETAPerformance(stopId, routesCount, calculationTimeMs)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(error -> log.warn("Failed to log ETA performance for stop {}: {}", stopId, error.getMessage()))
                .onErrorResume(error -> Mono.empty());
    }

    private Mono<BusStopArrivalsResponse> calculateStopArrivals(String stopId) {
        return busStopRepository.findById(BusStopId.of(stopId))
                .switchIfEmpty(Mono.error(new BusStopException("BUS_STOP.NOT_FOUND", "Stop not found: " + stopId) {
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
        String stopId = targetStop.getId().getValue();
        int maxEtaMinutes = etaProperties.getPosition().getMaxEtaMinutes();

        // Primary: use in-memory prediction states (fresh predicted position + fraction)
        Flux<BusArrivalInfo> fromPrediction = Flux.fromIterable(predictionService.getActiveStates())
                .filter(s -> s.getRouteNumber() != null && s.getTotalRouteDistanceMeters() > 0)
                .flatMap(state -> {
                    OptionalDouble stopFracOpt = routeGeometryCache.getStopFraction(
                            state.getRouteNumber(), state.getDirection(), stopId);
                    if (stopFracOpt.isEmpty()) return Mono.empty();

                    double stopFrac = stopFracOpt.getAsDouble();
                    // Skip if vehicle has already passed this stop
                    if (state.getFractionOnRoute() >= stopFrac - 0.001) return Mono.empty();

                    double distMeters = (stopFrac - state.getFractionOnRoute()) * state.getTotalRouteDistanceMeters();
                    int etaMin = computeEtaMinutes(distMeters, state.getSpeedKmh());
                    if (etaMin <= 0 || etaMin > maxEtaMinutes) return Mono.empty();

                    String status = distMeters < etaProperties.getPosition().getAtStopDistanceMeters()
                            ? "at_stop" : "approaching";

                    BusArrivalInfo info = new BusArrivalInfo(
                            state.getVehicleId(),
                            state.getLicensePlate(),
                            null,
                            state.getRouteNumber(),
                            null,
                            null,
                            etaMin,
                            status,
                            state.getPredictedLatitude(),
                            state.getPredictedLongitude(),
                            state.getSpeedKmh(),
                            state.isInMotion(),
                            "В пути",
                            LocalDateTime.now(),
                            state.getCourse()
                    );
                    return Mono.just(info);
                })
                // Keep only the closest bus per route
                .collectMultimap(BusArrivalInfo::getRouteNumber)
                .flatMapMany(byRoute -> Flux.fromIterable(byRoute.values())
                        .map(arrivals -> arrivals.stream()
                                .min(Comparator.comparingInt(BusArrivalInfo::getEstimatedArrivalMinutes))
                                .orElseThrow()));

        // Fallback: SQL query on vehicles table (for buses not tracked by prediction)
        Flux<BusArrivalInfo> fromDb = busStopRepository.findArrivingVehicles(
                targetStop.getId(),
                targetStop.getLatitude().doubleValue(),
                targetStop.getLongitude().doubleValue()
        );

        // Merge: prediction result wins for routes it knows about; DB fills gaps
        return fromPrediction
                .collectMap(BusArrivalInfo::getRouteNumber)
                .flatMapMany(predMap ->
                        fromDb.filter(db -> !predMap.containsKey(db.getRouteNumber()))
                                .mergeWith(Flux.fromIterable(predMap.values()))
                );
    }

    private int computeEtaMinutes(double distanceMeters, double speedKmh) {
        LocalDateTime now = LocalDateTime.now();
        TimePeriod period = TimePeriod.fromDateTime(now);
        double effective = (speedKmh >= etaProperties.getSpeed().getMovingThresholdKmh())
                ? speedKmh : period.getAverageSpeedKmh();
        double trafficMult = period.getTrafficMultiplier(TimePeriod.isWeekend(now));
        return (int) Math.max(1, Math.ceil((distanceMeters / 1000.0 / effective) * 60.0 * trafficMult));
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
        int streamIntervalSeconds = etaProperties.getCache().getStreamIntervalSeconds();

        return Flux.interval(Duration.ofSeconds(streamIntervalSeconds))
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
                .doOnSubscribe(sub -> log.debug("Started streaming arrivals for stop {} (interval: {}s)",
                        stopId, streamIntervalSeconds))
                .doOnCancel(() -> log.debug("Stopped streaming arrivals for stop {}", stopId));
    }


}
