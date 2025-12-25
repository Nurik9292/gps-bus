package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.VehicleShiftAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleShiftAssignmentId;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.VehicleShiftAssignmentEntityMapper;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.VehicleShiftAssignmentEntity;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
@Transactional(readOnly = true)
public class R2dbcVehicleShiftAssignmentRepository
        extends BaseR2dbcRepository<VehicleShiftAssignment, VehicleShiftAssignmentId>
        implements VehicleShiftAssignmentRepository {

    private final VehicleShiftAssignmentEntityMapper entityMapper;

    public R2dbcVehicleShiftAssignmentRepository(
            DatabaseClient databaseClient,
            VehicleShiftAssignmentEntityMapper entityMapper) {
        super(databaseClient, "vehicle_shift_assignments", VehicleShiftAssignment.class);
        this.entityMapper = entityMapper;
    }

    @Override
    protected String convertIdToDatabase(VehicleShiftAssignmentId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, VehicleShiftAssignment> getRowMapper() {
        return this::mapRowToEntity;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(VehicleShiftAssignment entity) {
        VehicleShiftAssignmentEntity persistenceEntity = entityMapper.toEntity(entity);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", persistenceEntity.getId());
        columns.put("vehicle_id", persistenceEntity.getVehicleId());
        columns.put("route_id", persistenceEntity.getRouteId());
        columns.put("shift_type", persistenceEntity.getShiftType());
        columns.put("is_active", persistenceEntity.getIsActive());
        columns.put("assigned_by", persistenceEntity.getAssignedBy());
        return columns;
    }

    @Override
    @Transactional
    protected Mono<VehicleShiftAssignment> insert(VehicleShiftAssignment entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("created_at", LocalDateTime.now());
        values.put("updated_at", LocalDateTime.now());
        values.put("version", 1L);

        String sql = """
            INSERT INTO vehicle_shift_assignments (
                id, vehicle_id, route_id, shift_type, is_active, assigned_by,
                created_at, updated_at, version
            ) VALUES (
                :id, :vehicle_id, :route_id, :shift_type, :is_active, :assigned_by,
                :created_at, :updated_at, :version
            ) RETURNING *
            """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            spec = bindValue(spec, entry.getKey(), entry.getValue());
        }

        return spec
                .map(getRowMapper())
                .one()
                .switchIfEmpty(Mono.error(
                        new RuntimeException("Failed to insert VehicleShiftAssignment")
                ))
                .doOnSuccess(v -> log.info("Created shift assignment: vehicle={}, route={}, shift={}",
                        v.getVehicleId(), v.getRouteId(), v.getShiftType()));
    }

    @Override
    @Transactional
    protected Mono<VehicleShiftAssignment> update(VehicleShiftAssignment entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("updated_at", LocalDateTime.now());
        values.put("version", entity.getVersion() + 1);

        String sql = """
            UPDATE vehicle_shift_assignments SET
                vehicle_id = :vehicle_id,
                route_id = :route_id,
                shift_type = :shift_type,
                is_active = :is_active,
                assigned_by = :assigned_by,
                updated_at = :updated_at,
                version = :version
            WHERE id = :id AND version = :old_version
            RETURNING *
            """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("id", entity.getId().getValue())
                .bind("old_version", entity.getVersion());

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!entry.getKey().equals("id")) {
                spec = bindValue(spec, entry.getKey(), entry.getValue());
            }
        }

        return spec.map(getRowMapper())
                .one()
                .switchIfEmpty(Mono.defer(() -> {
                    String msg = String.format(
                            "Optimistic lock failure for VehicleShiftAssignment with id: %s",
                            entity.getId()
                    );
                    log.error(msg);
                    return Mono.error(new org.springframework.dao.OptimisticLockingFailureException(msg));
                }))
                .doOnSuccess(v -> log.debug("Updated shift assignment: {}", v.getId()));
    }

    @Override
    public Flux<VehicleShiftAssignment> findByVehicleId(VehicleId vehicleId) {
        String sql = "SELECT * FROM vehicle_shift_assignments WHERE vehicle_id = :vehicleId AND is_active = true";

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<VehicleShiftAssignment> findByRouteId(BusRouteId routeId) {
        String sql = "SELECT * FROM vehicle_shift_assignments WHERE route_id = :routeId AND is_active = true";

        return databaseClient.sql(sql)
                .bind("routeId", routeId.getValue())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<VehicleShiftAssignment> findByVehicleIdAndShiftType(VehicleId vehicleId, ShiftType shiftType) {
        String sql = """
            SELECT * FROM vehicle_shift_assignments
            WHERE vehicle_id = :vehicleId AND shift_type = :shiftType AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .bind("shiftType", shiftType.name())
                .map(getRowMapper())
                .one();
    }

    @Override
    public Flux<VehicleShiftAssignment> findByShiftType(ShiftType shiftType) {
        String sql = "SELECT * FROM vehicle_shift_assignments WHERE shift_type = :shiftType";

        return databaseClient.sql(sql)
                .bind("shiftType", shiftType.name())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<VehicleShiftAssignment> findActiveByShiftType(ShiftType shiftType) {
        String sql = """
            SELECT * FROM vehicle_shift_assignments
            WHERE shift_type = :shiftType AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("shiftType", shiftType.name())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<VehicleShiftAssignment> findAllActive() {
        String sql = "SELECT * FROM vehicle_shift_assignments WHERE is_active = true ORDER BY shift_type, created_at";

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Boolean> existsByVehicleIdAndShiftType(VehicleId vehicleId, ShiftType shiftType) {
        String sql = """
            SELECT COUNT(*) FROM vehicle_shift_assignments
            WHERE vehicle_id = :vehicleId AND shift_type = :shiftType AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .bind("shiftType", shiftType.name())
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    @Transactional
    public Mono<Void> deleteByVehicleIdAndShiftType(VehicleId vehicleId, ShiftType shiftType) {
        String sql = """
            DELETE FROM vehicle_shift_assignments
            WHERE vehicle_id = :vehicleId AND shift_type = :shiftType
            """;

        return databaseClient.sql(sql)
                .bind("vehicleId", vehicleId.getValue())
                .bind("shiftType", shiftType.name())
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> log.info("Deleted {} shift assignments for vehicle {} shift {}",
                        count, vehicleId, shiftType))
                .then();
    }

    @Override
    public Mono<Long> countByRouteId(BusRouteId routeId) {
        String sql = "SELECT COUNT(*) FROM vehicle_shift_assignments WHERE route_id = :routeId AND is_active = true";

        return databaseClient.sql(sql)
                .bind("routeId", routeId.getValue())
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Long> countByShiftType(ShiftType shiftType) {
        String sql = "SELECT COUNT(*) FROM vehicle_shift_assignments WHERE shift_type = :shiftType AND is_active = true";

        return databaseClient.sql(sql)
                .bind("shiftType", shiftType.name())
                .map(row -> row.get(0, Long.class))
                .one();
    }

    private VehicleShiftAssignment mapRowToEntity(Row row, RowMetadata metadata) {
        VehicleShiftAssignmentEntity entity = VehicleShiftAssignmentEntity.builder()
                .id(row.get("id", String.class))
                .vehicleId(row.get("vehicle_id", String.class))
                .routeId(row.get("route_id", String.class))
                .shiftType(row.get("shift_type", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .assignedBy(safeGet(row, "assigned_by", String.class, null))
                .createdAt(safeGet(row, "created_at", LocalDateTime.class, null))
                .updatedAt(safeGet(row, "updated_at", LocalDateTime.class, null))
                .version(safeGet(row, "version", Long.class, 0L))
                .build();

        return entityMapper.toDomain(entity);
    }

    private <T> T safeGet(Row row, String columnName, Class<T> type, T defaultValue) {
        try {
            T value = row.get(columnName, type);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
