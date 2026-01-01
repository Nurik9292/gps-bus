package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.RouteAssignmentEntityMapper;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.RouteAssignmentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
@Slf4j
public class R2dbcRouteAssignmentRepository implements RouteAssignmentRepository {

    private final DatabaseClient databaseClient;
    private final RouteAssignmentEntityMapper mapper;

    public R2dbcRouteAssignmentRepository(DatabaseClient databaseClient,
                                          RouteAssignmentEntityMapper mapper) {
        this.databaseClient = databaseClient;
        this.mapper = mapper;
    }

    @Override
    public Mono<RouteAssignment> save(RouteAssignment assignment) {
        RouteAssignmentEntity entity = mapper.toEntity(assignment);

        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        String sql = """
            INSERT INTO route_assignments
                (id, vehicle_id, route_id, effective_date, shift_type,
                 assigned_by, reason, expires_at, is_active, created_at, updated_at, version)
            VALUES
                (:id, :vehicleId, :routeId, :effectiveDate, :shiftType,
                 :assignedBy, :reason, :expiresAt, :isActive, :createdAt, :updatedAt, :version)
            ON CONFLICT (id) DO UPDATE SET
                vehicle_id = EXCLUDED.vehicle_id,
                route_id = EXCLUDED.route_id,
                effective_date = EXCLUDED.effective_date,
                shift_type = EXCLUDED.shift_type,
                assigned_by = EXCLUDED.assigned_by,
                reason = EXCLUDED.reason,
                expires_at = EXCLUDED.expires_at,
                is_active = EXCLUDED.is_active,
                updated_at = EXCLUDED.updated_at,
                version = route_assignments.version + 1
            RETURNING *
            """;

        var spec = databaseClient.sql(sql)
                .bind("id", entity.getId())
                .bind("vehicleId", entity.getVehicleId())
                .bind("routeId", entity.getRouteId())
                .bind("effectiveDate", entity.getEffectiveDate())
                .bind("shiftType", entity.getShiftType())
                .bind("assignedBy", entity.getAssignedBy());

        spec = bindNullable(spec, "reason", entity.getReason(), String.class);
        spec = bindNullable(spec, "expiresAt", entity.getExpiresAt(), Instant.class);

        return spec
                .bind("isActive", entity.getIsActive())
                .bind("createdAt", entity.getCreatedAt())
                .bind("updatedAt", entity.getUpdatedAt())
                .bind("version", entity.getVersion() != null ? entity.getVersion() : 0L)
                .map((row, metadata) -> mapRowToEntity(row))
                .one()
                .map(mapper::toDomain)
                .doOnSuccess(saved -> log.debug("Saved route assignment: {}", saved.getId()));
    }

