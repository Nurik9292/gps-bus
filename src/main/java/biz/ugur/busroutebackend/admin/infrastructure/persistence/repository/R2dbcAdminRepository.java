package biz.ugur.busroutebackend.admin.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
@Slf4j
public class R2dbcAdminRepository extends AdminBaseRepository implements AdminRepository {

    public R2dbcAdminRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Mono<Admin> findByUsername(String username) {
        String sql = String.format(
                "SELECT %s FROM admins WHERE username = :username",
                selectColumns()
        );

        return databaseClient.sql(sql)
                .bind("username", username)
                .map(getRowMapper())
                .one()
                .doOnNext(admin -> log.debug("Found admin by username: {}", username));
    }

    @Override
    public Flux<Admin> findActiveAdmins() {
        String sql = String.format("""
            SELECT %s FROM admins
            WHERE is_active = true
            ORDER BY full_name
            """, selectColumns());

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
        String sql = String.format("""
        UPDATE admins
        SET avatar = :avatar,
            updated_at = :updated_at,
            version = version + 1
        WHERE id = :id
          AND version = :old_version
        RETURNING %s
        """, selectColumns());

        return findById(adminId)
                .flatMap(existing -> {
                    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                            .bind("id", adminId.getValue())
                            .bind("updated_at", LocalDateTime.now())
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
