package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.SegmentTravelStatsRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
@Slf4j
public class R2dbcSegmentTravelStatsRepository implements SegmentTravelStatsRepository {

    private static final String SELECT_COLUMNS = """
            route_number, direction, from_stop_id, to_stop_id,
            hour_of_day, is_weekend,
            avg_travel_seconds, sample_count, last_observed_at
            """;

    private final DatabaseClient databaseClient;

    public R2dbcSegmentTravelStatsRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<SegmentTravelStat> findByKey(String routeNumber, int direction,
                                             String fromStopId, String toStopId,
                                             int hourOfDay, boolean weekend) {
        String sql = "SELECT " + SELECT_COLUMNS + """
                FROM segment_travel_stats
                WHERE route_number = :routeNumber
                  AND direction = :direction
                  AND from_stop_id = :fromStopId
                  AND to_stop_id = :toStopId
                  AND hour_of_day = :hourOfDay
                  AND is_weekend = :weekend
                """;
        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .bind("direction", direction)
                .bind("fromStopId", fromStopId)
                .bind("toStopId", toStopId)
                .bind("hourOfDay", hourOfDay)
                .bind("weekend", weekend)
                .map(this::mapRow)
                .one();
    }

    @Override
    public Flux<SegmentTravelStat> findAll() {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM segment_travel_stats";
        return databaseClient.sql(sql).map(this::mapRow).all();
    }

    @Override
    public Mono<SegmentTravelStat> save(SegmentTravelStat stat) {
        String sql = """
                INSERT INTO segment_travel_stats (
                    route_number, direction, from_stop_id, to_stop_id,
                    hour_of_day, is_weekend,
                    avg_travel_seconds, sample_count, last_observed_at, updated_at
                ) VALUES (
                    :routeNumber, :direction, :fromStopId, :toStopId,
                    :hourOfDay, :weekend,
                    :avgTravel, :sampleCount, :lastObservedAt, CURRENT_TIMESTAMP
                )
                ON CONFLICT (route_number, direction, from_stop_id, to_stop_id, hour_of_day, is_weekend)
                DO UPDATE SET
                    avg_travel_seconds = EXCLUDED.avg_travel_seconds,
                    sample_count       = EXCLUDED.sample_count,
                    last_observed_at   = EXCLUDED.last_observed_at,
                    updated_at         = CURRENT_TIMESTAMP
                """;

        OffsetDateTime lastOdt = stat.getLastObservedAt() != null
                ? stat.getLastObservedAt().atOffset(ZoneOffset.UTC)
                : null;

        var spec = databaseClient.sql(sql)
                .bind("routeNumber", stat.getRouteNumber())
                .bind("direction", stat.getDirection())
                .bind("fromStopId", stat.getFromStopId())
                .bind("toStopId", stat.getToStopId())
                .bind("hourOfDay", stat.getHourOfDay())
                .bind("weekend", stat.isWeekend())
                .bind("avgTravel", stat.getAvgTravelSeconds())
                .bind("sampleCount", stat.getSampleCount());

        spec = lastOdt != null
                ? spec.bind("lastObservedAt", lastOdt)
                : spec.bindNull("lastObservedAt", OffsetDateTime.class);

        return spec.fetch().rowsUpdated().thenReturn(stat);
    }

    private SegmentTravelStat mapRow(Row row, RowMetadata meta) {
        OffsetDateTime lastObserved = row.get("last_observed_at", OffsetDateTime.class);
        return SegmentTravelStat.builder()
                .routeNumber(row.get("route_number", String.class))
                .direction(row.get("direction", Integer.class))
                .fromStopId(row.get("from_stop_id", String.class))
                .toStopId(row.get("to_stop_id", String.class))
                .hourOfDay(row.get("hour_of_day", Integer.class))
                .weekend(Boolean.TRUE.equals(row.get("is_weekend", Boolean.class)))
                .avgTravelSeconds(row.get("avg_travel_seconds", Double.class))
                .sampleCount(row.get("sample_count", Long.class))
                .lastObservedAt(lastObserved != null ? lastObserved.toInstant() : null)
                .build();
    }
}
