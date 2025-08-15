package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopDetail;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopsData;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopsStatistics;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
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
}
