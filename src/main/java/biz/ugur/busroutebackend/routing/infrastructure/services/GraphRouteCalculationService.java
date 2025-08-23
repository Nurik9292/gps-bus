package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.Location;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Service
@Slf4j
public class GraphRouteCalculationService implements RouteCalculationService {

    private final BusStopRepository busStopRepository;
    private final BusRouteRepository busRouteRepository;
    private final DatabaseClient databaseClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final class QueryConstants {
        static final int MAX_STOPS_PER_SEGMENT = 12;
        static final int MAX_MIDDLE_SEGMENT_STOPS = 10;
        static final int MAX_TOTAL_TRAVEL_TIME_MINUTES = 90;
        static final int MIN_SEGMENT_TIME_MINUTES = 3;
        static final int DIRECT_ROUTES_LIMIT = 15;
        static final int ONE_TRANSFER_LIMIT = 12;
        static final int TWO_TRANSFER_LIMIT = 6;

        static final Duration NEARBY_STOPS_CACHE_TTL = Duration.ofMinutes(5);
        static final Duration CONNECTION_CACHE_TTL = Duration.ofHours(1);
        static final Duration TWO_TRANSFER_CACHE_TTL = Duration.ofMinutes(10);
    }

    private static final String ROUTE_VALIDATION_CTE = """
        validated_routes AS (
            SELECT cr.*,
                EXISTS(
                    SELECT 1 FROM route_stops rs_check1 
                    WHERE rs_check1.route_id = cr.route_id 
                    AND rs_check1.direction = cr.direction
                    AND rs_check1.stop_id = ANY(:fromStopIds)
                ) as has_from_stop,
                EXISTS(
                    SELECT 1 FROM route_stops rs_check2 
                    WHERE rs_check2.route_id = cr.route_id 
                    AND rs_check2.direction = cr.direction
                    AND rs_check2.stop_id = ANY(:toStopIds)
                ) as has_to_stop
            FROM candidate_routes cr
        )
        """;

    private static final String ROUTE_VEHICLES_CTE = """
        route_vehicles AS (
            SELECT 
                br.id as route_id,
                COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles
            FROM bus_routes br
            LEFT JOIN vehicles v ON br.id = v.assigned_route_id
            WHERE br.is_active = true
            GROUP BY br.id
        )
        """;

    private static final String BASE_ACTIVE_CONDITIONS = """
        AND br.is_active = true
        AND bs1.is_active = true 
        AND bs2.is_active = true
        """;

    public GraphRouteCalculationService(BusStopRepository busStopRepository,
                                        BusRouteRepository busRouteRepository,
                                        DatabaseClient databaseClient,
                                        ReactiveRedisTemplate<String, Object> redisTemplate,
                                        ObjectMapper objectMapper) {
        this.busStopRepository = busStopRepository;
        this.busRouteRepository = busRouteRepository;
        this.databaseClient = databaseClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<BusStop> findNearbyStops(Location location, double radiusKm) {
        log.debug("Finding stops within {}km of ({}, {})", radiusKm, location.getLatitude(), location.getLongitude());

        String cacheKey = buildNearbyStopsCacheKey(location, radiusKm);

        return getCachedNearbyStops(cacheKey)
                .switchIfEmpty(searchAndCacheNearbyStops(location, radiusKm, cacheKey))
                .doOnNext(stop -> log.trace("Found nearby stop: {} at distance {}m",
                        stop.getStopName(), location.distanceTo(
                                stop.getLatitude().doubleValue(),
                                stop.getLongitude().doubleValue())));
    }

    @Override
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        log.debug("Finding direct routes between {} origin stops and {} destination stops",
                fromStops.size(), toStops.size());

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        String sql = """
        SELECT DISTINCT 
            br.id as route_id, br.route_number, br.route_name, br.route_color,
            br.route_geometry_forward, br.total_distance_forward_meters,
            rs1.stop_id as from_stop_id, bs1.stop_name as from_stop_name,
            bs1.latitude as from_lat, bs1.longitude as from_lon,
            rs2.stop_id as to_stop_id, bs2.stop_name as to_stop_name,
            bs2.latitude as to_lat, bs2.longitude as to_lon,
            rs1.stop_sequence as from_sequence, rs2.stop_sequence as to_sequence,
            rs1.direction,
            ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as estimated_travel_minutes,
            ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as distance_meters,
            COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count
        FROM route_stops rs1
        JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                            AND rs1.direction = rs2.direction
                            AND rs1.stop_sequence < rs2.stop_sequence
        JOIN bus_routes br ON rs1.route_id = br.id
        JOIN bus_stops bs1 ON rs1.stop_id = bs1.id  
        JOIN bus_stops bs2 ON rs2.stop_id = bs2.id
        LEFT JOIN vehicles v ON br.id = v.assigned_route_id
        WHERE rs1.stop_id = ANY(CAST(:fromStopIds AS TEXT[]))
        AND rs2.stop_id = ANY(CAST(:toStopIds AS TEXT[]))
        AND br.is_active = true
        AND bs1.is_active = true 
        AND bs2.is_active = true
        GROUP BY br.id, br.route_number, br.route_name, br.route_color,
                 br.route_geometry_forward, br.total_distance_forward_meters,
                 rs1.stop_id, bs1.stop_name, bs1.latitude, bs1.longitude,
                 rs2.stop_id, bs2.stop_name, bs2.latitude, bs2.longitude,
                 rs1.stop_sequence, rs2.stop_sequence, rs1.direction,
                 rs1.distance_from_start_meters, rs2.distance_from_start_meters
        ORDER BY estimated_travel_minutes, active_vehicles_count DESC
        LIMIT :limit
        """;

        String[] fromStopIds = fromStops.stream()
                .map(stop -> stop.getId().getValue())
                .filter(Objects::nonNull)
                .toArray(String[]::new);

        String[] toStopIds = toStops.stream()
                .map(stop -> stop.getId().getValue())
                .filter(Objects::nonNull)
                .toArray(String[]::new);

        if (fromStopIds.length == 0 || toStopIds.length == 0) {
            log.warn("❌ Empty stop arrays for direct routes");
            return Flux.empty();
        }

        return databaseClient.sql(sql)
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .bind("limit", QueryConstants.DIRECT_ROUTES_LIMIT)
                .map(this::mapToDirectRouteResult)
                .all()
                .filter(Objects::nonNull)
                .doOnNext(this::logDirectRouteResult);
    }

