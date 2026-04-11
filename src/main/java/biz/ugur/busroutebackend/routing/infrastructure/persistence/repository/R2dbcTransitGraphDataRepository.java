package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.repository.TransitGraphDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
@Slf4j
@RequiredArgsConstructor
public class R2dbcTransitGraphDataRepository implements TransitGraphDataRepository {

    private final DatabaseClient db;

    @Override
    public Flux<BusEdgeRecord> findAllConsecutiveStopEdges() {
        String sql = """
                SELECT
                    rs1.stop_id::text  AS from_stop_id,
                    rs2.stop_id::text  AS to_stop_id,
                    rs1.route_id::text AS route_id,
                    br.route_number,
                    GREATEST(2, COALESCE(
                        CASE
                            WHEN br.estimated_duration_minutes IS NOT NULL
                                 AND total_stops.cnt > 1
                            THEN ROUND(br.estimated_duration_minutes::float / (total_stops.cnt - 1))
                            ELSE 2
                        END, 2
                    ))::int AS weight_minutes
                FROM route_stops rs1
                JOIN route_stops rs2
                    ON rs1.route_id = rs2.route_id
                   AND rs1.direction  = rs2.direction
                   AND rs2.stop_sequence = rs1.stop_sequence + 1
                JOIN bus_routes br ON rs1.route_id = br.id
                JOIN (
                    SELECT route_id, direction, COUNT(*) AS cnt
                    FROM route_stops
                    GROUP BY route_id, direction
                ) total_stops
                    ON rs1.route_id = total_stops.route_id
                   AND rs1.direction = total_stops.direction
                WHERE br.is_active = true
                """;

        return db.sql(sql)
                .map(row -> new BusEdgeRecord(
                        row.get("from_stop_id", String.class),
                        row.get("to_stop_id", String.class),
                        row.get("route_id", String.class),
                        row.get("route_number", String.class),
                        row.get("weight_minutes", Integer.class)
                ))
                .all()
                .doOnError(e -> log.error("Failed to load bus edges for graph: {}", e.getMessage()));
    }

    @Override
    public Flux<WalkingEdgeRecord> findAllWalkingEdges(double maxWalkingMeters) {
        String sql = """
                SELECT
                    bs1.id::text AS stop_id_1,
                    bs2.id::text AS stop_id_2,
                    GREATEST(1, ROUND(
                        ST_Distance(
                            ST_MakePoint(bs1.longitude::float, bs1.latitude::float)::geography,
                            ST_MakePoint(bs2.longitude::float, bs2.latitude::float)::geography
                        ) / (5000.0 / 60.0)
                    ))::int AS walking_minutes
                FROM bus_stops bs1
                JOIN bus_stops bs2 ON bs1.id < bs2.id
                WHERE bs1.is_active = true
                  AND bs2.is_active = true
                  AND ST_DWithin(
                        ST_SetSRID(ST_MakePoint(bs1.longitude::float, bs1.latitude::float), 4326)::geography,
                        ST_SetSRID(ST_MakePoint(bs2.longitude::float, bs2.latitude::float), 4326)::geography,
                        :maxMeters
                  )
                """;

        return db.sql(sql)
                .bind("maxMeters", maxWalkingMeters)
                .map(row -> new WalkingEdgeRecord(
                        row.get("stop_id_1", String.class),
                        row.get("stop_id_2", String.class),
                        row.get("walking_minutes", Integer.class)
                ))
                .all()
                .doOnError(e -> log.error("Failed to load walking edges for graph: {}", e.getMessage()));
    }
}
