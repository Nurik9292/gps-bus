package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.*;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class R2dbcRouteStopRepository implements RouteStopRepository {

    private final DatabaseClient databaseClient;

    public R2dbcRouteStopRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }


    @Override
    public Mono<Void> deleteExistingStops(String routeId) {
        String sql = "DELETE FROM route_stops WHERE route_id = :routeId";

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .then();
    }

    @Override
    public Mono<Void> insertRouteStop(String routeId, String stopId, int sequence, int direction) {
        String sql = """
            INSERT INTO route_stops (
                id, route_id, stop_id, stop_sequence, direction, 
                estimated_travel_time_minutes, distance_from_start_meters, created_at
            ) VALUES (
                :id, :routeId, :stopId, :sequence, :direction,
                :estimatedTime, :distance, CURRENT_TIMESTAMP
            )
            """;

        String id = UUID.randomUUID().toString();
        int estimatedTime = sequence * 2; // 2 минуты на остановку (заглушка)
        int distance = sequence * 500; // 500 метров между остановками (заглушка)

        return databaseClient.sql(sql)
                .bind("id", id)
                .bind("routeId", routeId)
                .bind("stopId", stopId)
                .bind("sequence", sequence)
                .bind("direction", direction)
                .bind("estimatedTime", estimatedTime)
                .bind("distance", distance)
                .then();
    }

    @Override
    public Flux<RouteStopInfo> getRouteStops(String routeId, int direction) {
        String sql = """
            SELECT rs.stop_id, rs.stop_sequence, rs.direction, 
                   rs.estimated_travel_time_minutes, rs.distance_from_start_meters,
                   bs.stop_name, bs.latitude, bs.longitude, bs.stop_code, bs.is_major_stop
            FROM route_stops rs
            JOIN bus_stops bs ON rs.stop_id = bs.id
            WHERE rs.route_id = :routeId AND rs.direction = :direction
            ORDER BY rs.stop_sequence
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .bind("direction", direction)
                .map(row -> new RouteStopInfo(
                        row.get("stop_id", String.class),
                        row.get("stop_name", String.class),
                        row.get("stop_code", String.class),
                        row.get("stop_sequence", Integer.class),
                        row.get("direction", Integer.class),
                        row.get("estimated_travel_time_minutes", Integer.class),
                        row.get("distance_from_start_meters", Integer.class),
                        row.get("latitude", BigDecimal.class),
                        row.get("longitude", BigDecimal.class),
                        row.get("is_major_stop", Boolean.class)
                ))
                .all();
    }


    @Override
    public Flux<RouteStopDetail> getRouteStopsDetail(String routeId, int direction) {
        String sql = """
            SELECT 
                rs.stop_id, 
                rs.stop_sequence, 
                rs.direction,
                rs.estimated_travel_time_minutes, 
                rs.distance_from_start_meters,
                bs.stop_name, 
                bs.stop_code,
                bs.latitude, 
                bs.longitude,
                bs.is_major_stop
            FROM route_stops rs
            JOIN bus_stops bs ON rs.stop_id = bs.id
            WHERE rs.route_id = :routeId AND rs.direction = :direction
            ORDER BY rs.stop_sequence
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .bind("direction", direction)
                .map(row -> new RouteStopDetail(
                        row.get("stop_id", String.class),
                        row.get("stop_name", String.class),
                        row.get("stop_code", String.class),
                        row.get("stop_sequence", Integer.class),
                        row.get("direction", Integer.class),
                        row.get("estimated_travel_time_minutes", Integer.class),
                        row.get("distance_from_start_meters", Integer.class),
                        row.get("latitude", BigDecimal.class),
                        row.get("longitude", BigDecimal.class),
                        row.get("is_major_stop", Boolean.class)
                ))
                .all();
    }

    @Override
    public Flux<StopRouteDetail> getStopRoutesDetail(String stopId, int direction) {
        String sql = """
        SELECT 
            rs.route_id, 
            rs.direction,
            rs.estimated_travel_time_minutes, 
            rs.distance_from_start_meters,
            br.route_name, 
            br.route_number
        FROM route_stops rs
        JOIN bus_routes br ON rs.route_id = br.id
        WHERE rs.stop_id = :stopId AND rs.direction = :direction
        ORDER BY rs.stop_sequence
        """;

        return databaseClient.sql(sql)
                .bind("stopId", stopId)
                .bind("direction", direction)
                .map(row -> new StopRouteDetail(
                        row.get("route_id", String.class),
                        row.get("route_name", String.class),
                        row.get("route_number", String.class),
                        row.get("direction", Integer.class),
                        row.get("estimated_travel_time_minutes", Integer.class),
                        row.get("distance_from_start_meters", Integer.class)
                ))
                .all();
    }

    @Override
    public Mono<List<RouteStopDetail>> getForwardStopsDetail(String routeId) {
        return getRouteStopsDetail(routeId, 0).collectList();
    }

    @Override
    public Mono<List<RouteStopDetail>> getBackwardStopsDetail(String routeId) {
        return getRouteStopsDetail(routeId, 1).collectList();
    }

    @Override
    public Mono<RouteStopsData> getAllRouteStopsData(String routeId) {
        Mono<List<RouteStopDetail>> forwardStops = getForwardStopsDetail(routeId);
        Mono<List<RouteStopDetail>> backwardStops = getBackwardStopsDetail(routeId);

        return Mono.zip(forwardStops, backwardStops)
                .map(tuple -> new RouteStopsData(tuple.getT1(), tuple.getT2()));
    }

    @Override
    public Mono<RouteStopsStatistics> getRouteStopsStatistics(String routeId) {
        String sql = """
            SELECT 
                direction,
                COUNT(*) as stops_count,
                MAX(estimated_travel_time_minutes) as total_travel_time,
                MAX(distance_from_start_meters) as total_distance
            FROM route_stops 
            WHERE route_id = :routeId 
            GROUP BY direction
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .map(row -> new DirectionStats(
                        row.get("direction", Integer.class),
                        row.get("stops_count", Long.class),
                        row.get("total_travel_time", Integer.class),
                        row.get("total_distance", Integer.class)
                ))
                .all()
                .collectList()
                .map(stats -> {
                    DirectionStats forward = stats.stream()
                            .filter(s -> s.direction() == 0)
                            .findFirst()
                            .orElse(new DirectionStats(0, 0L, 0, 0));

                    DirectionStats backward = stats.stream()
                            .filter(s -> s.direction() == 1)
                            .findFirst()
                            .orElse(new DirectionStats(1, 0L, 0, 0));

                    return new RouteStopsStatistics(
                            forward.stopsCount(),
                            backward.stopsCount(),
                            forward.totalTravelTime(),
                            backward.totalTravelTime(),
                            forward.totalDistance(),
                            backward.totalDistance()
                    );
                });
    }


    private record DirectionStats(
            Integer direction,
            Long stopsCount,
            Integer totalTravelTime,
            Integer totalDistance
    ) {}

    @Override
    public Mono<NearestStopResult> findNearestStopSequence(String routeId, Double latitude, Double longitude, Integer currentDirection) {
        if (routeId == null || latitude == null || longitude == null) {
            return Mono.empty();
        }

        if (currentDirection == null) {
            String sql = """
                SELECT rs.stop_sequence, rs.direction,
                    6371000 * acos(
                        cos(radians(:latitude)) * cos(radians(bs.latitude)) *
                        cos(radians(bs.longitude) - radians(:longitude)) +
                        sin(radians(:latitude)) * sin(radians(bs.latitude))
                    ) as distance
                FROM route_stops rs
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE rs.route_id = :routeId
                ORDER BY distance
                LIMIT 1
                """;

            return databaseClient.sql(sql)
                    .bind("routeId", routeId)
                    .bind("latitude", latitude)
                    .bind("longitude", longitude)
                    .map(row -> new NearestStopResult(
                            row.get("stop_sequence", Integer.class),
                            row.get("direction", Integer.class)))
                    .one();
        }

        String sql = """
            WITH current_dir_stop AS (
                SELECT rs.stop_sequence, rs.direction,
                    6371000 * acos(
                        cos(radians(:latitude)) * cos(radians(bs.latitude)) *
                        cos(radians(bs.longitude) - radians(:longitude)) +
                        sin(radians(:latitude)) * sin(radians(bs.latitude))
                    ) as distance
                FROM route_stops rs
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE rs.route_id = :routeId AND rs.direction = :direction
                ORDER BY distance
                LIMIT 1
            ),
            other_dir_stop AS (
                SELECT rs.stop_sequence, rs.direction,
                    6371000 * acos(
                        cos(radians(:latitude)) * cos(radians(bs.latitude)) *
                        cos(radians(bs.longitude) - radians(:longitude)) +
                        sin(radians(:latitude)) * sin(radians(bs.latitude))
                    ) as distance
                FROM route_stops rs
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE rs.route_id = :routeId AND rs.direction != :direction
                ORDER BY distance
                LIMIT 1
            )
            SELECT
                CASE
                    -- Switch to other direction if it's significantly closer (20m threshold)
                    WHEN o.distance < c.distance - 20
                    THEN o.stop_sequence
                    ELSE c.stop_sequence
                END as stop_sequence,
                CASE
                    WHEN o.distance < c.distance - 20
                    THEN o.direction
                    ELSE c.direction
                END as direction
            FROM current_dir_stop c, other_dir_stop o
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .bind("direction", currentDirection)
                .bind("latitude", latitude)
                .bind("longitude", longitude)
                .map(row -> new NearestStopResult(
                        row.get("stop_sequence", Integer.class),
                        row.get("direction", Integer.class)))
                .one();
    }

    @Override
    public Mono<Map<String, NearestStopResult>> findNearestStopSequencesBatch(
            List<VehiclePositionKey> vehiclePositions) {

        if (vehiclePositions == null || vehiclePositions.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return reactor.core.publisher.Flux.fromIterable(vehiclePositions)
                .flatMap(pos -> findNearestStopSequence(pos.routeId(), pos.latitude(), pos.longitude(), pos.currentDirection())
                        .map(result -> Map.entry(pos.vehicleId(), result))
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    public Mono<NearestStopResult> findDirectionByCourse(String routeId, Double latitude, Double longitude, Double course) {
        if (routeId == null || latitude == null || longitude == null || course == null) {
            return Mono.empty();
        }

        String sql = """
            WITH forward_stops AS (
                SELECT rs.stop_sequence, bs.latitude as stop_lat, bs.longitude as stop_lon,
                    6371000 * acos(
                        LEAST(1.0, GREATEST(-1.0,
                            cos(radians(:latitude)) * cos(radians(bs.latitude)) *
                            cos(radians(bs.longitude) - radians(:longitude)) +
                            sin(radians(:latitude)) * sin(radians(bs.latitude))
                        ))
                    ) as distance
                FROM route_stops rs
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE rs.route_id = :routeId AND rs.direction = 0
                ORDER BY distance
            ),
            backward_stops AS (
                SELECT rs.stop_sequence, bs.latitude as stop_lat, bs.longitude as stop_lon,
                    6371000 * acos(
                        LEAST(1.0, GREATEST(-1.0,
                            cos(radians(:latitude)) * cos(radians(bs.latitude)) *
                            cos(radians(bs.longitude) - radians(:longitude)) +
                            sin(radians(:latitude)) * sin(radians(bs.latitude))
                        ))
                    ) as distance
                FROM route_stops rs
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE rs.route_id = :routeId AND rs.direction = 1
                ORDER BY distance
            ),
            nearest_forward AS (
                SELECT stop_sequence FROM forward_stops LIMIT 1
            ),
            nearest_backward AS (
                SELECT stop_sequence FROM backward_stops LIMIT 1
            ),
            forward_next AS (
                SELECT fs.stop_sequence, fs.stop_lat, fs.stop_lon
                FROM forward_stops fs, nearest_forward nf
                WHERE fs.stop_sequence > nf.stop_sequence
                ORDER BY fs.stop_sequence
                LIMIT 1
            ),
            backward_next AS (
                SELECT bs.stop_sequence, bs.stop_lat, bs.stop_lon
                FROM backward_stops bs, nearest_backward nb
                WHERE bs.stop_sequence > nb.stop_sequence
                ORDER BY bs.stop_sequence
                LIMIT 1
            ),
            bearings AS (
                SELECT
                    (SELECT stop_sequence FROM nearest_forward) as forward_seq,
                    (SELECT stop_sequence FROM nearest_backward) as backward_seq,
                    -- Bearing to forward next stop (normalized to 0-360)
                    CASE WHEN fn.stop_lat IS NOT NULL THEN
                        CASE
                            WHEN degrees(atan2(
                                sin(radians(fn.stop_lon - :longitude)) * cos(radians(fn.stop_lat)),
                                cos(radians(:latitude)) * sin(radians(fn.stop_lat)) -
                                sin(radians(:latitude)) * cos(radians(fn.stop_lat)) * cos(radians(fn.stop_lon - :longitude))
                            )) < 0
                            THEN degrees(atan2(
                                sin(radians(fn.stop_lon - :longitude)) * cos(radians(fn.stop_lat)),
                                cos(radians(:latitude)) * sin(radians(fn.stop_lat)) -
                                sin(radians(:latitude)) * cos(radians(fn.stop_lat)) * cos(radians(fn.stop_lon - :longitude))
                            )) + 360
                            ELSE degrees(atan2(
                                sin(radians(fn.stop_lon - :longitude)) * cos(radians(fn.stop_lat)),
                                cos(radians(:latitude)) * sin(radians(fn.stop_lat)) -
                                sin(radians(:latitude)) * cos(radians(fn.stop_lat)) * cos(radians(fn.stop_lon - :longitude))
                            ))
                        END
                    ELSE NULL END as forward_bearing,
                    -- Bearing to backward next stop (normalized to 0-360)
                    CASE WHEN bn.stop_lat IS NOT NULL THEN
                        CASE
                            WHEN degrees(atan2(
                                sin(radians(bn.stop_lon - :longitude)) * cos(radians(bn.stop_lat)),
                                cos(radians(:latitude)) * sin(radians(bn.stop_lat)) -
                                sin(radians(:latitude)) * cos(radians(bn.stop_lat)) * cos(radians(bn.stop_lon - :longitude))
                            )) < 0
                            THEN degrees(atan2(
                                sin(radians(bn.stop_lon - :longitude)) * cos(radians(bn.stop_lat)),
                                cos(radians(:latitude)) * sin(radians(bn.stop_lat)) -
                                sin(radians(:latitude)) * cos(radians(bn.stop_lat)) * cos(radians(bn.stop_lon - :longitude))
                            )) + 360
                            ELSE degrees(atan2(
                                sin(radians(bn.stop_lon - :longitude)) * cos(radians(bn.stop_lat)),
                                cos(radians(:latitude)) * sin(radians(bn.stop_lat)) -
                                sin(radians(:latitude)) * cos(radians(bn.stop_lat)) * cos(radians(bn.stop_lon - :longitude))
                            ))
                        END
                    ELSE NULL END as backward_bearing
                FROM (SELECT 1) dummy
                LEFT JOIN forward_next fn ON true
                LEFT JOIN backward_next bn ON true
            )
            SELECT
                CASE
                    -- If only forward bearing available
                    WHEN forward_bearing IS NOT NULL AND backward_bearing IS NULL THEN forward_seq
                    -- If only backward bearing available
                    WHEN forward_bearing IS NULL AND backward_bearing IS NOT NULL THEN backward_seq
                    -- If both bearings available, compare angle differences
                    WHEN forward_bearing IS NOT NULL AND backward_bearing IS NOT NULL THEN
                        CASE
                            WHEN LEAST(
                                    ABS(:course - forward_bearing),
                                    360 - ABS(:course - forward_bearing)
                                ) <= LEAST(
                                    ABS(:course - backward_bearing),
                                    360 - ABS(:course - backward_bearing)
                                )
                            THEN forward_seq
                            ELSE backward_seq
                        END
                    -- Fallback: use nearest stop from any direction
                    ELSE COALESCE(forward_seq, backward_seq)
                END as stop_sequence,
                CASE
                    WHEN forward_bearing IS NOT NULL AND backward_bearing IS NULL THEN 0
                    WHEN forward_bearing IS NULL AND backward_bearing IS NOT NULL THEN 1
                    WHEN forward_bearing IS NOT NULL AND backward_bearing IS NOT NULL THEN
                        CASE
                            WHEN LEAST(
                                    ABS(:course - forward_bearing),
                                    360 - ABS(:course - forward_bearing)
                                ) <= LEAST(
                                    ABS(:course - backward_bearing),
                                    360 - ABS(:course - backward_bearing)
                                )
                            THEN 0
                            ELSE 1
                        END
                    ELSE CASE WHEN forward_seq IS NOT NULL THEN 0 ELSE 1 END
                END as direction
            FROM bearings
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .bind("latitude", latitude)
                .bind("longitude", longitude)
                .bind("course", course)
                .map(row -> new NearestStopResult(
                        row.get("stop_sequence", Integer.class),
                        row.get("direction", Integer.class)))
                .one();
    }

    @Override
    public Mono<Map<String, NearestStopResult>> findDirectionByCoursesBatch(List<VehiclePositionKey> vehiclePositions) {
        if (vehiclePositions == null || vehiclePositions.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return reactor.core.publisher.Flux.fromIterable(vehiclePositions)
                .filter(pos -> pos.course() != null && pos.course() > 0)
                .flatMap(pos -> findDirectionByCourse(pos.routeId(), pos.latitude(), pos.longitude(), pos.course())
                        .map(result -> Map.entry(pos.vehicleId(), result))
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
