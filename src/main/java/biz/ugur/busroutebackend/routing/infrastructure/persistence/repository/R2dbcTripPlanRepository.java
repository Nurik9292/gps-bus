package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.repository.TripPlanRepository;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import biz.ugur.busroutebackend.routing.infrastructure.persistence.entity.TripPlanEntity;
import biz.ugur.busroutebackend.routing.infrastructure.persistence.mapper.TripPlanMapper;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    private final TripPlanMapper mapper;

    public R2dbcTripPlanRepository(DatabaseClient databaseClient, TripPlanMapper mapper) {
        super(databaseClient, "trip_plans", TripPlan.class);
        this.mapper = mapper;
    }

    @Override
    public Mono<TripPlan> save(TripPlan tripPlan) {
        return findById(tripPlan.getId())
                .flatMap(existing -> {
                    log.info("🔄 TripPlan {} already exists, updating...", tripPlan.getId().getValue());
                    return updateExisting(tripPlan);
                })
                .switchIfEmpty(insertNew(tripPlan))
                .doOnSuccess(plan -> log.info("✅ Successfully saved trip plan: {}", plan.getId().getValue()))
                .doOnError(error -> log.error("❌ Failed to save trip plan {}: {}",
                        tripPlan.getId().getValue(), error.getMessage()));
    }

    private Mono<TripPlan> insertNew(TripPlan tripPlan) {
        TripPlanEntity entity = mapper.toEntity(tripPlan);

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
                .bind(0, entity.getId())
                .bind(1, entity.getOriginLatitude())
                .bind(2, entity.getOriginLongitude())
                .bind(3, entity.getDestinationLatitude())
                .bind(4, entity.getDestinationLongitude())
                .bind(5, entity.getSearchTime() != null ? entity.getSearchTime() : LocalDateTime.now())
                .bind(6, entity.getOptionsCount() != null ? entity.getOptionsCount() : 0)
                .bind(7, entity.getMaxTransfers() != null ? entity.getMaxTransfers() : 2)
                .bind(8, entity.getMaxWalkingDistanceMeters() != null ? entity.getMaxWalkingDistanceMeters() : 800)
                .bind(9, convertToOffsetDateTime(entity.getCreatedAt()))
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
                .bind(0, tripPlan.getTripOptionsCount())
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
                        0.0, // averageTravelTime - requires additional logic
                        "Unknown" // mostPopularRoute - requires additional logic
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
            TripPlanEntity entity = TripPlanEntity.builder()
                    .id(row.get("id", String.class))
                    .originLatitude(row.get("origin_latitude", Double.class))
                    .originLongitude(row.get("origin_longitude", Double.class))
                    .destinationLatitude(row.get("destination_latitude", Double.class))
                    .destinationLongitude(row.get("destination_longitude", Double.class))
                    .searchTime(row.get("search_time", LocalDateTime.class))
                    .optionsCount(row.get("options_count", Integer.class))
                    .maxTransfers(row.get("max_transfers", Integer.class))
                    .maxWalkingDistanceMeters(row.get("max_walking_distance_meters", Integer.class))
                    .createdAt(row.get("created_at", LocalDateTime.class))
                    .updatedAt(row.get("updated_at", LocalDateTime.class))
                    .version(row.get("version", Long.class))
                    .build();

            // Convert to domain model using mapper
            return mapper.toDomain(entity);
        };
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(TripPlan entity) {
        TripPlanEntity persistenceEntity = mapper.toEntity(entity);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", persistenceEntity.getId());
        columns.put("origin_latitude", persistenceEntity.getOriginLatitude());
        columns.put("origin_longitude", persistenceEntity.getOriginLongitude());
        columns.put("destination_latitude", persistenceEntity.getDestinationLatitude());
        columns.put("destination_longitude", persistenceEntity.getDestinationLongitude());
        columns.put("search_time", persistenceEntity.getSearchTime());
        columns.put("options_count", persistenceEntity.getOptionsCount());
        columns.put("max_transfers", persistenceEntity.getMaxTransfers());
        columns.put("max_walking_distance_meters", persistenceEntity.getMaxWalkingDistanceMeters());
        columns.put("created_at", persistenceEntity.getCreatedAt());
        columns.put("updated_at", persistenceEntity.getUpdatedAt());
        columns.put("version", persistenceEntity.getVersion());
        return columns;
    }

    private OffsetDateTime convertToOffsetDateTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return OffsetDateTime.now();
        }
        return localDateTime.atOffset(ZoneOffset.UTC);
    }
}
