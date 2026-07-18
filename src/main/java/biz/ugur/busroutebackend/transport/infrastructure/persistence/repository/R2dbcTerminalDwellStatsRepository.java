package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.TerminalDwellStatsRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.TerminalDwellStat;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class R2dbcTerminalDwellStatsRepository implements TerminalDwellStatsRepository {

    private static final String SELECT_COLUMNS = """
            route_number, direction, hour_of_day, is_weekend,
            avg_dwell_seconds, sample_count, last_observed_at
            """;

    private final DatabaseClient databaseClient;

    public R2dbcTerminalDwellStatsRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<TerminalDwellStat> findByKey(String routeNumber, int direction,
                                             int hourOfDay, boolean weekend) {
        String sql = "SELECT " + SELECT_COLUMNS + """
                FROM terminal_dwell_stats
                WHERE route_number = :routeNumber
                  AND direction = :direction
                  AND hour_of_day = :hourOfDay
                  AND is_weekend = :weekend
                """;
        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .bind("direction", direction)
                .bind("hourOfDay", hourOfDay)
                .bind("weekend", weekend)
                .map(this::mapRow)
                .one();
    }

    @Override
    public Mono<TerminalDwellStat> save(TerminalDwellStat stat) {
        String sql = """
                INSERT INTO terminal_dwell_stats (
                    route_number, direction, hour_of_day, is_weekend,
                    avg_dwell_seconds, sample_count, last_observed_at, updated_at
                ) VALUES (
                    :routeNumber, :direction, :hourOfDay, :weekend,
                    :avgDwell, :sampleCount, :lastObservedAt, CURRENT_TIMESTAMP
                )
                ON CONFLICT (route_number, direction, hour_of_day, is_weekend)
                DO UPDATE SET
                    avg_dwell_seconds = EXCLUDED.avg_dwell_seconds,
                    sample_count      = EXCLUDED.sample_count,
                    last_observed_at  = EXCLUDED.last_observed_at,
                    updated_at        = CURRENT_TIMESTAMP
                """;
        OffsetDateTime lastOdt = stat.getLastObservedAt() != null
                ? stat.getLastObservedAt().atOffset(ZoneOffset.UTC)
                : null;
        var spec = databaseClient.sql(sql)
                .bind("routeNumber", stat.getRouteNumber())
                .bind("direction", stat.getDirection())
                .bind("hourOfDay", stat.getHourOfDay())
                .bind("weekend", stat.isWeekend())
                .bind("avgDwell", stat.getAvgDwellSeconds())
                .bind("sampleCount", stat.getSampleCount());
        spec = lastOdt != null
                ? spec.bind("lastObservedAt", lastOdt)
                : spec.bindNull("lastObservedAt", OffsetDateTime.class);
        return spec.fetch().rowsUpdated().thenReturn(stat);
    }

    private TerminalDwellStat mapRow(Readable row) {
        OffsetDateTime last = row.get("last_observed_at", OffsetDateTime.class);
        return TerminalDwellStat.builder()
                .routeNumber(row.get("route_number", String.class))
                .direction(row.get("direction", Integer.class))
                .hourOfDay(row.get("hour_of_day", Integer.class))
                .weekend(Boolean.TRUE.equals(row.get("is_weekend", Boolean.class)))
                .avgDwellSeconds(row.get("avg_dwell_seconds", Double.class))
                .sampleCount(row.get("sample_count", Long.class))
                .lastObservedAt(last != null ? last.toInstant() : null)
                .build();
    }
}
