package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.repository.StopConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
@Slf4j
public class R2dbcStopConnectionRepository implements StopConnectionRepository {

    private final DatabaseClient databaseClient;

    public R2dbcStopConnectionRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<StopConnection> findAllConnections() {
        String sql = """
            SELECT
                rs1.stop_id as from_stop_id,
                rs2.stop_id as to_stop_id,
                rs1.route_id,
                br.route_number,
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as estimated_travel_minutes,
                ABS(rs2.stop_sequence - rs1.stop_sequence) as stop_sequence_difference
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            JOIN bus_routes br ON rs1.route_id = br.id
            WHERE rs1.stop_id != rs2.stop_id
            AND br.is_active = true
            ORDER BY estimated_travel_minutes
            LIMIT 10000
            """;

        log.debug("Loading stop connections with LIMIT 10000 to prevent OOM");

        return databaseClient.sql(sql)
                .map(row -> new StopConnection(
                        row.get("from_stop_id", String.class),
                        row.get("to_stop_id", String.class),
                        row.get("route_id", String.class),
                        row.get("route_number", String.class),
                        row.get("estimated_travel_minutes", Integer.class),
                        row.get("stop_sequence_difference", Integer.class)
                ))
                .all();
    }

    @Override
    public Flux<StopConnection> findConnectionsFromStop(String stopId) {
        String sql = """
            SELECT
                rs1.stop_id as from_stop_id,
                rs2.stop_id as to_stop_id,
                rs1.route_id,
                br.route_number,
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as estimated_travel_minutes,
                ABS(rs2.stop_sequence - rs1.stop_sequence) as stop_sequence_difference
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            JOIN bus_routes br ON rs1.route_id = br.id
            WHERE rs1.stop_id = :stopId
            AND rs1.stop_id != rs2.stop_id
            AND br.is_active = true
            ORDER BY estimated_travel_minutes
            LIMIT 1000
            """;

        return databaseClient.sql(sql)
                .bind("stopId", stopId)
                .map(row -> new StopConnection(
                        row.get("from_stop_id", String.class),
                        row.get("to_stop_id", String.class),
                        row.get("route_id", String.class),
                        row.get("route_number", String.class),
                        row.get("estimated_travel_minutes", Integer.class),
                        row.get("stop_sequence_difference", Integer.class)
                ))
                .all();
    }

    @Override
    public Mono<Boolean> areStopsConnected(String fromStopId, String toStopId) {
        String sql = """
            SELECT COUNT(*) > 0 as connected
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            WHERE rs1.stop_id = :fromStopId AND rs2.stop_id = :toStopId
            """;

        return databaseClient.sql(sql)
                .bind("fromStopId", fromStopId)
                .bind("toStopId", toStopId)
                .map(row -> row.get("connected", Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<ShortestPath> findShortestPath(String fromStopId, String toStopId, int maxTransfers) {
        log.debug("Finding shortest path from {} to {} with max {} transfers", fromStopId, toStopId, maxTransfers);

        return Mono.empty();
    }
}
