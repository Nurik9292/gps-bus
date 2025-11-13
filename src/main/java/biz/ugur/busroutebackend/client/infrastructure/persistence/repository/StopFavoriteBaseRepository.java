package biz.ugur.busroutebackend.client.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.client.infrastructure.mapper.StopFavoriteMapper;
import biz.ugur.busroutebackend.client.infrastructure.persistence.entity.StopFavoriteEntity;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.BiFunction;


public abstract class StopFavoriteBaseRepository extends BaseR2dbcRepository<StopFavorite, StopFavoriteId> {

    protected StopFavoriteBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "stop_favorites", StopFavorite.class);
    }

    @Override
    protected String convertIdToDatabase(StopFavoriteId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, StopFavorite> getRowMapper() {
        return this::mapRowToStopFavorite;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(StopFavorite stopFavorite) {
        StopFavoriteEntity entity = StopFavoriteMapper.toEntity(stopFavorite);
        Map<String, Object> columns = new java.util.HashMap<>();
        columns.put("id", entity.getId());
        columns.put("client_id", entity.getClientId());
        columns.put("stop_id", entity.getStopId());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        columns.put("version", entity.getVersion());
        return columns;
    }


    private StopFavorite mapRowToStopFavorite(Row row, RowMetadata metadata) {
        return StopFavoriteMapper.toDomain(StopFavoriteEntity.builder()
                .id(row.get("id", String.class))
                .clientId(row.get("client_id", String.class))
                .stopId(row.get("stop_id", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }



    public Flux<StopFavorite> findBySpecification(Specification<StopFavorite> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM stop_favorites WHERE %s ORDER BY created_at DESC",
                criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }


    public Flux<StopFavorite> findBySpecification(Specification<StopFavorite> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM stop_favorites WHERE %s %s LIMIT :limit OFFSET :offset",
                criteria.getWhereClause(),
                getOrderByClause(pageable)
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }


    public Mono<Long> countBySpecification(Specification<StopFavorite> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT COUNT(*) FROM stop_favorites WHERE %s",
                criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
