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

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .flatMap(value -> {
                    if (value == null) {
                        return calculateStopArrivals(stopId)
                                .flatMap(response ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, response, Duration.ofSeconds(30))
                                                .thenReturn(response)
                                );
                    }
                    BusStopArrivalsResponse response = objectMapper.convertValue(value, BusStopArrivalsResponse.class);
                    return Mono.just(response);
                })
//                .cast(BusStopArrivalsResponse.class)
                .switchIfEmpty(
                        calculateStopArrivals(stopId)
                                .flatMap(response ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, response, Duration.ofSeconds(30))
                                                .thenReturn(response)
                                )
                )
                .doOnNext(response -> log.debug("Stop {} has {} arriving buses",
                        stopId, response.getArrivals().size()));
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
            
            -- Активные автобусы на этих маршрутах с их текущими позициями
            route_vehicles AS (
                SELECT 
                    v.id as vehicle_id,
                    v.license_plate,
                    v.current_latitude,
                    v.current_longitude,
                    v.speed_kmh,
                    v.is_in_motion,
                    v.last_position_update,
                    tsr.route_id,
                    tsr.direction,
                    tsr.target_sequence,
                    tsr.target_distance,
                    tsr.route_number,
                    tsr.route_name,
                    tsr.route_color,
                    -- Расстояние от автобуса до целевой остановки
                    ST_Distance(
                        ST_Point(v.current_longitude, v.current_latitude)::geography,
                        ST_Point(:stopLon, :stopLat)::geography
                    ) as distance_to_stop
                FROM vehicles v
                JOIN target_stop_routes tsr ON v.assigned_route_id = tsr.route_id
                WHERE v.is_active = true
                AND v.last_position_update > CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                AND v.current_latitude IS NOT NULL
                AND v.current_longitude IS NOT NULL
            ),
            
            -- Определяем ближайшую остановку для каждого автобуса
            vehicle_current_stops AS (
                SELECT 
                    rv.*,
                    -- Ближайшая остановка на маршруте автобуса
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
            )
            
            SELECT 
                vcs.*,
                CASE 
                    -- Автобус еще не дошел до целевой остановки
                    WHEN vcs.current_sequence < vcs.target_sequence THEN
                        -- Рассчитываем ETA на основе расстояния и скорости
                        CASE 
                            WHEN vcs.speed_kmh > 5 THEN 
                                ROUND((vcs.target_distance - vcs.current_distance) / (vcs.speed_kmh * 1000.0 / 60.0))::integer
                            ELSE 
                                -- Если автобус стоит, используем среднюю скорость 20 км/ч
                                ROUND((vcs.target_distance - vcs.current_distance) / (20.0 * 1000.0 / 60.0))::integer
                        END
                    -- Автобус уже прошел остановку или очень далеко
                    ELSE NULL
                END as estimated_arrival_minutes,
                
                CASE 
                    WHEN vcs.current_sequence < vcs.target_sequence THEN 'approaching'
                    WHEN vcs.current_sequence = vcs.target_sequence AND vcs.distance_to_current_stop < 100 THEN 'at_stop'
                    ELSE 'passed'
                END as arrival_status
                
            FROM vehicle_current_stops vcs
            WHERE 
                -- Фильтруем только автобусы, которые еще не прошли остановку
                (vcs.current_sequence < vcs.target_sequence 
                 OR (vcs.current_sequence = vcs.target_sequence AND vcs.distance_to_current_stop < 100))
                -- И которые находятся в разумном радиусе (10 км)
                AND vcs.distance_to_stop < 10000
            ORDER BY 
                estimated_arrival_minutes NULLS LAST,
                vcs.distance_to_stop
            LIMIT 10
            """;

        return databaseClient.sql(sql)
                .bind("stopId", targetStop.getId().getValue())
                .bind("stopLat", targetStop.getLatitude().doubleValue())
                .bind("stopLon", targetStop.getLongitude().doubleValue())
                .map(row -> {
                    String vehicleId = row.get("vehicle_id", String.class);
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

                    return new BusArrivalInfo(
                            vehicleId,
                            licensePlate,
                            routeNumber,
                            routeName,
                            routeColor,
                            etaMinutes != null ? etaMinutes : 999, // 999 = неизвестно
                            arrivalStatus,
                            currentLat,
                            currentLon,
                            speedKmh != null ? speedKmh : 0.0,
                            Boolean.TRUE.equals(isInMotion),
                            currentStopName,
                            LocalDateTime.now()
                    );
                })
                .all()
                .filter(arrival -> arrival.getEstimatedArrivalMinutes() < 60) // Показываем только автобусы в пределах часа
                .doOnNext(arrival -> log.trace("Found arriving bus: {} route {} ETA {} min",
                        arrival.getLicensePlate(), arrival.getRouteNumber(), arrival.getEstimatedArrivalMinutes()));
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
        return Flux.interval(Duration.ofSeconds(15))
                .flatMap(tick -> getStopArrivals(stopId))
                .distinctUntilChanged()
                .doOnNext(arrivals -> log.trace("Streaming update for stop {}: {} buses",
                        stopId, arrivals.getArrivals().size()));
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
