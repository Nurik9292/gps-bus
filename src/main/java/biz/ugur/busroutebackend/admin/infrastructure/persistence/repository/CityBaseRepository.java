package biz.ugur.busroutebackend.admin.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.admin.infrastructure.mapper.CityMapper;
import biz.ugur.busroutebackend.admin.infrastructure.persistence.entity.CityEntity;
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

public abstract class CityBaseRepository extends BaseR2dbcRepository<City, CityId> {

    private static final String SELECT_COLUMNS = String.join(", ",
            "id", "name", "name_tm", "is_active", "display_order",
            "version", "created_at", "updated_at"
    );

    protected CityBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "cities", City.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(CityId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, City> getRowMapper() {
        return this::mapRowToCity;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(City city) {
        CityEntity entity = CityMapper.toEntity(city);
        Map<String, Object> columns = new java.util.HashMap<>();
        columns.put("id", entity.getId());
        columns.put("name", entity.getName());
        columns.put("name_tm", entity.getNameTm());
        columns.put("is_active", entity.getIsActive());
        columns.put("display_order", entity.getDisplayOrder());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    private City mapRowToCity(Row row, RowMetadata metadata) {
        return CityMapper.toDomain(CityEntity.builder()
                .id(row.get("id", String.class))
                .name(row.get("name", String.class))
                .nameTm(row.get("name_tm", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .displayOrder(row.get("display_order", Integer.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }


    public Flux<City> findBySpecification(Specification<City> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT %s FROM cities WHERE %s ORDER BY display_order ASC, name ASC",
                selectColumns(),
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

    public Flux<City> findBySpecification(Specification<City> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT %s FROM cities WHERE %s %s LIMIT :limit OFFSET :offset",
                selectColumns(),
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

    public Mono<Long> countBySpecification(Specification<City> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT COUNT(*) FROM cities WHERE %s",
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
