package biz.ugur.busroutebackend.client.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.client.infrastructure.mapper.RouteFavoriteMapper;
import biz.ugur.busroutebackend.client.infrastructure.persistence.entity.RouteFavoriteEntity;
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


public abstract class RouteFavoriteBaseRepository extends BaseR2dbcRepository<RouteFavorite, RouteFavoriteId> {

    protected RouteFavoriteBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "route_favorites", RouteFavorite.class);
    }

    @Override
    protected String convertIdToDatabase(RouteFavoriteId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, RouteFavorite> getRowMapper() {
        return this::mapRowToRouteFavorite;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(RouteFavorite routeFavorite) {
        RouteFavoriteEntity entity = RouteFavoriteMapper.toEntity(routeFavorite);
        Map<String, Object> columns = new java.util.HashMap<>();
        columns.put("id", entity.getId());
        columns.put("client_id", entity.getClientId());
        columns.put("route_id", entity.getRouteId());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        columns.put("version", entity.getVersion());
        return columns;
    }


    private RouteFavorite mapRowToRouteFavorite(Row row, RowMetadata metadata) {
        return RouteFavoriteMapper.toDomain(RouteFavoriteEntity.builder()
                .id(row.get("id", String.class))
                .clientId(row.get("client_id", String.class))
                .routeId(row.get("route_id", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }


    public Flux<RouteFavorite> findBySpecification(Specification<RouteFavorite> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM route_favorites WHERE %s ORDER BY created_at DESC",
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


    public Flux<RouteFavorite> findBySpecification(Specification<RouteFavorite> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM route_favorites WHERE %s %s LIMIT :limit OFFSET :offset",
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


    public Mono<Long> countBySpecification(Specification<RouteFavorite> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT COUNT(*) FROM route_favorites WHERE %s",
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
