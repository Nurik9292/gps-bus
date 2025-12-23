package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.model.ImmediateRouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.ImmediateAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.ImmediateRouteAssignmentEntityMapper;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.ImmediateRouteAssignmentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;

@Repository
@Slf4j
public class R2dbcImmediateRouteAssignmentRepository implements ImmediateRouteAssignmentRepository {

    private final DatabaseClient databaseClient;
    private final ImmediateRouteAssignmentEntityMapper mapper;

    public R2dbcImmediateRouteAssignmentRepository(DatabaseClient databaseClient,
                                                    ImmediateRouteAssignmentEntityMapper mapper) {
        this.databaseClient = databaseClient;
        this.mapper = mapper;
    }

    @Override
    public Mono<ImmediateRouteAssignment> save(ImmediateRouteAssignment assignment) {
        ImmediateRouteAssignmentEntity entity = mapper.toEntity(assignment);

        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        String sql = """
            INSERT INTO immediate_route_assignments
                (id, vehicle_id, route_id, assigned_by, reason, assigned_at, expires_at, is_active, created_at, updated_at, version)
            VALUES
                (:id, :vehicleId, :routeId, :assignedBy, :reason, :assignedAt, :expiresAt, :isActive, :createdAt, :updatedAt, :version)
            ON CONFLICT (id) DO UPDATE SET
                vehicle_id = EXCLUDED.vehicle_id,
                route_id = EXCLUDED.route_id,
                assigned_by = EXCLUDED.assigned_by,
                reason = EXCLUDED.reason,
                assigned_at = EXCLUDED.assigned_at,
                expires_at = EXCLUDED.expires_at,
                is_active = EXCLUDED.is_active,
                updated_at = EXCLUDED.updated_at,
                version = immediate_route_assignments.version + 1
            RETURNING *
            """;

        return databaseClient.sql(sql)
                .bind("id", entity.getId())
                .bind("vehicleId", entity.getVehicleId())
                .bind("routeId", entity.getRouteId())
                .bind("assignedBy", entity.getAssignedBy())
                .bind("reason", entity.getReason() != null ? entity.getReason() : "")
                .bind("assignedAt", entity.getAssignedAt())
                .bindNull("expiresAt", Instant.class)
                .bind("isActive", entity.getIsActive())
                .bind("createdAt", entity.getCreatedAt())
                .bind("updatedAt", entity.getUpdatedAt())
                .bind("version", entity.getVersion() != null ? entity.getVersion() : 0L)
                .map((row, metadata) -> mapRowToEntity(row))
                .one()
                .map(mapper::toDomain)
                .doOnSuccess(saved -> log.debug("Saved immediate assignment: {}", saved.getId()));
    }

    @Override
    public Mono<ImmediateRouteAssignment> findById(ImmediateAssignmentId id) {
        String sql = "SELECT * FROM immediate_route_assignments WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .map((row, metadata) -> mapRowToEntity(row))
                .one()
                .map(mapper::toDomain);
    }

    @Override
    public Mono<ImmediateRouteAssignment> findActiveByVehicleId(VehicleId vehicleId) {
        String sql = """
            SELECT * FROM immediate_route_assignments
            WHERE vehicle_id = :vehicleId AND is_active = true
            ORDER BY assigned_at DESC
            LIMIT 1
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .map((row, metadata) -> mapRowToEntity(row))
                .one()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<ImmediateRouteAssignment> findAllActive() {
        String sql = "SELECT * FROM immediate_route_assignments WHERE is_active = true ORDER BY assigned_at DESC";

        return databaseClient.sql(sql)
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<ImmediateRouteAssignment> findByVehicleId(VehicleId vehicleId) {
        String sql = "SELECT * FROM immediate_route_assignments WHERE vehicle_id = :vehicleId ORDER BY assigned_at DESC";

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<ImmediateRouteAssignment> findByRouteId(BusRouteId routeId) {
        String sql = "SELECT * FROM immediate_route_assignments WHERE route_id = :routeId AND is_active = true ORDER BY assigned_at DESC";

        return databaseClient.sql(sql)
                .bind("routeId", routeId.getValue())
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<ImmediateRouteAssignment> findExpired() {
        String sql = """
            SELECT * FROM immediate_route_assignments
            WHERE is_active = true AND expires_at IS NOT NULL AND expires_at < :now
            """;

        return databaseClient.sql(sql)
                .bind("now", Instant.now())
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(ImmediateAssignmentId id) {
        String sql = "DELETE FROM immediate_route_assignments WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Long> countActive() {
        String sql = "SELECT COUNT(*) FROM immediate_route_assignments WHERE is_active = true";

        return databaseClient.sql(sql)
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Integer> deactivateByVehicleId(VehicleId vehicleId) {
        String sql = """
            UPDATE immediate_route_assignments
            SET is_active = false, updated_at = :now
            WHERE vehicle_id = :vehicleId AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .bind("now", LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .map(Long::intValue);
    }

    private ImmediateRouteAssignmentEntity mapRowToEntity(io.r2dbc.spi.Row row) {
        return ImmediateRouteAssignmentEntity.builder()
                .id(row.get("id", String.class))
                .vehicleId(row.get("vehicle_id", String.class))
                .routeId(row.get("route_id", String.class))
                .assignedBy(row.get("assigned_by", String.class))
                .reason(row.get("reason", String.class))
                .assignedAt(row.get("assigned_at", Instant.class))
                .expiresAt(row.get("expires_at", Instant.class))
                .isActive(row.get("is_active", Boolean.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build();
    }
}
