package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.repository.RoutingAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
@Slf4j
public class R2dbcRoutingAnalyticsRepository implements RoutingAnalyticsRepository {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<OverviewStats> getOverviewStats(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql("""
                SELECT
                    COUNT(*)::BIGINT AS total_searches,
                    COUNT(CASE WHEN EXISTS (
                        SELECT 1 FROM trip_options topt WHERE topt.trip_plan_id = tp.id
                    ) THEN 1 END)::BIGINT AS successful_searches,
                    COUNT(CASE WHEN NOT EXISTS (
                        SELECT 1 FROM trip_options topt WHERE topt.trip_plan_id = tp.id
                    ) THEN 1 END)::BIGINT AS failed_searches,
                    COALESCE(AVG(
                        (SELECT COUNT(*) FROM trip_options topt WHERE topt.trip_plan_id = tp.id)
                    ), 0)::DOUBLE PRECISION AS avg_options,
                    COALESCE(AVG(
                        (SELECT AVG(topt.total_duration_minutes) FROM trip_options topt WHERE topt.trip_plan_id = tp.id)
                    ), 0)::DOUBLE PRECISION AS avg_duration
                FROM trip_plans tp
                WHERE tp.created_at >= :from AND tp.created_at < :to
                """)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> new OverviewStats(
                        row.get("total_searches", Long.class),
                        row.get("successful_searches", Long.class),
                        row.get("failed_searches", Long.class),
                        row.get("avg_options", Double.class),
                        row.get("avg_duration", Double.class)
                ))
                .one()
                .defaultIfEmpty(new OverviewStats(0, 0, 0, 0.0, 0.0));
    }

    @Override
    public Flux<HourlySearchStats> getHourlySearchStats(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql("""
                SELECT
                    DATE_TRUNC('hour', tp.created_at) AS hour,
                    COUNT(*)::INTEGER AS total_searches,
                    COUNT(CASE WHEN EXISTS (
                        SELECT 1 FROM trip_options topt WHERE topt.trip_plan_id = tp.id
                    ) THEN 1 END)::INTEGER AS successful_searches,
                    COUNT(CASE WHEN NOT EXISTS (
                        SELECT 1 FROM trip_options topt WHERE topt.trip_plan_id = tp.id
                    ) THEN 1 END)::INTEGER AS failed_searches,
                    COALESCE(AVG(
                        (SELECT COUNT(*) FROM trip_options topt WHERE topt.trip_plan_id = tp.id)
                    ), 0)::DOUBLE PRECISION AS avg_options
                FROM trip_plans tp
                WHERE tp.created_at >= :from AND tp.created_at < :to
                GROUP BY DATE_TRUNC('hour', tp.created_at)
                ORDER BY hour
                """)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> new HourlySearchStats(
                        row.get("hour", LocalDateTime.class),
                        row.get("total_searches", Integer.class),
                        row.get("successful_searches", Integer.class),
                        row.get("failed_searches", Integer.class),
                        row.get("avg_options", Double.class)
                ))
                .all();
    }

    @Override
    public Flux<HeatmapPoint> getHeatmapData(String pointType) {
        return databaseClient.sql("""
                SELECT lat, lon, search_count, point_type
                FROM mv_search_heatmap
                WHERE point_type = :pointType
                ORDER BY search_count DESC
                """)
                .bind("pointType", pointType)
                .map((row, metadata) -> new HeatmapPoint(
                        row.get("lat", Double.class),
                        row.get("lon", Double.class),
                        row.get("search_count", Integer.class),
                        row.get("point_type", String.class)
                ))
                .all();
    }

    @Override
    public Flux<ODPair> getPopularODPairs(int limit) {
        return databaseClient.sql("""
                SELECT from_lat, from_lon, to_lat, to_lon, trip_count, avg_options
                FROM mv_popular_od_pairs
                ORDER BY trip_count DESC
                LIMIT :limit
                """)
                .bind("limit", limit)
                .map((row, metadata) -> new ODPair(
                        row.get("from_lat", Double.class),
                        row.get("from_lon", Double.class),
                        row.get("to_lat", Double.class),
                        row.get("to_lon", Double.class),
                        row.get("trip_count", Integer.class),
                        row.get("avg_options", Double.class)
                ))
                .all();
    }

    @Override
    public Flux<RoutePopularity> getRoutePopularity(int limit, LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql("""
                SELECT
                    br.id AS route_id,
                    br.route_number,
                    br.route_name,
                    COUNT(ts.id)::BIGINT AS usage_count
                FROM trip_segments ts
                JOIN trip_options topt ON ts.trip_option_id = topt.id
                JOIN trip_plans tp ON topt.trip_plan_id = tp.id
                JOIN bus_routes br ON ts.route_id = br.id
                WHERE ts.segment_type = 'BUS_RIDE'
                  AND tp.created_at >= :from AND tp.created_at < :to
                GROUP BY br.id, br.route_number, br.route_name
                ORDER BY usage_count DESC
                LIMIT :limit
                """)
                .bind("from", from)
                .bind("to", to)
                .bind("limit", limit)
                .map((row, metadata) -> new RoutePopularity(
                        row.get("route_id", String.class),
                        row.get("route_number", String.class),
                        row.get("route_name", String.class),
                        row.get("usage_count", Long.class)
                ))
                .all();
    }

    @Override
    public Flux<TransferDistribution> getTransferDistribution(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql("""
                SELECT
                    topt.number_of_transfers,
                    COUNT(*)::BIGINT AS count
                FROM trip_options topt
                JOIN trip_plans tp ON topt.trip_plan_id = tp.id
                WHERE tp.created_at >= :from AND tp.created_at < :to
                GROUP BY topt.number_of_transfers
                ORDER BY topt.number_of_transfers
                """)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> new TransferDistribution(
                        row.get("number_of_transfers", Integer.class),
                        row.get("count", Long.class)
                ))
                .all();
    }

    @Override
    public Flux<DailySearchStats> getDailySearchStats(LocalDateTime from, LocalDateTime to) {
        return databaseClient.sql("""
                SELECT
                    DATE_TRUNC('day', tp.created_at)::TIMESTAMP AS date,
                    COUNT(*)::INTEGER AS total_searches,
                    COUNT(CASE WHEN EXISTS (
                        SELECT 1 FROM trip_options topt WHERE topt.trip_plan_id = tp.id
                    ) THEN 1 END)::INTEGER AS successful_searches,
                    COUNT(CASE WHEN NOT EXISTS (
                        SELECT 1 FROM trip_options topt WHERE topt.trip_plan_id = tp.id
                    ) THEN 1 END)::INTEGER AS failed_searches
                FROM trip_plans tp
                WHERE tp.created_at >= :from AND tp.created_at < :to
                GROUP BY DATE_TRUNC('day', tp.created_at)
                ORDER BY date
                """)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> new DailySearchStats(
                        row.get("date", LocalDateTime.class),
                        row.get("total_searches", Integer.class),
                        row.get("successful_searches", Integer.class),
                        row.get("failed_searches", Integer.class)
                ))
                .all();
    }
}
