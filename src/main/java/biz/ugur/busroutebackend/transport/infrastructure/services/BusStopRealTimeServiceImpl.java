package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.admin.domain.exceptions.BusStopException;
import biz.ugur.busroutebackend.interfaces.rest.transport.dto.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.application.dto.NearbyStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.application.services.BusStopRealTimeService;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
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
    private final DatabaseClient databaseClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public BusStopRealTimeServiceImpl(
            BusStopRepository busStopRepository,
            DatabaseClient databaseClient,
            ReactiveRedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.busStopRepository = busStopRepository;
        this.databaseClient = databaseClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<BusStopArrivalsResponse> getStopArrivals(String stopId) {
        String cacheKey = "stop_arrivals:" + stopId;

        long startTime = System.currentTimeMillis();

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(BusStopArrivalsResponse.class)
                .doOnNext(cached -> log.debug("Cache HIT for stop {}", stopId))
                .switchIfEmpty(
                        calculateStopArrivals(stopId)
                                .flatMap(response -> {
                                    long calculationTime = System.currentTimeMillis() - startTime;

                                    // ✅ Логируем производительность для мониторинга
                                    logETAPerformance(stopId, response.getArrivals().size(),
                                            0, calculationTime, false);

                                    return redisTemplate.opsForValue()
                                            // ✅ ИСПРАВЛЕНИЕ: Сокращаем TTL для real-time точности
                                            .set(cacheKey, response, Duration.ofSeconds(15))
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
        if (calculationTimeMs > 100) {
            String performanceSql = """
            SELECT log_eta_performance(:stopId, :routesCount, :vehiclesProcessed, :calculationTime, :cacheHit)
            """;

            databaseClient.sql(performanceSql)
                    .bind("stopId", stopId)
                    .bind("routesCount", routesCount)
                    .bind("vehiclesProcessed", vehiclesProcessed)
                    .bind("calculationTime", (int) calculationTimeMs)
                    .bind("cacheHit", cacheHit)
                    .then()
                    .subscribe(
                            result -> log.debug("Logged ETA performance for stop {}: {}ms", stopId, calculationTimeMs),
                            error -> log.warn("Failed to log ETA performance: {}", error.getMessage())
                    );
        }
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
        String sql = """
        WITH 
        target_stop_routes AS (
            SELECT DISTINCT 
                rs.route_id,
                rs.direction,
                rs.stop_sequence as target_sequence,
                rs.distance_from_start_meters as target_distance,
                br.route_number,
                br.route_name,
                br.route_color
            FROM route_stops rs
            JOIN bus_routes br ON rs.route_id = br.id
            WHERE rs.stop_id = :stopId
            AND br.is_active = true
        ),
        
        route_vehicles AS (
            SELECT 
                v.id as vehicle_id,
                v.license_plate,
                v.current_latitude,
                v.current_longitude,
                v.speed_kmh,
                v.is_in_motion,
                v.last_position_update,
                v.course,
                tsr.route_id,
                tsr.direction,
                tsr.target_sequence,
                tsr.target_distance,
                tsr.route_number,
                tsr.route_name,
                tsr.route_color,
                ST_Distance(
                    ST_Point(v.current_longitude, v.current_latitude)::geography,
                    ST_Point(:stopLon, :stopLat)::geography
                ) as distance_to_stop
            FROM vehicles v
            JOIN target_stop_routes tsr ON v.assigned_route_id = tsr.route_id
            WHERE v.is_active = true
            AND v.last_position_update > CURRENT_TIMESTAMP - INTERVAL '24 hours'
            AND v.current_latitude IS NOT NULL
            AND v.current_longitude IS NOT NULL
        ),
        
        vehicle_current_stops AS (
            SELECT 
                rv.*,
                rs_nearest.stop_sequence as current_sequence,
                rs_nearest.distance_from_start_meters as current_distance,
                rs_nearest.stop_name as current_stop_name,
                ST_Distance(
                    ST_Point(rv.current_longitude, rv.current_latitude)::geography,
                    ST_Point(rs_nearest.longitude, rs_nearest.latitude)::geography
                ) as distance_to_current_stop
            FROM route_vehicles rv
            JOIN LATERAL (
                SELECT rs.*, bs.stop_name, bs.latitude, bs.longitude
                FROM route_stops rs
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE rs.route_id = rv.route_id
                AND rs.direction = rv.direction
                ORDER BY ST_Distance(
                    ST_Point(rv.current_longitude, rv.current_latitude)::geography,
                    ST_Point(bs.longitude, bs.latitude)::geography
                )
                LIMIT 1
            ) rs_nearest ON true
        ),
        
        vehicles_with_eta AS (
            SELECT 
                vcs.*,
                CASE 
                    -- Автобус еще не дошел до целевой остановки
                    WHEN vcs.current_sequence < vcs.target_sequence THEN
                        CASE 
                            -- Автобус движется с нормальной скоростью
                            WHEN vcs.speed_kmh > 5 THEN 
                                GREATEST(1, ROUND((vcs.target_distance - vcs.current_distance) / (vcs.speed_kmh * 1000.0 / 60.0))::integer)
                            
                            -- Автобус стоит или движется медленно - учитываем время дня
                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 7 AND 9 THEN 
                                -- Утренний час пик - медленное движение
                                GREATEST(2, ROUND((vcs.target_distance - vcs.current_distance) / (12.0 * 1000.0 / 60.0))::integer)
                            
                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 17 AND 19 THEN 
                                -- Вечерний час пик - медленное движение
                                GREATEST(2, ROUND((vcs.target_distance - vcs.current_distance) / (12.0 * 1000.0 / 60.0))::integer)
                            
                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 12 AND 14 THEN 
                                -- Обеденное время - средняя скорость
                                GREATEST(1, ROUND((vcs.target_distance - vcs.current_distance) / (18.0 * 1000.0 / 60.0))::integer)
                            
                            ELSE 
                                -- Обычное время - хорошая скорость
                                GREATEST(1, ROUND((vcs.target_distance - vcs.current_distance) / (25.0 * 1000.0 / 60.0))::integer)
                        END
                    
                    -- Автобус на остановке или очень близко
                    WHEN vcs.current_sequence = vcs.target_sequence AND vcs.distance_to_current_stop < 200 THEN 1
                    
                    -- Автобус прошел остановку - не показываем
                    ELSE NULL
                END as calculated_eta,
                
                CASE 
                    WHEN vcs.current_sequence < vcs.target_sequence THEN 'approaching'
                    WHEN vcs.current_sequence = vcs.target_sequence AND vcs.distance_to_current_stop < 200 THEN 'at_stop'
                    ELSE 'passed'
                END as calculated_status
            FROM vehicle_current_stops vcs
        )
        
        SELECT DISTINCT ON (route_number)
            vwe.vehicle_id,
            vwe.license_plate,
            vwe.route_id,
            vwe.route_number,
            vwe.route_name,
            vwe.route_color,
            vwe.calculated_eta as estimated_arrival_minutes,
            vwe.calculated_status as arrival_status,
            vwe.current_latitude,
            vwe.current_longitude,
            vwe.speed_kmh,
            vwe.is_in_motion,
            vwe.current_stop_name,
            vwe.course
        FROM vehicles_with_eta vwe
        WHERE vwe.calculated_eta IS NOT NULL 
        AND vwe.calculated_eta > 0
        AND vwe.calculated_eta < 120  -- Показываем автобусы в пределах 2 часов
        ORDER BY vwe.route_number, vwe.calculated_eta
        """;

        return databaseClient.sql(sql)
                .bind("stopId", targetStop.getId().getValue())
                .bind("stopLat", targetStop.getLatitude().doubleValue())
                .bind("stopLon", targetStop.getLongitude().doubleValue())
                .map(row -> {
                    String vehicleId = row.get("vehicle_id", String.class);
                    String routeId = row.get("route_id", String.class);
                    String licensePlate = row.get("license_plate", String.class);
                    String routeNumber = row.get("route_number", String.class);
                    String routeName = row.get("route_name", String.class);
                    String routeColor = row.get("route_color", String.class);
                    Integer etaMinutes = row.get("estimated_arrival_minutes", Integer.class);
                    String arrivalStatus = row.get("arrival_status", String.class);
                    Double currentLat = row.get("current_latitude", Double.class);
                    Double currentLon = row.get("current_longitude", Double.class);
                    Double speedKmh = row.get("speed_kmh", Double.class);
                    Boolean isInMotion = row.get("is_in_motion", Boolean.class);
                    String currentStopName = row.get("current_stop_name", String.class);
                    Double course = row.get("course", Double.class);

                    return new BusArrivalInfo(
                            vehicleId,
                            licensePlate,
                            routeId,
                            routeNumber,
                            routeName,
                            routeColor,
                            etaMinutes != null ? etaMinutes : 999,
                            arrivalStatus,
                            currentLat,
                            currentLon,
                            speedKmh != null ? speedKmh : 0.0,
                            Boolean.TRUE.equals(isInMotion),
                            currentStopName,
                            LocalDateTime.now(),
                            course != null ? course : 0.0
                    );
                })
                .all()
                .filter(arrival -> arrival.getEstimatedArrivalMinutes() < 120)
                .doOnNext(arrival -> log.trace("Route {} closest bus: {} ETA {} min",
                        arrival.getRouteNumber(), arrival.getLicensePlate(), arrival.getEstimatedArrivalMinutes()));
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
                                        calculateDistance(lat, lon,
                                                nearestStop.getLatitude().doubleValue(),
                                                nearestStop.getLongitude().doubleValue()),
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

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000;
    }


    public static class BusStopNotFoundException extends RuntimeException {
        public BusStopNotFoundException(String message) {
            super(message);
        }
    }

    public static class ETACalculationException extends RuntimeException {
        public ETACalculationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
