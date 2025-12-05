package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAlternativeId;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.RouteAlternativeEntityMapper;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.RouteAlternativeEntity;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
public class R2dbcRouteAlternativeRepository extends BaseR2dbcRepository<RouteAlternative, RouteAlternativeId>
        implements RouteAlternativeRepository {

    private final RouteAlternativeEntityMapper entityMapper;

    public R2dbcRouteAlternativeRepository(DatabaseClient databaseClient, RouteAlternativeEntityMapper entityMapper) {
        super(databaseClient, "route_alternatives", RouteAlternative.class);
        this.entityMapper = entityMapper;
    }

    @Override
    protected String convertIdToDatabase(RouteAlternativeId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, RouteAlternative> getRowMapper() {
        return this::mapRowToRouteAlternative;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(RouteAlternative entity) {
        RouteAlternativeEntity persistenceEntity = entityMapper.toEntity(entity);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", persistenceEntity.getId());
        columns.put("primary_route_id", persistenceEntity.getPrimaryRouteId());
        columns.put("alternative_route_id", persistenceEntity.getAlternativeRouteId());
        columns.put("priority", persistenceEntity.getPriority());
        columns.put("description", persistenceEntity.getDescription());
        columns.put("description_tm", persistenceEntity.getDescriptionTm());
        columns.put("description_en", persistenceEntity.getDescriptionEn());
        return columns;
    }

    private RouteAlternative mapRowToRouteAlternative(Row row, RowMetadata metadata) {
        RouteAlternativeEntity entity = RouteAlternativeEntity.builder()
                .id(row.get("id", String.class))
                .primaryRouteId(row.get("primary_route_id", String.class))
                .alternativeRouteId(row.get("alternative_route_id", String.class))
                .priority(row.get("priority", Integer.class))
                .description(row.get("description", String.class))
                .descriptionTm(row.get("description_tm", String.class))
                .descriptionEn(row.get("description_en", String.class))
                .createdAt(convertToLocalDateTime(row.get("created_at")))
                .updatedAt(convertToLocalDateTime(row.get("updated_at")))
                .version(row.get("version", Long.class))
                .build();

        return entityMapper.toDomain(entity);
    }

    private LocalDateTime convertToLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        if (value instanceof OffsetDateTime) return ((OffsetDateTime) value).toLocalDateTime();
        return null;
    }

    @Override
    public Flux<RouteAlternative> findByPrimaryRouteId(BusRouteId primaryRouteId) {
        String sql = """
            SELECT * FROM route_alternatives
            WHERE primary_route_id = :primaryRouteId
            ORDER BY priority ASC
            """;

        return databaseClient.sql(sql)
                .bind("primaryRouteId", primaryRouteId.getValue())
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Found alternatives for primary route: {}", primaryRouteId));
    }

    @Override
    public Flux<RouteAlternative> findByAlternativeRouteId(BusRouteId alternativeRouteId) {
        String sql = """
            SELECT * FROM route_alternatives
            WHERE alternative_route_id = :alternativeRouteId
            ORDER BY priority ASC
            """;

        return databaseClient.sql(sql)
                .bind("alternativeRouteId", alternativeRouteId.getValue())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Boolean> existsByPrimaryAndAlternative(BusRouteId primaryRouteId, BusRouteId alternativeRouteId) {
        String sql = """
            SELECT EXISTS(
                SELECT 1 FROM route_alternatives
                WHERE primary_route_id = :primaryRouteId
                AND alternative_route_id = :alternativeRouteId
            )
            """;

        return databaseClient.sql(sql)
                .bind("primaryRouteId", primaryRouteId.getValue())
                .bind("alternativeRouteId", alternativeRouteId.getValue())
                .map(row -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Mono<RouteAlternative> findByPrimaryAndAlternative(BusRouteId primaryRouteId, BusRouteId alternativeRouteId) {
        String sql = """
            SELECT * FROM route_alternatives
            WHERE primary_route_id = :primaryRouteId
            AND alternative_route_id = :alternativeRouteId
            """;

        return databaseClient.sql(sql)
                .bind("primaryRouteId", primaryRouteId.getValue())
                .bind("alternativeRouteId", alternativeRouteId.getValue())
                .map(getRowMapper())
                .one();
    }

    @Override
    public Mono<Void> deleteByPrimaryRouteId(BusRouteId primaryRouteId) {
        String sql = "DELETE FROM route_alternatives WHERE primary_route_id = :primaryRouteId";

        return databaseClient.sql(sql)
                .bind("primaryRouteId", primaryRouteId.getValue())
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> log.debug("Deleted {} alternatives for primary route: {}", count, primaryRouteId))
                .then();
    }

    @Override
    public Mono<Void> deleteByAlternativeRouteId(BusRouteId alternativeRouteId) {
        String sql = "DELETE FROM route_alternatives WHERE alternative_route_id = :alternativeRouteId";

        return databaseClient.sql(sql)
                .bind("alternativeRouteId", alternativeRouteId.getValue())
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> log.debug("Deleted {} alternatives referencing: {}", count, alternativeRouteId))
                .then();
    }

    @Override
    public Mono<Long> countByPrimaryRouteId(BusRouteId primaryRouteId) {
        String sql = "SELECT COUNT(*) FROM route_alternatives WHERE primary_route_id = :primaryRouteId";

        return databaseClient.sql(sql)
                .bind("primaryRouteId", primaryRouteId.getValue())
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