    private <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            T value,
            Class<T> type) {
        if (value != null && !(value instanceof String s && s.trim().isEmpty())) {
            return spec.bind(name, value);
        }
        return spec.bindNull(name, type);
    }

    @Override
    public Mono<RouteAssignment> findById(RouteAssignmentId id) {
        String sql = "SELECT * FROM route_assignments WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .map((row, metadata) -> mapRowToEntity(row))
                .one()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<RouteAssignment> findAll() {
        String sql = "SELECT * FROM route_assignments ORDER BY effective_date DESC, created_at DESC";

        return databaseClient.sql(sql)
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<RouteAssignment> findAll(org.springframework.data.domain.Pageable pageable) {
        String sql = "SELECT * FROM route_assignments ORDER BY effective_date DESC, created_at DESC " +
                     "LIMIT :limit OFFSET :offset";

        return databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(RouteAssignmentId id) {
        String sql = "DELETE FROM route_assignments WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<RouteAssignment> findActiveByVehicleAndDateAndShift(
            VehicleId vehicleId,
            LocalDate effectiveDate,
            ShiftType shiftType) {

        String sql = """
            SELECT * FROM route_assignments
            WHERE vehicle_id = :vehicleId
              AND effective_date = :effectiveDate
              AND shift_type = :shiftType
              AND is_active = true
            LIMIT 1
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .bind("effectiveDate", effectiveDate)
                .bind("shiftType", shiftType.name())
                .map((row, metadata) -> mapRowToEntity(row))
                .one()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<RouteAssignment> findActiveByVehicleId(VehicleId vehicleId) {
        String sql = """
            SELECT * FROM route_assignments
            WHERE vehicle_id = :vehicleId AND is_active = true
            ORDER BY effective_date DESC, created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<RouteAssignment> findActiveByDateAndShift(LocalDate effectiveDate, ShiftType shiftType) {
        String sql = """
            SELECT * FROM route_assignments
            WHERE effective_date = :effectiveDate
              AND shift_type = :shiftType
              AND is_active = true
            ORDER BY created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("effectiveDate", effectiveDate)
                .bind("shiftType", shiftType.name())
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<RouteAssignment> findActiveByRouteId(BusRouteId routeId) {
        String sql = """
            SELECT * FROM route_assignments
            WHERE route_id = :routeId AND is_active = true
            ORDER BY effective_date DESC, created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId.getValue())
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<RouteAssignment> findExpiredAssignments() {
        String sql = """
            SELECT * FROM route_assignments
            WHERE is_active = true
              AND expires_at IS NOT NULL
              AND expires_at < :now
            ORDER BY expires_at ASC
            """;

        return databaseClient.sql(sql)
                .bind("now", Instant.now())
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<RouteAssignment> findActiveByEffectiveDateBefore(LocalDate date) {
        String sql = """
            SELECT * FROM route_assignments
            WHERE effective_date < :date AND is_active = true
            ORDER BY effective_date ASC
            """;

        return databaseClient.sql(sql)
                .bind("date", date)
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Flux<RouteAssignment> findActiveByAssignedBy(String assignedBy) {
        String sql = """
            SELECT * FROM route_assignments
            WHERE assigned_by = :assignedBy AND is_active = true
            ORDER BY effective_date DESC, created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("assignedBy", assignedBy)
                .map((row, metadata) -> mapRowToEntity(row))
                .all()
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsActiveByVehicleAndDateAndShift(
            VehicleId vehicleId,
            LocalDate effectiveDate,
            ShiftType shiftType) {

        String sql = """
            SELECT EXISTS(
                SELECT 1 FROM route_assignments
                WHERE vehicle_id = :vehicleId
                  AND effective_date = :effectiveDate
                  AND shift_type = :shiftType
                  AND is_active = true
            )
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .bind("effectiveDate", effectiveDate)
                .bind("shiftType", shiftType.name())
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Integer> deactivateAllByVehicleId(VehicleId vehicleId) {
        String sql = """
            UPDATE route_assignments
            SET is_active = false, updated_at = :now
            WHERE vehicle_id = :vehicleId AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .bind("now", LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .map(Long::intValue)
                .doOnSuccess(count -> log.debug("Deactivated {} assignments for vehicle: {}", count, vehicleId));
    }

    @Override
    public Mono<RouteAssignment> deactivateById(RouteAssignmentId id) {
        return findById(id)
                .flatMap(assignment -> {
                    RouteAssignment deactivated = assignment.deactivate();
                    return save(deactivated);
                });
    }

    @Override
    public Mono<Integer> deleteByEffectiveDateBefore(LocalDate date) {
        String sql = """
            DELETE FROM route_assignments
            WHERE effective_date < :date
            """;

        return databaseClient.sql(sql)
                .bind("date", date)
                .fetch()
                .rowsUpdated()
                .map(Long::intValue)
                .doOnSuccess(count -> log.info("Deleted {} old assignments (effective_date < {})", count, date));
    }

    @Override
    public Mono<Long> count() {
        String sql = "SELECT COUNT(*) FROM route_assignments WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnSuccess(count -> log.debug("Counted {} active route assignments", count))
                .doOnError(error -> log.error("Failed to count route assignments", error));
    }

    @Override
    public Mono<Boolean> existsById(RouteAssignmentId id) {
        String sql = "SELECT COUNT(*) > 0 FROM route_assignments WHERE id = :id AND is_active = true";

        return databaseClient.sql(sql)
                .bind("id", id.getValue())
                .map(row -> row.get(0, Boolean.class))
                .one()
                .doOnSuccess(exists -> log.debug("Assignment exists check for id {}: {}", id.getValue(), exists))
                .doOnError(error -> log.error("Failed to check existence for assignment id: {}", id.getValue(), error));
    }

    private RouteAssignmentEntity mapRowToEntity(io.r2dbc.spi.Row row) {
        return RouteAssignmentEntity.builder()
                .id(row.get("id", String.class))
                .vehicleId(row.get("vehicle_id", String.class))
                .routeId(row.get("route_id", String.class))
                .effectiveDate(row.get("effective_date", LocalDate.class))
                .shiftType(row.get("shift_type", String.class))
                .assignedBy(row.get("assigned_by", String.class))
                .reason(row.get("reason", String.class))
                .expiresAt(row.get("expires_at", Instant.class))
                .isActive(row.get("is_active", Boolean.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build();
    }
}
