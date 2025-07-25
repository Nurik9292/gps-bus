package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
@Slf4j
public class R2dbcAdminRepository implements AdminRepository {

    private final DatabaseClient databaseClient;

    public R2dbcAdminRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Admin> save(Admin admin) {
        if (admin.getId() == null) {
            return insert(admin);
        } else {
            return update(admin);
        }
    }

    private Mono<Admin> insert(Admin admin) {
        String sql = """
            INSERT INTO admins (id, username, password_hash, full_name, is_active, is_super_admin, 
                               last_login_at, created_at, updated_at, version)
            VALUES (:id, :username, :passwordHash, :fullName, :isActive, :isSuperAdmin,
                   :lastLoginAt, :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();
        return databaseClient.sql(sql)
                .bind("id", admin.getId().getValue())
                .bind("username", admin.getUsername())
                .bind("passwordHash", admin.getPasswordHash())
                .bind("fullName", admin.getFullName())
                .bind("isActive", admin.getIsActive())
                .bind("isSuperAdmin", admin.getIsSuperAdmin())
                .bind("lastLoginAt", admin.getLastLoginAt())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 0L)
                .then()
                .thenReturn(admin)
                .doOnSuccess(a -> log.debug("Inserted admin: {}", a.getUsername()));
    }

    private Mono<Admin> update(Admin admin) {
        String sql = """
            UPDATE admins 
            SET username = :username, password_hash = :passwordHash, full_name = :fullName,
                is_active = :isActive, is_super_admin = :isSuperAdmin, last_login_at = :lastLoginAt,
                updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", admin.getId().getValue())
                .bind("username", admin.getUsername())
                .bind("passwordHash", admin.getPasswordHash())
                .bind("fullName", admin.getFullName())
                .bind("isActive", admin.getIsActive())
                .bind("isSuperAdmin", admin.getIsSuperAdmin())
                .bind("lastLoginAt", admin.getLastLoginAt())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(admin)
                .doOnSuccess(a -> log.debug("Updated admin: {}", a.getUsername()));
    }

    @Override
    public Mono<Admin> findById(AdminId adminId) {
        String sql = "SELECT * FROM admins WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", adminId.getValue())
                .map(this::mapRowToAdmin)
                .one()
                .doOnNext(a -> log.debug("Found admin by ID: {}", adminId.getValue()));
    }

    @Override
    public Mono<Admin> findByUsername(String username) {
        String sql = "SELECT * FROM admins WHERE username = :username";

        return databaseClient.sql(sql)
                .bind("username", username)
                .map(this::mapRowToAdmin)
                .one()
                .doOnNext(a -> log.debug("Found admin by username: {}", username));
    }

    @Override
    public Flux<Admin> findActiveAdmins() {
        String sql = "SELECT * FROM admins WHERE is_active = true ORDER BY username";

        return databaseClient.sql(sql)
                .map(this::mapRowToAdmin)
                .all()
                .doOnComplete(() -> log.debug("Found all active admins"));
    }

    @Override
    public Flux<Admin> findAllAdmins() {
        String sql = "SELECT * FROM admins ORDER BY created_at DESC";

        return databaseClient.sql(sql)
                .map(this::mapRowToAdmin)
                .all()
                .doOnComplete(() -> log.debug("Found all admins"));
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM admins WHERE username = :username";

        return databaseClient.sql(sql)
                .bind("username", username)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    public Mono<Void> deleteById(AdminId adminId) {
        String sql = "DELETE FROM admins WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", adminId.getValue())
                .then()
                .doOnSuccess(v -> log.debug("Deleted admin: {}", adminId.getValue()));
    }

    @Override
    public Mono<Long> countActiveAdmins() {
        String sql = "SELECT COUNT(*) FROM admins WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnNext(count -> log.debug("Active admins count: {}", count));
    }

    private Admin mapRowToAdmin(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new Admin(
                AdminId.of(row.get("id", String.class)),
                row.get("username", String.class),
                row.get("password_hash", String.class),
                row.get("full_name", String.class),
                row.get("is_active", Boolean.class),
                row.get("is_super_admin", Boolean.class),
                row.get("last_login_at", Instant.class)
        );
    }
}