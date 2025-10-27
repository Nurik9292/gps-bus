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

/**
 * Base repository for City aggregate.
 * This abstract class provides common mapping and query functionality for all City repository implementations.
 * Follows the same pattern as BannerBaseRepository.
 */
public abstract class CityBaseRepository extends BaseR2dbcRepository<City, CityId> {

    protected CityBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "cities", City.class);
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

    /**
     * Maps database row to City domain model.
     * Uses CityMapper to convert entity to domain model.
     */
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

    // ============= Specification Pattern Support =============

    /**
     * Finds cities matching the given specification.
     * Converts specification to SQL criteria and executes query.
     *
     * @param specification the specification to match
     * @return Flux of cities matching the specification
     */
    public Flux<City> findBySpecification(Specification<City> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM cities WHERE %s ORDER BY display_order ASC, name ASC",
                criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        // Bind all parameters from specification
        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }

    /**
     * Finds cities matching the given specification with pagination.
     * Converts specification to SQL criteria, adds pagination, and executes query.
     *
     * @param specification the specification to match
     * @param pageable pagination parameters
     * @return Flux of cities matching the specification
     */
    public Flux<City> findBySpecification(Specification<City> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM cities WHERE %s %s LIMIT :limit OFFSET :offset",
                criteria.getWhereClause(),
                getOrderByClause(pageable)
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());

        // Bind all parameters from specification
        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }

    /**
     * Counts cities matching the given specification.
     * Converts specification to SQL criteria and executes count query.
     *
     * @param specification the specification to match
     * @return Mono of Long (count of matching cities)
     */
    public Mono<Long> countBySpecification(Specification<City> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT COUNT(*) FROM cities WHERE %s",
                criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        // Bind all parameters from specification
        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
