package biz.ugur.busroutebackend.admin.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.admin.infrastructure.mapper.AdminMapper;
import biz.ugur.busroutebackend.admin.infrastructure.persistence.entity.AdminEntity;
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
 * Base repository for Admin aggregate.
 * This abstract class provides common mapping and query functionality for all Admin repository implementations.
 * Follows the same pattern as BannerBaseRepository.
 */
public abstract class AdminBaseRepository extends BaseR2dbcRepository<Admin, AdminId> {

    protected AdminBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "admins", Admin.class);
    }

    @Override
    protected String convertIdToDatabase(AdminId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Admin> getRowMapper() {
        return this::mapRowToAdmin;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Admin admin) {
        AdminEntity entity = AdminMapper.toEntity(admin);
        Map<String, Object> columns = new java.util.HashMap<>();
        columns.put("id", entity.getId());
        columns.put("username", entity.getUsername());
        columns.put("password_hash", entity.getPasswordHash());
        columns.put("full_name", entity.getFullName());
        columns.put("avatar", entity.getAvatar());
        columns.put("is_active", entity.getIsActive());
        columns.put("is_super_admin", entity.getIsSuperAdmin());
        columns.put("last_login_at", entity.getLastLoginAt());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    /**
     * Maps database row to Admin domain model.
     * Uses AdminMapper to convert entity to domain model.
     */
    private Admin mapRowToAdmin(Row row, RowMetadata metadata) {
        return AdminMapper.toDomain(AdminEntity.builder()
                .id(row.get("id", String.class))
                .username(row.get("username", String.class))
                .passwordHash(row.get("password_hash", String.class))
                .fullName(row.get("full_name", String.class))
                .avatar(row.get("avatar", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .isSuperAdmin(row.get("is_super_admin", Boolean.class))
                .lastLoginAt(row.get("last_login_at", LocalDateTime.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }

    // ============= Specification Pattern Support =============

    /**
     * Finds admins matching the given specification.
     * Converts specification to SQL criteria and executes query.
     *
     * @param specification the specification to match
     * @return Flux of admins matching the specification
     */
    public Flux<Admin> findBySpecification(Specification<Admin> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM admins WHERE %s ORDER BY full_name ASC, created_at DESC",
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
     * Finds admins matching the given specification with pagination.
     * Converts specification to SQL criteria, adds pagination, and executes query.
     *
     * @param specification the specification to match
     * @param pageable pagination parameters
     * @return Flux of admins matching the specification
     */
    public Flux<Admin> findBySpecification(Specification<Admin> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM admins WHERE %s %s LIMIT :limit OFFSET :offset",
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
     * Counts admins matching the given specification.
     * Converts specification to SQL criteria and executes count query.
     *
     * @param specification the specification to match
     * @return Mono of Long (count of matching admins)
     */
    public Mono<Long> countBySpecification(Specification<Admin> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT COUNT(*) FROM admins WHERE %s",
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
