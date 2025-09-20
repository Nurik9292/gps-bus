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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        // ✅ РЕШЕНИЕ: Сначала проверяем существует ли запись
        return findById(tripPlan.getId())
                .flatMap(existing -> {
                    // Запись уже существует - обновляем её
                    log.info("🔄 TripPlan {} already exists, updating...", tripPlan.getId().getValue());
                    return updateExisting(tripPlan);
                })
                .switchIfEmpty(
                        // Записи не существует - создаем новую
                        insertNew(tripPlan)
                )
                .doOnSuccess(plan -> log.info("✅ Successfully saved trip plan: {}", plan.getId().getValue()))
                .doOnError(error -> log.error("❌ Failed to save trip plan {}: {}",
                        tripPlan.getId().getValue(), error.getMessage()));
    }


    private Mono<TripPlan> insertNew(TripPlan tripPlan) {
        String sql = """
        INSERT INTO trip_plans (
            id,
            origin_latitude,
            origin_longitude,
            destination_latitude,
            destination_longitude,
            search_time,
            options_count,
            max_transfers,
            max_walking_distance_meters,
            created_at,
            updated_at,
            version
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING *
        """;

        log.info("➕ Inserting new TripPlan: {}", tripPlan.getId().getValue());

        return databaseClient.sql(sql)
                .bind(0, tripPlan.getId().getValue())
                .bind(1, tripPlan.getOriginLatitude() != null ? tripPlan.getOriginLatitude() : 0.0)
                .bind(2, tripPlan.getOriginLongitude() != null ? tripPlan.getOriginLongitude() : 0.0)
                .bind(3, tripPlan.getDestinationLatitude() != null ? tripPlan.getDestinationLatitude() : 0.0)
                .bind(4, tripPlan.getDestinationLongitude() != null ? tripPlan.getDestinationLongitude() : 0.0)
                .bind(5, tripPlan.getSearchTimeDb() != null ? tripPlan.getSearchTimeDb() : LocalDateTime.now())
                .bind(6, tripPlan.getOptionsCount() != null ? tripPlan.getOptionsCount() : 0)
                .bind(7, tripPlan.getMaxTransfers() != null ? tripPlan.getMaxTransfers() : 2)
                .bind(8, tripPlan.getMaxWalkingDistanceMeters() != null ? tripPlan.getMaxWalkingDistanceMeters() : 800)
                .bind(9, convertToOffsetDateTime(tripPlan.getCreatedAt()))
                .bind(10, OffsetDateTime.now())
                .bind(11, 0L)
                .map(getRowMapper())
                .one()
                .onErrorResume(error -> {
                    if (error.getMessage() != null && error.getMessage().contains("duplicate key")) {
                        log.warn("⚠️ Duplicate key during insert, trying to find existing record: {}",
                                tripPlan.getId().getValue());
                        return findById(tripPlan.getId())
                                .switchIfEmpty(Mono.error(new RuntimeException("Record disappeared after duplicate key error")));
                    }
                    return Mono.error(error);
                });
    }


    private Mono<TripPlan> updateExisting(TripPlan tripPlan) {
        String sql = """
        UPDATE trip_plans SET
            options_count = ?,
            updated_at = ?,
            version = version + 1
        WHERE id = ?
        RETURNING *
        """;

        log.info("🔄 Updating existing TripPlan: {}", tripPlan.getId().getValue());

        return databaseClient.sql(sql)
                .bind(0, tripPlan.getOptionsCount() != null ? tripPlan.getOptionsCount() : 0)
                .bind(1, OffsetDateTime.now())
                .bind(2, tripPlan.getId().getValue())
                .map(getRowMapper())
                .one()
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to update TripPlan: " + tripPlan.getId().getValue())));
    }


    @Override
    public Mono<TripPlan> findById(TripPlanId id) {
        String sql = "SELECT * FROM trip_plans WHERE id = ?";

        return databaseClient.sql(sql)
                .bind(0, id.getValue())
                .map(getRowMapper())
                .all()
                .take(1)
                .singleOrEmpty()
                .doOnNext(plan -> log.debug("🔍 Found existing TripPlan: {}", id.getValue()));
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
        return (row, metadata) -> {
            TripPlan tripPlan = new TripPlan(
                    TripPlanId.of(row.get("id", String.class)),
                    row.get("origin_latitude", Double.class),
                    row.get("origin_longitude", Double.class),
                    row.get("destination_latitude", Double.class),
                    row.get("destination_longitude", Double.class),
                    row.get("search_time", LocalDateTime.class),
                    row.get("options_count", Integer.class),
                    row.get("max_transfers", Integer.class),
                    row.get("max_walking_distance_meters", Integer.class));

            tripPlan.setCreatedAt(row.get("created_at", Instant.class));
            tripPlan.setUpdatedAt(row.get("updated_at", Instant.class));
            tripPlan.setVersion(row.get("version", Long.class));

            return tripPlan;
        };
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
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        columns.put("version", entity.getVersion());
        return columns;
    }

    private OffsetDateTime convertToOffsetDateTime(Instant instant) {
        if (instant == null) {
            return OffsetDateTime.now();
        }
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}