    @Override
    public Flux<TransferRouteResult> findRoutesWithOneTransfer(List<BusStop> fromStops, List<BusStop> toStops,
                                                               double maxTransferDistanceKm) {
        log.debug("Finding routes with one transfer (max transfer distance: {}km)", maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        String sql = buildOneTransferQuery();
        QueryParams params = new QueryParams()
                .addStopIds("fromStopIds", fromStops)
                .addStopIds("toStopIds", toStops)
                .addLimit("limit", QueryConstants.ONE_TRANSFER_LIMIT);

        return executeValidatedQuery(sql, params, this::mapToTransferRouteResult)
                .doOnNext(this::logTransferRouteResult)
                .doOnComplete(() -> log.debug("One transfer routes search completed with validation"))
                .onErrorResume(error -> {
                    log.error("Error finding one-transfer routes", error);
                    return Flux.empty();
                });
    }

    @Override
    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(List<BusStop> fromStops, List<BusStop> toStops,
                                                                   double maxTransferDistanceKm) {
        long startTime = System.currentTimeMillis();
        log.debug("Starting two-transfer search: {} origin stops, {} destination stops, max distance: {}km",
                fromStops.size(), toStops.size(), maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        double optimalDistance = calculateOptimalTransferDistance(fromStops, toStops, maxTransferDistanceKm);
        String cacheKey = buildTwoTransferCacheKey(fromStops, toStops, optimalDistance);

        return getCachedTwoTransferResults(cacheKey)
                .switchIfEmpty(performTwoTransferSearch(fromStops, toStops, optimalDistance))
                .onErrorResume(throwable -> {
                    log.error("Error in two-transfer search: {}", throwable.getMessage(), throwable);
                    return Flux.empty();
                })
                .doOnComplete(() -> {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.debug("Two-transfer search completed in {}ms", totalTime);
                });
    }

    private String buildDirectRoutesQuery() {
        return """
        WITH candidate_routes AS (
            SELECT DISTINCT 
                br.id as route_id, br.route_number, br.route_name, br.route_color,
                br.route_geometry_forward, br.total_distance_forward_meters,
                rs1.stop_id as from_stop_id, bs1.stop_name as from_stop_name,
                bs1.latitude as from_lat, bs1.longitude as from_lon,
                rs2.stop_id as to_stop_id, bs2.stop_name as to_stop_name,
                bs2.latitude as to_lat, bs2.longitude as to_lon,
                rs1.stop_sequence as from_sequence, rs2.stop_sequence as to_sequence,
                rs1.direction,
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as estimated_travel_minutes,
                ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as distance_meters,
                COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                AND rs1.direction = rs2.direction
                                AND rs1.stop_sequence < rs2.stop_sequence
            JOIN bus_routes br ON rs1.route_id = br.id
            JOIN bus_stops bs1 ON rs1.stop_id = bs1.id  
            JOIN bus_stops bs2 ON rs2.stop_id = bs2.id
            LEFT JOIN vehicles v ON br.id = v.assigned_route_id
            WHERE rs1.stop_id = ANY(:fromStopIds) 
            AND rs2.stop_id = ANY(:toStopIds)
            """ + BASE_ACTIVE_CONDITIONS + """
            GROUP BY br.id, br.route_number, br.route_name, br.route_color,
                     br.route_geometry_forward, br.total_distance_forward_meters,
                     rs1.stop_id, bs1.stop_name, bs1.latitude, bs1.longitude,
                     rs2.stop_id, bs2.stop_name, bs2.latitude, bs2.longitude,
                     rs1.stop_sequence, rs2.stop_sequence, rs1.direction,
                     rs1.distance_from_start_meters, rs2.distance_from_start_meters
        ),
        """ + ROUTE_VALIDATION_CTE + """
        SELECT *
        FROM validated_routes 
        WHERE has_from_stop = true AND has_to_stop = true
        ORDER BY estimated_travel_minutes, active_vehicles_count DESC
        LIMIT :limit
        """;
    }

    private String buildOneTransferQuery() {
        return """
        WITH potential_transfers AS (
            SELECT DISTINCT
                rs1.route_id as first_route_id,
                rs1.stop_id as from_stop_id,
                rs2.stop_id as transfer_stop_id,
                rs3.route_id as second_route_id,
                rs4.stop_id as to_stop_id,
                bs_transfer.stop_name as transfer_stop_name,
                bs_transfer.latitude as transfer_lat,
                bs_transfer.longitude as transfer_lon,
                bs_transfer.is_major_stop as transfer_is_major,
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as first_route_minutes,
                ABS(rs4.stop_sequence - rs3.stop_sequence) * 2 as second_route_minutes,
                rs2.distance_from_start_meters - rs1.distance_from_start_meters as first_route_distance,
                rs4.distance_from_start_meters - rs3.distance_from_start_meters as second_route_distance
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                AND rs1.direction = rs2.direction
                                AND rs1.stop_sequence < rs2.stop_sequence
            JOIN route_stops rs3 ON rs2.stop_id = rs3.stop_id 
                                AND rs3.direction = rs2.direction
                                AND rs1.route_id != rs3.route_id
            JOIN route_stops rs4 ON rs3.route_id = rs4.route_id 
                                AND rs3.direction = rs4.direction
                                AND rs3.stop_sequence < rs4.stop_sequence
            JOIN bus_stops bs_transfer ON rs2.stop_id = bs_transfer.id
            WHERE rs1.stop_id = ANY(:fromStopIds)
            AND rs4.stop_id = ANY(:toStopIds)
            AND (rs2.stop_sequence - rs1.stop_sequence) <= 15
            AND (rs4.stop_sequence - rs3.stop_sequence) <= 15
        ),
        validated_transfers AS (
            SELECT pt.*,
                EXISTS(
                    SELECT 1 FROM route_stops rs_v1a, route_stops rs_v1b
                    WHERE rs_v1a.route_id = pt.first_route_id
                    AND rs_v1b.route_id = pt.first_route_id  
                    AND rs_v1a.direction = rs_v1b.direction
                    AND rs_v1a.stop_sequence < rs_v1b.stop_sequence
                    AND rs_v1a.stop_id = ANY(:fromStopIds)
                    AND rs_v1b.stop_id = pt.transfer_stop_id
                ) as first_route_valid,
                EXISTS(
                    SELECT 1 FROM route_stops rs_v2a, route_stops rs_v2b
                    WHERE rs_v2a.route_id = pt.second_route_id
                    AND rs_v2b.route_id = pt.second_route_id  
                    AND rs_v2a.direction = rs_v2b.direction
                    AND rs_v2a.stop_sequence < rs_v2b.stop_sequence
                    AND rs_v2a.stop_id = pt.transfer_stop_id
                    AND rs_v2b.stop_id = ANY(:toStopIds)
                ) as second_route_valid
            FROM potential_transfers pt
        ),
        route_vehicles AS (
            SELECT 
                br.id as route_id,
                COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles
            FROM bus_routes br
            LEFT JOIN vehicles v ON br.id = v.assigned_route_id
            WHERE br.is_active = true
            GROUP BY br.id
        )
        SELECT 
            vt.*,
            br1.route_number as first_route_number,
            br1.route_name as first_route_name,
            br1.route_color as first_route_color,
            br2.route_number as second_route_number,
            br2.route_name as second_route_name,
            br2.route_color as second_route_color,
            bs_from.stop_name as from_stop_name,
            bs_from.latitude as from_lat,
            bs_from.longitude as from_lon,
            bs_to.stop_name as to_stop_name,
            bs_to.latitude as to_lat,
            bs_to.longitude as to_lon,
            COALESCE(rv1.active_vehicles, 0) as first_route_vehicles,
            COALESCE(rv2.active_vehicles, 0) as second_route_vehicles
        FROM validated_transfers vt
        JOIN bus_routes br1 ON vt.first_route_id = br1.id
        JOIN bus_routes br2 ON vt.second_route_id = br2.id
        JOIN bus_stops bs_from ON vt.from_stop_id = bs_from.id
        JOIN bus_stops bs_to ON vt.to_stop_id = bs_to.id
        LEFT JOIN route_vehicles rv1 ON br1.id = rv1.route_id
        LEFT JOIN route_vehicles rv2 ON br2.id = rv2.route_id
        WHERE br1.is_active = true AND br2.is_active = true
        AND vt.first_route_valid = true AND vt.second_route_valid = true
        ORDER BY (vt.first_route_minutes + vt.second_route_minutes), 
                 (COALESCE(rv1.active_vehicles, 0) + COALESCE(rv2.active_vehicles, 0)) DESC
        LIMIT :limit
        """;
    }

    private String buildTransferValidationSubquery(String routeIdField, String fromStopField, String toStopField) {
        return String.format("""
            EXISTS(
                SELECT 1 FROM route_stops rs_v1a, route_stops rs_v1b
                WHERE rs_v1a.route_id = pt.%s
                AND rs_v1b.route_id = pt.%s  
                AND rs_v1a.direction = rs_v1b.direction
                AND rs_v1a.stop_sequence < rs_v1b.stop_sequence
                AND rs_v1a.stop_id = %s
                AND rs_v1b.stop_id = %s
            )""",
                routeIdField,
                routeIdField,
                fromStopField.startsWith(":") ? "ANY(" + fromStopField + ")" : "pt." + fromStopField,
                toStopField.startsWith(":") ? "ANY(" + toStopField + ")" : "pt." + toStopField);
    }

//    private <T> Flux<T> executeValidatedQuery(String sql, QueryParams params, RowMapper<T> mapper) {
//        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
//        params.applyTo(spec);
//
//        return spec.map(mapper::map)
//                .all()
//                .filter(Objects::nonNull);
//    }

    private <T> Flux<T> executeValidatedQuery(String sql, QueryParams params, RowMapper<T> mapper) {
        try {
            DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);

            for (Map.Entry<String, Object> entry : params.getParams().entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                if (paramValue == null) {
                    if (paramName.contains("StopIds")) {
                        spec = spec.bindNull(paramName, String[].class);
                    } else if (paramName.contains("Distance") || paramName.contains("Time")) {
                        spec = spec.bindNull(paramName, Double.class);
                    } else {
                        spec = spec.bindNull(paramName, Object.class);
                    }
                } else if (paramValue instanceof String[] arrayValue) {
                    if (arrayValue.length == 0) {
                        spec = spec.bindNull(paramName, String[].class);
                    } else {
                        spec = spec.bind(paramName, arrayValue);
                    }
                } else {
                    spec = spec.bind(paramName, paramValue);
                }
            }

            return spec.map(mapper::map).all().filter(Objects::nonNull);

        } catch (Exception e) {
            log.error("❌ SQL execution failed for query: {}", sql.substring(0, Math.min(100, sql.length())), e);
            return Flux.empty();
        }
    }

    private static class QueryParams {
        private final Map<String, Object> params = new HashMap<>();

        QueryParams addStopIds(String paramName, List<BusStop> stops) {
            if (stops == null || stops.isEmpty()) {
                params.put(paramName, new String[0]);
            } else {
                String[] stopIds = stops.stream()
                        .map(stop -> stop.getId().getValue())
                        .filter(Objects::nonNull)
                        .toArray(String[]::new);
                params.put(paramName, stopIds);
            }
            return this;
        }

        QueryParams addLimit(String paramName, int limit) {
            params.put(paramName, Math.max(1, limit));
            return this;
        }

        QueryParams add(String name, Object value) {
            if (value instanceof Double && (Double.isNaN((Double) value) || Double.isInfinite((Double) value))) {
                params.put(name, 0.0);
            } else {
                params.put(name, value);
            }
            return this;
        }

        void applyTo(DatabaseClient.GenericExecuteSpec spec) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                try {
                    if (paramValue == null) {
                        spec = spec.bindNull(paramName, Object.class);
                    } else {
                        spec = spec.bind(paramName, paramValue);
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to bind parameter {}: {}", paramName, e.getMessage());
                }
            }
        }

        public Map<String, Object> getParams() {
            return new HashMap<>(params);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(Row row, RowMetadata metadata);
    }

    private String buildNearbyStopsCacheKey(Location location, double radiusKm) {
        return String.format("nearby_stops:%.6f:%.6f:%.1f",
                location.getLatitude(), location.getLongitude(), radiusKm);
    }

    private Flux<BusStop> getCachedNearbyStops(String cacheKey) {
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(List.class)
                .flatMapMany(cachedStops -> {
                    log.debug("Found cached nearby stops for location");
                    return Flux.fromIterable((List<String>) cachedStops)
                            .flatMap(stopId -> busStopRepository.findById(BusStopId.of(stopId)));
                })
                .onErrorResume(e -> {
                    log.debug("Cache miss or error: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private Flux<BusStop> searchAndCacheNearbyStops(Location location, double radiusKm, String cacheKey) {
        return busStopRepository.findStopsWithinRadius(
                        location.getLatitude(),
                        location.getLongitude(),
                        radiusKm
                )
                .collectList()
                .flatMapMany(stops -> {
                    List<String> stopIds = stops.stream()
                            .map(stop -> stop.getId().getValue())
                            .toList();

                    return redisTemplate.opsForValue()
                            .set(cacheKey, stopIds, QueryConstants.NEARBY_STOPS_CACHE_TTL)
                            .thenMany(Flux.fromIterable(stops));
                });
    }


    private void logDirectRouteResult(DirectRouteResult result) {
        log.debug("✅ VALIDATED direct route: {} from {} to {} ({} minutes)",
                result.route().getRouteNumber(),
                result.fromStop().getStopName(),
                result.toStop().getStopName(),
                result.estimatedTravelMinutes());
    }

    private void logTransferRouteResult(TransferRouteResult result) {
        log.debug("✅ VALIDATED transfer route: {}-{} via {} ({} + {} minutes)",
                result.firstRoute().getRouteNumber(),
                result.secondRoute().getRouteNumber(),
                result.transferStop().getStopName(),
                result.firstRouteTravelMinutes(),
                result.secondRouteTravelMinutes());
    }



    private int adjustTravelTimeByVehicleCount(Integer baseMinutes, Long vehicleCount) {
        if (baseMinutes == null) return 20;
        if (vehicleCount == null || vehicleCount == 0) return baseMinutes + 10;

        if (vehicleCount >= 5) return baseMinutes;
        if (vehicleCount >= 3) return baseMinutes + 2;
        if (vehicleCount >= 1) return baseMinutes + 5;

        return baseMinutes + 10;
    }

    private int calculateTransferWaitTime(Boolean isMajorStop, Long fromRouteVehicles, Long toRouteVehicles) {
        int baseTransferTime = Boolean.TRUE.equals(isMajorStop) ? 3 : 5;
        long toVehicleCount = toRouteVehicles != null ? toRouteVehicles : 0;

        int waitTime;
        if (toVehicleCount >= 5) waitTime = 4;
        else if (toVehicleCount >= 3) waitTime = 7;
        else if (toVehicleCount >= 1) waitTime = 12;
        else waitTime = 18;

        long fromVehicleCount = fromRouteVehicles != null ? fromRouteVehicles : 0;
        if (fromVehicleCount >= 5) waitTime -= 2;
        else if (fromVehicleCount >= 3) waitTime -= 1;

        return Math.max(baseTransferTime + waitTime + 2, 5);
    }

    private double calculateOptimalTransferDistance(List<BusStop> fromStops, List<BusStop> toStops, double maxDistance) {
        if (fromStops.size() > 6 && toStops.size() > 6) {
            return Math.min(maxDistance, 0.3);
        }
        if (fromStops.size() <= 3 || toStops.size() <= 3) {
            return Math.min(maxDistance, 0.5);
        }
        return Math.min(maxDistance, 0.4);
    }

    @Override
    public Flux<BusRoute> getConnectingRoutes(BusStop fromStop, BusStop toStop) {
        String sql = """
            SELECT DISTINCT br.*
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                AND rs1.direction = rs2.direction
                                AND rs1.stop_sequence < rs2.stop_sequence
            JOIN bus_routes br ON rs1.route_id = br.id
            WHERE rs1.stop_id = :fromStopId 
            AND rs2.stop_id = :toStopId
            AND br.is_active = true
            ORDER BY ABS(rs2.stop_sequence - rs1.stop_sequence)
            """;

        return databaseClient.sql(sql)
                .bind("fromStopId", fromStop.getId().getValue())
                .bind("toStopId", toStop.getId().getValue())
                .map(row -> new BusRoute(
                        row.get("route_number", String.class),
                        row.get("route_name", String.class),
                        row.get("name_tm", String.class),
                        row.get("name_en", String.class),
                        row.get("route_color", String.class)
                ))
                .all();
    }


    @Override
    public Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2) {
        String cacheKey = String.format("stops_connected:%s:%s",
                stop1.getId().getValue(), stop2.getId().getValue());

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(Boolean.class)
                .switchIfEmpty(
                        checkStopsConnectionInDatabase(stop1, stop2)
                                .flatMap(connected ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, connected, QueryConstants.CONNECTION_CACHE_TTL)
                                                .thenReturn(connected)
                                )
                );
    }


    private Flux<TwoTransferRouteResult> performTwoTransferSearch(List<BusStop> fromStops, List<BusStop> toStops,
                                                                  double maxTransferDistanceKm) {

        String sql = """
        WITH 
        first_segments AS (
            SELECT DISTINCT
                rs1.route_id as first_route_id,
                rs1.stop_id as from_stop_id,
                rs2.stop_id as first_transfer_stop_id,
                rs1.direction as first_direction,
                bs_t1.stop_name as first_transfer_name,
                bs_t1.latitude as first_transfer_lat,
                bs_t1.longitude as first_transfer_lon,
                bs_t1.is_major_stop as first_transfer_is_major,
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as first_route_minutes,
                ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as first_route_distance
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                AND rs1.direction = rs2.direction
                                AND rs1.stop_sequence < rs2.stop_sequence
                                AND (rs2.stop_sequence - rs1.stop_sequence) <= :maxStopsPerSegment
            JOIN bus_stops bs_t1 ON rs2.stop_id = bs_t1.id
            JOIN bus_routes br1 ON rs1.route_id = br1.id
            WHERE rs1.stop_id = ANY(CAST(:fromStopIds AS TEXT[]))
            AND br1.is_active = true
            AND bs_t1.is_active = true
        ),
        
        -- Второй уровень: находим вторые сегменты (transfer1 → transfer2)
        second_segments AS (
            SELECT DISTINCT
                fs.first_route_id,
                fs.from_stop_id,
                fs.first_transfer_stop_id,
                fs.first_transfer_name,
                fs.first_transfer_lat,
                fs.first_transfer_lon,
                fs.first_transfer_is_major,
                fs.first_route_minutes,
                rs3.route_id as second_route_id,
                rs4.stop_id as second_transfer_stop_id,
                rs3.direction as second_direction,
                bs_t2.stop_name as second_transfer_name,
                bs_t2.latitude as second_transfer_lat,
                bs_t2.longitude as second_transfer_lon,
                bs_t2.is_major_stop as second_transfer_is_major,
                ABS(rs4.stop_sequence - rs3.stop_sequence) * 2 as second_route_minutes,
                ABS(rs4.distance_from_start_meters - rs3.distance_from_start_meters) as second_route_distance,
                ST_Distance(
                    ST_Point(CAST(fs.first_transfer_lon AS double precision), CAST(fs.first_transfer_lat AS double precision))::geography,
                    ST_Point(CAST(bs_t2.longitude AS double precision), CAST(bs_t2.latitude AS double precision))::geography
                ) as transfer_distance_meters
            FROM first_segments fs
            JOIN route_stops rs3 ON fs.first_transfer_stop_id = rs3.stop_id
            JOIN route_stops rs4 ON rs3.route_id = rs4.route_id 
                                AND rs3.direction = rs4.direction
                                AND rs3.stop_sequence < rs4.stop_sequence
                                AND (rs4.stop_sequence - rs3.stop_sequence) <= :maxMiddleSegmentStops
            JOIN bus_stops bs_t2 ON rs4.stop_id = bs_t2.id
            JOIN bus_routes br2 ON rs3.route_id = br2.id
            WHERE fs.first_route_id != rs3.route_id
            AND br2.is_active = true
            AND bs_t2.is_active = true
            AND ST_Distance(
                ST_Point(CAST(fs.first_transfer_lon AS double precision), CAST(fs.first_transfer_lat AS double precision))::geography,
                ST_Point(CAST(bs_t2.longitude AS double precision), CAST(bs_t2.latitude AS double precision))::geography
            ) <= CAST(:maxTransferDistanceMeters AS double precision)
        ),
        
        -- Третий уровень: находим финальные сегменты (transfer2 → B)
        complete_routes AS (
            SELECT DISTINCT
                ss.*,
                rs5.route_id as third_route_id,
                rs6.stop_id as to_stop_id,
                rs5.direction as third_direction,
                ABS(rs6.stop_sequence - rs5.stop_sequence) * 2 as third_route_minutes,
                ABS(rs6.distance_from_start_meters - rs5.distance_from_start_meters) as third_route_distance
            FROM second_segments ss
            JOIN route_stops rs5 ON ss.second_transfer_stop_id = rs5.stop_id
            JOIN route_stops rs6 ON rs5.route_id = rs6.route_id 
                                AND rs5.direction = rs6.direction
                                AND rs5.stop_sequence < rs6.stop_sequence
                                AND (rs6.stop_sequence - rs5.stop_sequence) <= :maxStopsPerSegment
            JOIN bus_routes br3 ON rs5.route_id = br3.id
            WHERE ss.second_route_id != rs5.route_id
            AND rs6.stop_id = ANY(CAST(:toStopIds AS TEXT[]))
            AND br3.is_active = true
        ),
        
        -- КРИТИЧЕСКИ ВАЖНЫЙ CTE для подсчета автобусов по каждому маршруту
        route_vehicles AS (
            SELECT 
                br.id as route_id,
                COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles
            FROM bus_routes br
            LEFT JOIN vehicles v ON br.id = v.assigned_route_id
            WHERE br.is_active = true
            GROUP BY br.id
        )
        
        -- Финальный SELECT с дополнительной информацией о маршрутах
        SELECT 
            cr.first_route_id,
            cr.from_stop_id,
            cr.first_transfer_stop_id,
            cr.second_route_id,
            cr.second_transfer_stop_id,
            cr.third_route_id,
            cr.to_stop_id,
            cr.first_transfer_name,
            cr.first_transfer_lat,
            cr.first_transfer_lon,
            cr.first_transfer_is_major,
            cr.second_transfer_name,
            cr.second_transfer_lat,
            cr.second_transfer_lon,
            cr.second_transfer_is_major,
            cr.first_route_minutes,
            cr.second_route_minutes,
            cr.third_route_minutes,
            cr.transfer_distance_meters,
            br1.route_number as first_route_number,
            br1.route_name as first_route_name,
            br1.route_color as first_route_color,
            br2.route_number as second_route_number,
            br2.route_name as second_route_name,
            br2.route_color as second_route_color,
            br3.route_number as third_route_number,
            br3.route_name as third_route_name,
            br3.route_color as third_route_color,
            bs_from.stop_name as from_stop_name,
            bs_from.latitude as from_lat,
            bs_from.longitude as from_lon,
            bs_to.stop_name as to_stop_name,
            bs_to.latitude as to_lat,
            bs_to.longitude as to_lon,
            COALESCE(rv1.active_vehicles, 0) as first_route_vehicles,
            COALESCE(rv2.active_vehicles, 0) as second_route_vehicles,
            COALESCE(rv3.active_vehicles, 0) as third_route_vehicles,
            (cr.first_route_minutes + cr.second_route_minutes + cr.third_route_minutes) as total_travel_time
        FROM complete_routes cr
        JOIN bus_routes br1 ON cr.first_route_id = br1.id
        JOIN bus_routes br2 ON cr.second_route_id = br2.id
        JOIN bus_routes br3 ON cr.third_route_id = br3.id
        JOIN bus_stops bs_from ON cr.from_stop_id = bs_from.id
        JOIN bus_stops bs_to ON cr.to_stop_id = bs_to.id
        LEFT JOIN route_vehicles rv1 ON br1.id = rv1.route_id
        LEFT JOIN route_vehicles rv2 ON br2.id = rv2.route_id
        LEFT JOIN route_vehicles rv3 ON br3.id = rv3.route_id
        WHERE 
            (cr.first_route_minutes + cr.second_route_minutes + cr.third_route_minutes) <= :maxTotalTravelTime
            AND cr.first_route_minutes >= :minSegmentTime
            AND cr.second_route_minutes >= :minSegmentTime
            AND cr.third_route_minutes >= :minSegmentTime
        ORDER BY 
            total_travel_time,
            (COALESCE(rv1.active_vehicles, 0) + COALESCE(rv2.active_vehicles, 0) + COALESCE(rv3.active_vehicles, 0)) DESC,
            (CASE WHEN cr.first_transfer_is_major THEN 0 ELSE 1 END + 
             CASE WHEN cr.second_transfer_is_major THEN 0 ELSE 1 END)
        LIMIT :limit
        """;

        // ИСПРАВЛЕННЫЙ BINDING ПАРАМЕТРОВ
        String[] fromStopIds = fromStops.stream()
                .map(stop -> stop.getId().getValue())
                .filter(Objects::nonNull)
                .toArray(String[]::new);

        String[] toStopIds = toStops.stream()
                .map(stop -> stop.getId().getValue())
                .filter(Objects::nonNull)
                .toArray(String[]::new);

        // ДОБАВЛЯЕМ ВАЛИДАЦИЮ
        if (fromStopIds.length == 0 || toStopIds.length == 0) {
            log.warn("❌ Empty stop arrays: from={}, to={}", fromStopIds.length, toStopIds.length);
            return Flux.empty();
        }

        log.debug("🔍 Two-transfer search: {} from stops, {} to stops, max distance={}m",
                fromStopIds.length, toStopIds.length, maxTransferDistanceKm * 1000);

        return databaseClient.sql(sql)
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .bind("maxTransferDistanceMeters", maxTransferDistanceKm * 1000.0)
                .bind("maxStopsPerSegment", QueryConstants.MAX_STOPS_PER_SEGMENT)
                .bind("maxMiddleSegmentStops", QueryConstants.MAX_MIDDLE_SEGMENT_STOPS)
                .bind("maxTotalTravelTime", QueryConstants.MAX_TOTAL_TRAVEL_TIME_MINUTES)
                .bind("minSegmentTime", QueryConstants.MIN_SEGMENT_TIME_MINUTES)
                .bind("limit", QueryConstants.TWO_TRANSFER_LIMIT)
                .map(this::mapToTwoTransferRouteResult)
                .all()
                .filter(Objects::nonNull)
                .doOnNext(result -> {
                    if (result != null) {
                        log.debug("✅ Found two-transfer route: {}-{}-{} ({} + {} + {} = {} minutes)",
                                result.firstRoute().getRouteNumber(),
                                result.secondRoute().getRouteNumber(),
                                result.thirdRoute().getRouteNumber(),
                                result.firstRouteTravelMinutes(),
                                result.secondRouteTravelMinutes(),
                                result.thirdRouteTravelMinutes(),
                                result.firstRouteTravelMinutes() + result.secondRouteTravelMinutes() + result.thirdRouteTravelMinutes());
                    }
                })
                .doOnError(error -> log.error("❌ Two-transfer search failed: {}", error.getMessage(), error))
                .onErrorResume(error -> {
                    log.error("🔄 Recovering from two-transfer search error", error);
                    return Flux.empty();
                });
    }


    private Flux<TwoTransferRouteResult> getCachedTwoTransferResults(String cacheKey) {
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(String.class)
                .flatMapMany(jsonString -> {
                    try {
                        log.debug("Found cached two-transfer routes");
                        TypeReference<List<TwoTransferRouteResultDTO>> typeRef = new TypeReference<>() {};
                        List<TwoTransferRouteResultDTO> dtoList = objectMapper.readValue(jsonString, typeRef);

                        List<TwoTransferRouteResult> results = dtoList.stream()
                                .map(this::convertDTOToTwoTransferResult)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        return Flux.fromIterable(results);

                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cached two-transfer results: {}", e.getMessage());
                        return redisTemplate.delete(cacheKey).thenMany(Flux.empty());
                    }
                })
                .onErrorResume(e -> {
                    log.debug("No valid cached results found: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private String buildTwoTransferCacheKey(List<BusStop> fromStops, List<BusStop> toStops, double distance) {
        return String.format("two_transfers:v3:%s:%s:%.1f",
                fromStops.stream().map(s -> s.getId().getValue()).sorted().collect(Collectors.joining(",")),
                toStops.stream().map(s -> s.getId().getValue()).sorted().collect(Collectors.joining(",")),
                distance);
    }


    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwoTransferRouteResultDTO {
        public RouteDTO firstRoute;
        public BusStopDTO fromStop;
        public BusStopDTO firstTransferStop;
        public RouteDTO secondRoute;
        public BusStopDTO secondTransferStop;
        public RouteDTO thirdRoute;
        public BusStopDTO toStop;
        public int firstRouteTravelMinutes;
        public int firstTransferWaitMinutes;
        public int secondRouteTravelMinutes;
        public int secondTransferWaitMinutes;
        public int thirdRouteTravelMinutes;
        public double walkingDistanceToStart;
        public double walkingDistanceFromEnd;

        public TwoTransferRouteResultDTO() {}

        public TwoTransferRouteResultDTO(RouteDTO firstRoute, BusStopDTO fromStop, BusStopDTO firstTransferStop,
                                         RouteDTO secondRoute, BusStopDTO secondTransferStop, RouteDTO thirdRoute,
                                         BusStopDTO toStop, int firstRouteTravelMinutes, int firstTransferWaitMinutes,
                                         int secondRouteTravelMinutes, int secondTransferWaitMinutes,
                                         int thirdRouteTravelMinutes, double walkingDistanceToStart,
                                         double walkingDistanceFromEnd) {
            this.firstRoute = firstRoute;
            this.fromStop = fromStop;
            this.firstTransferStop = firstTransferStop;
            this.secondRoute = secondRoute;
            this.secondTransferStop = secondTransferStop;
            this.thirdRoute = thirdRoute;
            this.toStop = toStop;
            this.firstRouteTravelMinutes = firstRouteTravelMinutes;
            this.firstTransferWaitMinutes = firstTransferWaitMinutes;
            this.secondRouteTravelMinutes = secondRouteTravelMinutes;
            this.secondTransferWaitMinutes = secondTransferWaitMinutes;
            this.thirdRouteTravelMinutes = thirdRouteTravelMinutes;
            this.walkingDistanceToStart = walkingDistanceToStart;
            this.walkingDistanceFromEnd = walkingDistanceFromEnd;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteDTO {
        public String routeNumber;
        public String routeName;
        public String routeColor;

        public RouteDTO() {}

        public RouteDTO(String routeNumber, String routeName, String routeColor) {
            this.routeNumber = routeNumber;
            this.routeName = routeName;
            this.routeColor = routeColor;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BusStopDTO {
        public String id;
        public String stopName;
        public double latitude;
        public double longitude;
        public boolean isMajorStop;

        public BusStopDTO() {}

        public BusStopDTO(String id, String stopName, double latitude, double longitude, boolean isMajorStop) {
            this.id = id;
            this.stopName = stopName;
            this.latitude = latitude;
            this.longitude = longitude;
            this.isMajorStop = isMajorStop;
        }
    }


    private Mono<Boolean> checkStopsConnectionInDatabase(BusStop stop1, BusStop stop2) {
        String sql = """
            SELECT COUNT(*) > 0 as connected
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            WHERE rs1.stop_id = :stop1Id AND rs2.stop_id = :stop2Id
            """;

        return databaseClient.sql(sql)
                .bind("stop1Id", stop1.getId().getValue())
                .bind("stop2Id", stop2.getId().getValue())
                .map(row -> row.get("connected", Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }


    private TwoTransferRouteResult convertDTOToTwoTransferResult(TwoTransferRouteResultDTO dto) {
        try {
            BusRoute firstRoute = new BusRoute(dto.firstRoute.routeNumber, dto.firstRoute.routeName, null, null, dto.firstRoute.routeColor);
            BusRoute secondRoute = new BusRoute(dto.secondRoute.routeNumber, dto.secondRoute.routeName, null, null, dto.secondRoute.routeColor);
            BusRoute thirdRoute = new BusRoute(dto.thirdRoute.routeNumber, dto.thirdRoute.routeName, null, null, dto.thirdRoute.routeColor);

            BusStop fromStop = new BusStop(dto.fromStop.stopName, dto.fromStop.id,
                    BigDecimal.valueOf(dto.fromStop.latitude), BigDecimal.valueOf(dto.fromStop.longitude));
            BusStop firstTransferStop = new BusStop(dto.firstTransferStop.stopName, dto.firstTransferStop.id,
                    BigDecimal.valueOf(dto.firstTransferStop.latitude), BigDecimal.valueOf(dto.firstTransferStop.longitude));
            BusStop secondTransferStop = new BusStop(dto.secondTransferStop.stopName, dto.secondTransferStop.id,
                    BigDecimal.valueOf(dto.secondTransferStop.latitude), BigDecimal.valueOf(dto.secondTransferStop.longitude));
            BusStop toStop = new BusStop(dto.toStop.stopName, dto.toStop.id,
                    BigDecimal.valueOf(dto.toStop.latitude), BigDecimal.valueOf(dto.toStop.longitude));

            return new TwoTransferRouteResult(
                    firstRoute, fromStop, firstTransferStop,
                    secondRoute, secondTransferStop,
                    thirdRoute, toStop,
                    dto.firstRouteTravelMinutes, dto.firstTransferWaitMinutes,
                    dto.secondRouteTravelMinutes, dto.secondTransferWaitMinutes,
                    dto.thirdRouteTravelMinutes, dto.walkingDistanceToStart, dto.walkingDistanceFromEnd
            );

        } catch (Exception e) {
            log.warn("Failed to convert DTO to TwoTransferRouteResult: {}", e.getMessage());
            return null;
        }
    }


    private DirectRouteResult mapToDirectRouteResult(Row row, RowMetadata rowMetadata) {
        try {
            BusRoute route = new BusRoute(
                    BusRouteId.of(row.get("route_id", String.class)),
                    row.get("route_number", String.class),
                    row.get("route_name", String.class),
                    null,
                    null,
                    row.get("route_color", String.class) != null ?
                            row.get("route_color", String.class) : "#1976D2",
                    null,
                    true,
                    null,
                    row.get("route_geometry_forward", String.class),
                    null,
                    row.get("total_distance_forward_meters", Integer.class),
                    null
            );

            BusStop fromStop = new BusStop(
                    row.get("from_stop_name", String.class),
                    row.get("from_stop_id", String.class),
                    row.get("from_lat", BigDecimal.class),
                    row.get("from_lon", BigDecimal.class)
            );

            BusStop toStop = new BusStop(
                    row.get("to_stop_name", String.class),
                    row.get("to_stop_id", String.class),
                    row.get("to_lat", BigDecimal.class),
                    row.get("to_lon", BigDecimal.class)
            );

            Integer estimatedMinutes = row.get("estimated_travel_minutes", Integer.class);
            Long activeVehicles = row.get("active_vehicles_count", Long.class);

            int adjustedMinutes = adjustTravelTimeByVehicleCount(estimatedMinutes, activeVehicles);

            return new DirectRouteResult(route, fromStop, toStop, adjustedMinutes, 0.0, 0.0);
        } catch (Exception e) {
            log.error("Error mapping direct route result", e);
            return null;
        }
    }


    private TransferRouteResult mapToTransferRouteResult(Row row, RowMetadata rowMetadata) {
        try {
            BusRoute firstRoute = new BusRoute(
                    row.get("first_route_number", String.class),
                    row.get("first_route_name", String.class),
                    null, null,
                    row.get("first_route_color", String.class) != null ?
                            row.get("first_route_color", String.class) : "#1976D2"
            );

            BusRoute secondRoute = new BusRoute(
                    row.get("second_route_number", String.class),
                    row.get("second_route_name", String.class),
                    null, null,
                    row.get("second_route_color", String.class) != null ?
                            row.get("second_route_color", String.class) : "#4CAF50"
            );

            BusStop fromStop = new BusStop(
                    row.get("from_stop_name", String.class),
                    row.get("from_stop_id", String.class),
                    row.get("from_lat", BigDecimal.class),
                    row.get("from_lon", BigDecimal.class)
            );

            BusStop transferStop = new BusStop(
                    row.get("transfer_stop_name", String.class),
                    row.get("transfer_stop_id", String.class),
                    row.get("transfer_lat", BigDecimal.class),
                    row.get("transfer_lon", BigDecimal.class)
            );


            transferStop = new BusStop(
                    transferStop.getId(),
                    transferStop.getStopName(),
                    transferStop.getStopCode(),
                    transferStop.getLatitude(),
                    transferStop.getLongitude(),
                    true,
                    row.get("transfer_is_major", Boolean.class)
            );

            BusStop toStop = new BusStop(
                    row.get("to_stop_name", String.class),
                    row.get("to_stop_id", String.class),
                    row.get("to_lat", BigDecimal.class),
                    row.get("to_lon", BigDecimal.class)
            );

            Integer firstRouteMinutes = row.get("first_route_minutes", Integer.class);
            Integer secondRouteMinutes = row.get("second_route_minutes", Integer.class);
            Long firstRouteVehicles = row.get("first_route_vehicles", Long.class);
            Long secondRouteVehicles = row.get("second_route_vehicles", Long.class);

            int adjustedFirstMinutes = adjustTravelTimeByVehicleCount(firstRouteMinutes, firstRouteVehicles);
            int adjustedSecondMinutes = adjustTravelTimeByVehicleCount(secondRouteMinutes, secondRouteVehicles);

            int transferWaitTime = calculateTransferWaitTime(transferStop.getIsMajorStop(),
                    firstRouteVehicles, secondRouteVehicles);

            return new TransferRouteResult(
                    firstRoute, fromStop, transferStop,
                    secondRoute, toStop,
                    adjustedFirstMinutes,
                    transferWaitTime,
                    adjustedSecondMinutes,
                    0.0, 0.0
            );
        } catch (Exception e) {
            log.error("Error mapping transfer route result", e);
            return null;
        }
    }

    private TwoTransferRouteResult mapToTwoTransferRouteResult(Row row, RowMetadata rowMetadata) {
        try {
            BusRoute firstRoute = new BusRoute(
                    row.get("first_route_number", String.class),
                    row.get("first_route_name", String.class),
                    null, null,
                    row.get("first_route_color", String.class) != null ?
                            row.get("first_route_color", String.class) : "#1976D2"
            );

            BusRoute secondRoute = new BusRoute(
                    row.get("second_route_number", String.class),
                    row.get("second_route_name", String.class),
                    null, null,
                    row.get("second_route_color", String.class) != null ?
                            row.get("second_route_color", String.class) : "#4CAF50"
            );

            BusRoute thirdRoute = new BusRoute(
                    row.get("third_route_number", String.class),
                    row.get("third_route_name", String.class),
                    null, null,
                    row.get("third_route_color", String.class) != null ?
                            row.get("third_route_color", String.class) : "#FF9800"
            );

            // Создаем остановки
            BusStop fromStop = new BusStop(
                    row.get("from_stop_name", String.class),
                    row.get("from_stop_id", String.class),
                    row.get("from_lat", BigDecimal.class),
                    row.get("from_lon", BigDecimal.class)
            );

            BusStop firstTransferStop = new BusStop(
                    row.get("first_transfer_name", String.class),
                    row.get("first_transfer_stop_id", String.class),
                    row.get("first_transfer_lat", BigDecimal.class),
                    row.get("first_transfer_lon", BigDecimal.class)
            );

            firstTransferStop = new BusStop(
                    firstTransferStop.getId(),
                    firstTransferStop.getStopName(),
                    firstTransferStop.getStopCode(),
                    firstTransferStop.getLatitude(),
                    firstTransferStop.getLongitude(),
                    true,
                    row.get("first_transfer_is_major", Boolean.class)
            );

            BusStop secondTransferStop = new BusStop(
                    row.get("second_transfer_name", String.class),
                    row.get("second_transfer_stop_id", String.class),
                    row.get("second_transfer_lat", BigDecimal.class),
                    row.get("second_transfer_lon", BigDecimal.class)
            );

            secondTransferStop = new BusStop(
                    secondTransferStop.getId(),
                    secondTransferStop.getStopName(),
                    secondTransferStop.getStopCode(),
                    secondTransferStop.getLatitude(),
                    secondTransferStop.getLongitude(),
                    true,
                    row.get("second_transfer_is_major", Boolean.class)
            );

            BusStop toStop = new BusStop(
                    row.get("to_stop_name", String.class),
                    row.get("to_stop_id", String.class),
                    row.get("to_lat", BigDecimal.class),
                    row.get("to_lon", BigDecimal.class)
            );

            Integer firstRouteMinutes = row.get("first_route_minutes", Integer.class);
            Integer secondRouteMinutes = row.get("second_route_minutes", Integer.class);
            Integer thirdRouteMinutes = row.get("third_route_minutes", Integer.class);

            Long firstRouteVehicles = row.get("first_route_vehicles", Long.class);
            Long secondRouteVehicles = row.get("second_route_vehicles", Long.class);
            Long thirdRouteVehicles = row.get("third_route_vehicles", Long.class);

            int adjustedFirstMinutes = adjustTravelTimeByVehicleCount(firstRouteMinutes, firstRouteVehicles);
            int adjustedSecondMinutes = adjustTravelTimeByVehicleCount(secondRouteMinutes, secondRouteVehicles);
            int adjustedThirdMinutes = adjustTravelTimeByVehicleCount(thirdRouteMinutes, thirdRouteVehicles);

            int firstTransferWaitTime = calculateTransferWaitTime(
                    firstTransferStop.getIsMajorStop(), firstRouteVehicles, secondRouteVehicles);
            int secondTransferWaitTime = calculateTransferWaitTime(
                    secondTransferStop.getIsMajorStop(), secondRouteVehicles, thirdRouteVehicles);

            return new TwoTransferRouteResult(
                    firstRoute, fromStop, firstTransferStop,
                    secondRoute, secondTransferStop,
                    thirdRoute, toStop,
                    adjustedFirstMinutes,
                    firstTransferWaitTime,
                    adjustedSecondMinutes,
                    secondTransferWaitTime,
                    adjustedThirdMinutes,
                    0.0, 0.0
            );

        } catch (Exception e) {
            log.error("Error mapping TwoTransferRouteResult: {}", e.getMessage(), e);
            return null;
        }
    }
}

