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
import java.util.stream.Collectors;


public abstract class AdminBaseRepository extends BaseR2dbcRepository<Admin, AdminId> {

    private static final String SELECT_COLUMNS = String.join(", ",
            "id", "username", "password_hash", "full_name", "avatar",
            "is_active", "is_super_admin", "last_login_at",
            "version", "created_at", "updated_at"
    );

    protected AdminBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "admins", Admin.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
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


    public Flux<Admin> findBySpecification(Specification<Admin> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT %s FROM admins WHERE %s ORDER BY full_name ASC, created_at DESC",
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

    @Override
    protected String getOrderByClause(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "ORDER BY created_at DESC";
        }
        return "ORDER BY " + pageable.getSort().stream()
                .map(order -> mapSortField(order.getProperty()) + " " + order.getDirection().name())
                .collect(Collectors.joining(", "));
    }

    private String mapSortField(String sortField) {
        return switch (sortField != null ? sortField.toLowerCase() : "created_at") {
            case "username" -> "username";
            case "fullname", "full_name" -> "full_name";
            case "createdat", "created_at" -> "created_at";
            case "updatedat", "updated_at" -> "updated_at";
            case "lastloginat", "last_login_at" -> "last_login_at";
            case "isactive", "is_active", "active" -> "is_active";
            case "issuperadmin", "is_super_admin" -> "is_super_admin";
            default -> "created_at";
        };
    }

    public Flux<Admin> findBySpecification(Specification<Admin> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT %s FROM admins WHERE %s %s LIMIT :limit OFFSET :offset",
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

    public Mono<Long> countBySpecification(Specification<Admin> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT COUNT(*) FROM admins WHERE %s",
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
