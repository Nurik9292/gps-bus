package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.StopDwellStatsRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopDwellStat;
import io.r2dbc.spi.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
@Slf4j
public class R2dbcStopDwellStatsRepository implements StopDwellStatsRepository {

    private final DatabaseClient databaseClient;

    public R2dbcStopDwellStatsRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<StopDwellStat> findByStopRouteDirection(String stopId, String routeId, int direction) {
        String sql = """
            SELECT stop_id, route_id, route_number, direction, avg_dwell_seconds,
                   min_dwell_seconds, max_dwell_seconds, sample_count, last_dwell_at
            FROM stop_dwell_stats
            WHERE stop_id = :stopId AND route_id = :routeId AND direction = :direction
            """;
        return databaseClient.sql(sql)
                .bind("stopId", stopId)
                .bind("routeId", routeId)
                .bind("direction", direction)
                .map(this::mapRow)
                .one();
    }

    @Override
    public Flux<StopDwellStat> findByRouteAndDirection(String routeId, int direction) {
        String sql = """
            SELECT stop_id, route_id, route_number, direction, avg_dwell_seconds,
                   min_dwell_seconds, max_dwell_seconds, sample_count, last_dwell_at
            FROM stop_dwell_stats
            WHERE route_id = :routeId AND direction = :direction
            """;
        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .bind("direction", direction)
                .map(this::mapRow)
                .all();
    }

    @Override
    public Flux<StopDwellStat> findAll() {
        String sql = """
            SELECT stop_id, route_id, route_number, direction, avg_dwell_seconds,
                   min_dwell_seconds, max_dwell_seconds, sample_count, last_dwell_at
            FROM stop_dwell_stats
            """;
        return databaseClient.sql(sql).map(this::mapRow).all();
    }

    @Override
    public Mono<StopDwellStat> save(StopDwellStat stat) {
        String sql = """
            INSERT INTO stop_dwell_stats (
                stop_id, route_id, route_number, direction,
                avg_dwell_seconds, min_dwell_seconds, max_dwell_seconds,
                sample_count, last_dwell_at, updated_at
            ) VALUES (
                :stopId, :routeId, :routeNumber, :direction,
                :avgDwell, :minDwell, :maxDwell,
                :sampleCount, :lastDwellAt, CURRENT_TIMESTAMP
            )
            ON CONFLICT (stop_id, route_id, direction) DO UPDATE SET
                avg_dwell_seconds = EXCLUDED.avg_dwell_seconds,
                min_dwell_seconds = EXCLUDED.min_dwell_seconds,
                max_dwell_seconds = EXCLUDED.max_dwell_seconds,
                sample_count = EXCLUDED.sample_count,
                last_dwell_at = EXCLUDED.last_dwell_at,
                updated_at = CURRENT_TIMESTAMP
            """;

        OffsetDateTime lastDwellOdt = stat.getLastDwellAt() != null
                ? stat.getLastDwellAt().atOffset(ZoneOffset.UTC)
                : null;

        var spec = databaseClient.sql(sql)
                .bind("stopId", stat.getStopId())
                .bind("routeId", stat.getRouteId())
                .bind("routeNumber", stat.getRouteNumber())
                .bind("direction", stat.getDirection())
                .bind("avgDwell", stat.getAvgDwellSeconds())
                .bind("sampleCount", stat.getSampleCount());

        spec = stat.getMinDwellSeconds() != null
                ? spec.bind("minDwell", stat.getMinDwellSeconds())
                : spec.bindNull("minDwell", Double.class);
        spec = stat.getMaxDwellSeconds() != null
                ? spec.bind("maxDwell", stat.getMaxDwellSeconds())
                : spec.bindNull("maxDwell", Double.class);
        spec = lastDwellOdt != null
                ? spec.bind("lastDwellAt", lastDwellOdt)
                : spec.bindNull("lastDwellAt", OffsetDateTime.class);

        return spec.fetch().rowsUpdated().thenReturn(stat);
    }

    private StopDwellStat mapRow(Row row, io.r2dbc.spi.RowMetadata meta) {
        OffsetDateTime lastDwell = row.get("last_dwell_at", OffsetDateTime.class);
        Double minD = row.get("min_dwell_seconds", Double.class);
        Double maxD = row.get("max_dwell_seconds", Double.class);
        return StopDwellStat.builder()
                .stopId(row.get("stop_id", String.class))
                .routeId(row.get("route_id", String.class))
                .routeNumber(row.get("route_number", String.class))
                .direction(row.get("direction", Integer.class))
                .avgDwellSeconds(row.get("avg_dwell_seconds", Double.class))
                .minDwellSeconds(minD)
                .maxDwellSeconds(maxD)
                .sampleCount(row.get("sample_count", Long.class))
                .lastDwellAt(lastDwell != null ? lastDwell.toInstant() : null)
                .build();
    }
}
