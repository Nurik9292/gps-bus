package biz.ugur.busroutebackend.routing.infrastructure.repository;

import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.repository.TripPlanRepository;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
public class R2dbcTripPlanRepository extends BaseR2dbcRepository<TripPlan, TripPlanId>
        implements TripPlanRepository {

    public R2dbcTripPlanRepository(DatabaseClient databaseClient) {
        super(databaseClient, "trip_plans", TripPlan.class);
    }

    @Override
    public Mono<TripPlan> save(TripPlan tripPlan) {
        String sql = """
            INSERT INTO trip_plans (
                id, origin_latitude, origin_longitude, destination_latitude, destination_longitude, 
                search_time, options_count, max_transfers, max_walking_distance_meters,
                created_at, updated_at, version)
            VALUES (
                :id, :originLat, :originLon, :destLat, :destLon,
                :searchTime, :optionsCount, :maxTransfers, :maxWalkingDistance, 
                :created_at, :updated_at, :version)
            ON CONFLICT (id) DO UPDATE SET
                options_count = :optionsCount,
                updated_at = CURRENT_TIMESTAMP,
                version = trip_plans.version + 1
            RETURNING *
            """;

        return databaseClient.sql(sql)
                .bind("id", tripPlan.getId().getValue())
                .bind("originLat", tripPlan.getOriginLatitude())
                .bind("originLon", tripPlan.getOriginLongitude())
                .bind("destLat", tripPlan.getDestinationLatitude())
                .bind("destLon", tripPlan.getDestinationLongitude())
                .bind("searchTime", tripPlan.getSearchTimeDb())
                .bind("optionsCount", tripPlan.getOptionsCount())
                .bind("maxTransfers", tripPlan.getMaxTransfers())
                .bind("maxWalkingDistance", tripPlan.getMaxWalkingDistanceMeters())
                .bind("created_at", tripPlan.getCreatedAt())
                .bind("updated_at", tripPlan.getUpdatedAt())
                .bind("version", tripPlan.getVersion())
                .map(getRowMapper())
                .one()
                .doOnSuccess(plan -> log.debug("Saved trip plan: {}", plan.getId().getValue()))
                .doOnError(error -> log.error("Failed to save trip plan: {}", error.getMessage()));
    }

    @Override
    public Flux<TripPlan> findRecentPlans(int limit) {
        String sql = String.format(
                "SELECT * FROM %s ORDER BY created_at DESC LIMIT :limit",
                tableName);

        return databaseClient.sql(sql)
                .bind("limit", limit)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<TripPlan> findPlansByTimeRange(LocalDateTime from, LocalDateTime to) {
        String sql = String.format(
                "SELECT * FROM %s WHERE created_at BETWEEN :from AND :to ORDER BY created_at DESC",
                tableName);

        return databaseClient.sql(sql)
                .bind("from", from)
                .bind("to", to)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<TripPlanningStatistics> getTripPlanningStatistics(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT 
                DATE(created_at) as search_date,
                COUNT(*) as total_searches,
                COUNT(*) FILTER (WHERE options_count > 0) as successful_searches,
                AVG(options_count) as avg_options_found
            FROM trip_plans 
            WHERE created_at BETWEEN :from AND :to
            GROUP BY DATE(created_at)
            ORDER BY search_date DESC
            """;

        return databaseClient.sql(sql)
                .bind("from", from)
                .bind("to", to)
                .map(row -> new TripPlanningStatistics(
                        row.get("search_date", LocalDateTime.class),
                        row.get("total_searches", Long.class),
                        row.get("successful_searches", Long.class),
                        row.get("avg_options_found", Double.class),
                        0.0, // averageTravelTime - нужна дополнительная логика
                        "Unknown" // mostPopularRoute - нужна дополнительная логика
                ))
                .all();
    }

    @Override
    protected String convertIdToDatabase(TripPlanId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, TripPlan> getRowMapper() {
        return (row, metadata) -> new TripPlan(
                TripPlanId.of(row.get("id", String.class)),
                row.get("origin_latitude", Double.class),
                row.get("origin_longitude", Double.class),
                row.get("destination_latitude", Double.class),
                row.get("destination_longitude", Double.class),
                row.get("search_time", LocalDateTime.class),
                row.get("options_count", Integer.class),
                row.get("max_transfers", Integer.class),
                row.get("max_walking_distance_meters", Integer.class)
        );
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(TripPlan entity) {
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId().getValue());
        columns.put("origin_latitude", entity.getOriginLatitude());
        columns.put("origin_longitude", entity.getOriginLongitude());
        columns.put("destination_latitude", entity.getDestinationLatitude());
        columns.put("destination_longitude", entity.getDestinationLongitude());
        columns.put("search_time", entity.getSearchTimeDb());
        columns.put("options_count", entity.getOptionsCount());
        columns.put("max_transfers", entity.getMaxTransfers());
        columns.put("max_walking_distance_meters", entity.getMaxWalkingDistanceMeters());
        return columns;
    }
}