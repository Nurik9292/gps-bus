package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;


@Repository
@Slf4j
public class R2dbcAdminRepository extends BaseR2dbcRepository<Admin, AdminId> implements AdminRepository {

    public R2dbcAdminRepository(DatabaseClient databaseClient) {
        super(databaseClient, "admins", Admin.class);
    }

    @Override
    protected String convertIdToDatabase(AdminId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Admin> getRowMapper() {
        return (row, metadata) -> Admin.fromDatabase(
                AdminId.of(row.get("id", String.class)),
                row.get("username", String.class),
                row.get("password_hash", String.class),
                row.get("full_name", String.class),
                row.get("avatar", String.class),
                row.get("is_active", Boolean.class),
                row.get("is_super_admin", Boolean.class),
                row.get("last_login_at", Instant.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class),
                row.get("version", Long.class)
        );
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Admin admin) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", admin.getId().getValue());
        values.put("username", admin.getUsername());
        values.put("password_hash", admin.getPasswordHash());
        values.put("full_name", admin.getFullName());
        values.put("avatar", admin.getAvatar());
        values.put("is_active", admin.getIsActive());
        values.put("is_super_admin", admin.getIsSuperAdmin());
        values.put("last_login_at", admin.getLastLoginAt());
        return values;
    }

    @Override
    public Mono<Admin> findByUsername(String username) {
        String sql = "SELECT * FROM admins WHERE username = :username";

        return databaseClient.sql(sql)
                .bind("username", username)
                .map(getRowMapper())
                .one()
                .doOnNext(admin -> log.debug("Found admin by username: {}", username));
    }

    @Override
    public Flux<Admin> findActiveAdmins() {
        String sql = """
            SELECT * FROM admins 
            WHERE is_active = true 
            ORDER BY full_name
            """;

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        String sql = """
            SELECT EXISTS(
                SELECT 1 FROM admins 
                WHERE username = :username
            ) AS exists_flag
            """;

        return databaseClient.sql(sql)
                .bind("username", username)
                .map(row -> row.get("exists_flag", Boolean.class))
                .one();
    }

    @Override
    public Mono<Long> countActiveAdmins() {
        String sql = "SELECT COUNT(*) AS cnt FROM admins WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    @Override
    public Mono<Admin> updateAvatar(AdminId adminId, String avatar) {
        String sql = """
        UPDATE admins 
        SET avatar = :avatar, 
            updated_at = :updated_at,
            version = version + 1
        WHERE id = :id 
          AND version = :old_version
        RETURNING *
        """;

        return findById(adminId)
                .flatMap(existing -> {
                    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                            .bind("id", adminId.getValue())
                            .bind("updated_at", Instant.now())
                            .bind("old_version", existing.getVersion());

                    spec = bindValue(spec, "avatar", avatar);

                    return spec.map(getRowMapper())
                            .one()
                            .switchIfEmpty(Mono.defer(() -> {
                                String msg = "Version conflict when updating avatar for Admin with id: " + adminId;
                                log.error(msg);
                                return Mono.error(new OptimisticLockingFailureException(msg));
                            }))
                            .doOnSuccess(admin -> log.debug(
                                    "Updated avatar for admin: {} to: {}",
                                    adminId, avatar != null ? avatar : "NULL"
                            ));
                });
    }
}
