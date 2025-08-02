package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;


@Repository
@Slf4j
@Transactional(readOnly = true)
public class R2dbcAdminRepository implements AdminRepository {

    private final DatabaseClient databaseClient;

    public R2dbcAdminRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    @Transactional
    public Mono<Admin> save(Admin admin) {
        log.info("Saving admin: {}", admin);
        if (admin.isNew()) {
            return insert(admin).doOnSuccess(Admin::markAsExisting);
        } else {
            return update(admin);
        }
    }

    private Mono<Admin> insert(Admin admin) {
        Instant now = Instant.now();
        return databaseClient.sql("INSERT INTO admins (id, username, password_hash, full_name, is_active, " +
                        "is_super_admin, last_login_at, created_at, updated_at, version) " +
                        "VALUES (:id, :username, :passwordHash, :fullName, :isActive, " +
                        ":isSuperAdmin, :lastLoginAt, :createdAt, :updatedAt, :version)")
                .bind("id",  admin.getId().toString())
                .bind("username", admin.getUsername())
                .bind("passwordHash", admin.getPasswordHash())
                .bind("fullName", admin.getFullName())
                .bind("isActive", admin.getIsActive())
                .bind("isSuperAdmin", admin.getIsSuperAdmin())
                .bind("lastLoginAt", admin.getLastLoginAt())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 1L)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows > 0
                        ? Mono.just(admin)
                        : Mono.error(new RuntimeException("Insert failed: no rows updated")))
                .doOnSuccess(a -> log.debug("Inserted admin: {}", a.getUsername()))
                .doOnError(e -> log.error("Error inserting admin", e));
    }


    private Mono<Admin> update(Admin admin) {
        log.info("Updating admin repository : {}", admin);
        return databaseClient.sql(" UPDATE admins " +
                        "SET username = :username, password_hash = :passwordHash, full_name = :fullName, " +
                        "is_active = :isActive, is_super_admin = :isSuperAdmin, last_login_at = :lastLoginAt, " +
                        "updated_at = :updatedAt, version = version + 1 WHERE id = :id")
                .bind("username", admin.getUsername())
                .bind("passwordHash", admin.getPasswordHash())
                .bind("fullName", admin.getFullName())
                .bind("isActive", admin.getIsActive())
                .bind("isSuperAdmin", admin.getIsSuperAdmin())
                .bind("lastLoginAt", admin.getLastLoginAt())
                .bind("updatedAt", Instant.now())
                .bind("id", admin.getId().getValue())
                .fetch()
                .rowsUpdated()
                .thenReturn(admin)
                .doOnSuccess(a -> log.debug("Updated admin: {}", a.getUsername()));
    }

    @Override
    public Mono<Admin> findById(AdminId adminId) {
        return databaseClient.sql("SELECT * FROM admins WHERE id = :id")
                .bind("id", adminId.getValue())
                .map(this::mapRowToAdmin)
                .one()
                .doOnNext(a -> log.debug("Found admin by ID: {}", adminId.getValue()));
    }

    @Override
    public Mono<Admin> findByUsername(String username) {
        return databaseClient.sql("SELECT * FROM admins WHERE username = :username")
                .bind("username", username)
                .map(this::mapRowToAdmin)
                .first()
                .doOnNext(a -> log.debug("Found admin by username: {}", username));
    }

    @Override
    public Flux<Admin> findActiveAdmins() {
        return databaseClient.sql("SELECT * FROM admins WHERE is_active = true ORDER BY username")
                .map(this::mapRowToAdmin)
                .all()
                .doOnComplete(() -> log.debug("Found all active admins"));
    }

    @Override
    public Flux<Admin> findAllAdmins() {
        return databaseClient.sql("SELECT * FROM admins ORDER BY created_at DESC")
                .map(this::mapRowToAdmin)
                .all()
                .doOnComplete(() -> log.debug("Found all admins"));
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        log.info("Checking if admin exists: {}", username);
        return databaseClient.sql("SELECT COUNT(*) FROM admins WHERE username = :username")
                .bind("username", username)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    @Transactional
    public Mono<Void> deleteById(AdminId adminId) {
        return databaseClient.sql("DELETE FROM admins WHERE id = :id")
                .bind("id", adminId.getValue())
                .then()
                .doOnSuccess(v -> log.debug("Deleted admin: {}", adminId.getValue()));
    }

    @Override
    public Mono<Long> countActiveAdmins() {
        return databaseClient.sql("SELECT COUNT(*) FROM admins WHERE is_active = true")
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnNext(count -> log.debug("Active admins count: {}", count));
    }

    private Admin mapRowToAdmin(Row row, RowMetadata metadata) {
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