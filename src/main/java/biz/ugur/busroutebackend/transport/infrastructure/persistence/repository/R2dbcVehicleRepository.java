package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISQueryBuilder;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.OptimisticLockMetricsRecorder;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.VehicleEntity;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.VehicleEntityMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@Repository
@Slf4j
@Transactional(readOnly = true)
public class R2dbcVehicleRepository extends BaseR2dbcRepository<Vehicle, VehicleId> implements VehicleRepository {

    private static final String ENTITY_TYPE = "vehicle";
    private static final int MAX_RETRIES = 3;
    private static final int BATCH_UPDATE_CONCURRENCY = 16;

    private final VehicleEntityMapper entityMapper;
    private final OptimisticLockMetricsRecorder metricsRecorder;

    public R2dbcVehicleRepository(DatabaseClient databaseClient,
                                   VehicleEntityMapper entityMapper,
                                   OptimisticLockMetricsRecorder metricsRecorder) {
        super(databaseClient, "vehicles", Vehicle.class);
        this.entityMapper = entityMapper;
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    protected String convertIdToDatabase(VehicleId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Vehicle> getRowMapper() {
        return this::mapRowToVehicle;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Vehicle entity) {
        VehicleEntity persistenceEntity = entityMapper.toEntity(entity);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", persistenceEntity.getId());
        columns.put("device_id", persistenceEntity.getDeviceId());
        columns.put("license_plate", persistenceEntity.getLicensePlate());
        columns.put("current_latitude", persistenceEntity.getCurrentLatitude());
        columns.put("current_longitude", persistenceEntity.getCurrentLongitude());
        columns.put("speed_kmh", persistenceEntity.getSpeedKmh());
        columns.put("is_in_motion", persistenceEntity.getIsInMotion());
        columns.put("last_position_update", persistenceEntity.getLastPositionUpdate());
        columns.put("assigned_route_id", persistenceEntity.getAssignedRouteId());
        columns.put("route_number", persistenceEntity.getRouteNumber());
        columns.put("is_active", persistenceEntity.getIsActive());
        columns.put("course", persistenceEntity.getCourse());
        columns.put("current_direction", persistenceEntity.getCurrentDirection());
        columns.put("last_stop_sequence", persistenceEntity.getLastStopSequence());
        columns.put("last_garage_id", persistenceEntity.getLastGarageId());
        columns.put("garage_entry_time", persistenceEntity.getGarageEntryTime());
        columns.put("garage_exit_time", persistenceEntity.getGarageExitTime());
        columns.put("is_in_garage", persistenceEntity.getIsInGarage());
        columns.put("route_source", persistenceEntity.getRouteSource());
        columns.put("route_confidence", persistenceEntity.getRouteConfidence());
        columns.put("gps_detection_enabled", persistenceEntity.getGpsDetectionEnabled());
        columns.put("gps_provider", persistenceEntity.getGpsProvider());
        return columns;
    }

    @Override
    protected Mono<Vehicle> insert(Vehicle entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("created_at", LocalDateTime.now());
        values.put("updated_at", LocalDateTime.now());
        values.put("version", 1L);

        String sql = """
            INSERT INTO vehicles (
                id, device_id, license_plate, current_latitude, current_longitude,
                speed_kmh, is_in_motion, last_position_update, assigned_route_id,
                route_number, is_active, created_at, updated_at, course,
                current_direction, last_stop_sequence, version,
                last_garage_id, garage_entry_time, garage_exit_time, is_in_garage,
                route_source, route_confidence, gps_detection_enabled, gps_provider
            ) VALUES (
                :id, :device_id, :license_plate, :current_latitude, :current_longitude,
                :speed_kmh, :is_in_motion, :last_position_update, :assigned_route_id,
                :route_number, :is_active, :created_at, :updated_at, :course,
                :current_direction, :last_stop_sequence, :version,
                :last_garage_id, :garage_entry_time, :garage_exit_time, :is_in_garage,
                :route_source, :route_confidence, :gps_detection_enabled, :gps_provider
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
                        new RuntimeException("Failed to insert " + entityClass.getSimpleName())
                ))
                .doOnSuccess(v -> log.info("Successfully inserted vehicle: {} with device ID: {}",
                        v.getLicensePlate(), v.getDeviceId()));
    }

    @Override
    protected Mono<Vehicle> update(Vehicle entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("updated_at", LocalDateTime.now());
        values.put("version", entity.getVersion() + 1);

        String sql = """
            UPDATE vehicles SET
                device_id = :device_id,
                license_plate = :license_plate,
                current_latitude = :current_latitude,
                current_longitude = :current_longitude,
                speed_kmh = :speed_kmh,
                is_in_motion = :is_in_motion,
                last_position_update = :last_position_update,
                assigned_route_id = :assigned_route_id,
                route_number = :route_number,
                is_active = :is_active,
                updated_at = :updated_at,
                course = :course,
                current_direction = :current_direction,
                last_stop_sequence = :last_stop_sequence,
                last_garage_id = :last_garage_id,
                garage_entry_time = :garage_entry_time,
                garage_exit_time = :garage_exit_time,
                is_in_garage = :is_in_garage,
                route_source = :route_source,
                route_confidence = :route_confidence,
                gps_detection_enabled = :gps_detection_enabled,
                gps_provider = :gps_provider,
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
                            "Optimistic lock failure for %s with id: %s. " +
                            "Entity was modified by another transaction (expected version: %d)",
                            entityClass.getSimpleName(),
                            entity.getId(),
                            entity.getVersion()
                    );
                    log.error(msg);
                    return Mono.error(new org.springframework.dao.OptimisticLockingFailureException(msg));
                }))
                .doOnSuccess(v -> log.debug("Successfully updated vehicle: {}", v.getLicensePlate()));
    }

    @Override
    public Mono<Vehicle> findByDeviceId(String deviceId) {
        String sql = "SELECT * FROM vehicles WHERE device_id = :deviceId";

        return databaseClient.sql(sql)
                .bind("deviceId", deviceId)
                .map(getRowMapper())
                .one()
                .doOnNext(v -> log.debug("Found vehicle by device ID: {}", deviceId));
    }

    @Override
    public Mono<Vehicle> findByLicensePlate(String licensePlate) {
        String sql = "SELECT * FROM vehicles WHERE license_plate = :licensePlate";

        return databaseClient.sql(sql)
                .bind("licensePlate", licensePlate)
                .map(getRowMapper())
                .one()
                .doOnNext(v -> log.debug("Found vehicle by license plate: {}", licensePlate));
    }

    @Override
    public Flux<Vehicle> findByAssignedRouteId(BusRouteId routeId) {
        String sql = "SELECT * FROM vehicles WHERE assigned_route_id = :routeId AND is_active = true";

        return databaseClient.sql(sql)
                .bind("routeId", routeId.getValue())
                .map(getRowMapper())
                .all()
                .doOnNext(v -> log.debug("Found vehicle by route ID {}: {}", routeId, v.getLicensePlate()));
    }

    @Override
    public Flux<Vehicle> findActiveVehicles() {
        String sql = "SELECT * FROM vehicles WHERE is_active = true ORDER BY last_position_update DESC";

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .doOnNext(v -> log.debug("Found active vehicle: {}", v.getLicensePlate()));
    }

    @Override
    public Flux<Vehicle> findByRouteNumber(String routeNumber) {
        String sql = "SELECT * FROM vehicles WHERE route_number = :routeNumber AND is_active = true";

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .map(getRowMapper())
                .all()
                .doOnNext(v -> log.debug("Found vehicle by route number {}: {}", routeNumber, v.getLicensePlate()));
    }

    @Override
    public Flux<Vehicle> findUnassignedVehicles() {
        String sql = "SELECT * FROM vehicles WHERE route_number IS NULL AND is_active = true";

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .doOnNext(v -> log.debug("Found unassigned vehicle: {}", v.getLicensePlate()));
    }

    @Override
    public Flux<Vehicle> findVehiclesInMotion() {
        // Filter by freshness: ignore vehicles whose last GPS update is older than 5 minutes
        // (e.g. frozen state during the 23:00–06:00 night window when GPS scheduler is paused).
        String sql = """
                SELECT * FROM vehicles
                WHERE is_in_motion = true
                  AND is_active = true
                  AND last_position_update IS NOT NULL
                  AND last_position_update > NOW() - INTERVAL '5 minutes'
                """;

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .doOnNext(v -> log.debug("Found vehicle in motion: {}", v.getLicensePlate()));
    }

    @Override
    public Flux<Vehicle> findVehiclesWithinRadius(Double centerLat, Double centerLon, Integer radiusMeters) {
        PostGISQueryBuilder.QueryFragment query = PostGISQueryBuilder.nearbyPointsQuery(
                "current_longitude", "current_latitude",
                ":centerLon", ":centerLat",
                ":radiusMeters",
                "distance_meters", "m"
        );

        String sql = String.format("""
            SELECT *,
                   %s
            FROM vehicles
            WHERE is_active = true
            AND current_latitude IS NOT NULL
            AND current_longitude IS NOT NULL
            AND %s
            ORDER BY %s
            """,
                query.distanceColumn(),
                query.withinRadiusCondition(),
                query.orderByClause()
        );

        return databaseClient.sql(sql)
                .bind("centerLat", centerLat)
                .bind("centerLon", centerLon)
                .bind("radiusMeters", radiusMeters)
                .map(getRowMapper())
                .all()
                .doOnNext(v -> log.debug("Found vehicle within radius: {}", v.getLicensePlate()));
    }

    @Override
    public Flux<Vehicle> findVehiclesWithRecentPosition() {
        String sql = """
            SELECT * FROM vehicles 
            WHERE is_active = true 
            AND last_position_update > NOW() - INTERVAL '5 minutes'
            ORDER BY last_position_update DESC
            """;

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .doOnNext(v -> log.debug("Found vehicle with recent position: {}", v.getLicensePlate()));
    }

    @Override
    public Mono<Boolean> existsByDeviceId(String deviceId) {
        String sql = "SELECT COUNT(*) FROM vehicles WHERE device_id = :deviceId";

        return databaseClient.sql(sql)
                .bind("deviceId", deviceId)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    public Mono<Boolean> existsByLicensePlate(String licensePlate) {
        String sql = "SELECT COUNT(*) FROM vehicles WHERE license_plate = :licensePlate";

        return databaseClient.sql(sql)
                .bind("licensePlate", licensePlate)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    public Mono<Long> countActiveVehicles() {
        String sql = "SELECT COUNT(*) FROM vehicles WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnNext(count -> log.debug("Active vehicles count: {}", count));
    }

    @Override
    public Mono<Long> countOnlineVehicles() {
        String sql = """
            SELECT COUNT(*) FROM vehicles
            WHERE is_active = true
            AND last_position_update > NOW() - INTERVAL '5 minutes'
            """;

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnNext(count -> log.debug("Online vehicles count: {}", count));
    }

    @Override
    public Mono<Long> countActiveVehiclesRouteNumber(String routeNumber) {
        String sql = "SELECT COUNT(*) FROM vehicles WHERE is_active = true AND route_number = :routeNumber";

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnNext(count -> log.debug("Active vehicles count for route {}: {}", routeNumber, count));
    }

    @Override
    public Flux<String> findAllDeviceIds() {
        String sql = "SELECT device_id FROM vehicles WHERE device_id IS NOT NULL AND is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get("device_id", String.class))
                .all()
                .doOnComplete(() -> log.debug("Successfully fetched all device IDs"));
    }

    private Vehicle mapRowToVehicle(Row row, RowMetadata metadata) {
        VehicleEntity entity = VehicleEntity.builder()
                .id(row.get("id", String.class))
                .deviceId(row.get("device_id", String.class))
                .licensePlate(row.get("license_plate", String.class))
                .currentLatitude(row.get("current_latitude", Double.class))
                .currentLongitude(row.get("current_longitude", Double.class))
                .speedKmh(row.get("speed_kmh", Double.class))
                .isInMotion(row.get("is_in_motion", Boolean.class))
                .lastPositionUpdate(row.get("last_position_update", LocalDateTime.class))
                .assignedRouteId(row.get("assigned_route_id", String.class))
                .routeNumber(row.get("route_number", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .course(row.get("course", Double.class))
                .currentDirection(safeGet(row, "current_direction", Integer.class, null))
                .lastStopSequence(safeGet(row, "last_stop_sequence", Integer.class, null))
                .lastGarageId(safeGet(row, "last_garage_id", String.class, null))
                .garageEntryTime(safeGet(row, "garage_entry_time", LocalDateTime.class, null))
                .garageExitTime(safeGet(row, "garage_exit_time", LocalDateTime.class, null))
                .isInGarage(safeGet(row, "is_in_garage", Boolean.class, false))
                .routeSource(safeGet(row, "route_source", String.class, null))
                .routeConfidence(safeGet(row, "route_confidence", Integer.class, 0))
                .gpsDetectionEnabled(safeGet(row, "gps_detection_enabled", Boolean.class, true))
                .gpsProvider(safeGet(row, "gps_provider", String.class, "CHINA"))
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
            log.debug("Column '{}' not found, using default value: {}", columnName, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public Mono<Map<String, Vehicle>> findByDeviceIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        List<String> validDeviceIds = deviceIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        if (validDeviceIds.isEmpty()) {
            log.warn("No valid device IDs found after filtering from {} original IDs", deviceIds.size());
            return Mono.just(Map.of());
        }

        List<String> namedParams = new ArrayList<>();
        for (int i = 0; i < validDeviceIds.size(); i++) {
            namedParams.add(":deviceId" + i);
        }
        String placeholders = String.join(",", namedParams);

        String sql = "SELECT * FROM vehicles WHERE device_id IN (" + placeholders + ")";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int i = 0; i < validDeviceIds.size(); i++) {
            spec = spec.bind("deviceId" + i, validDeviceIds.get(i));
        }

        return spec.map(getRowMapper())
                .all()
                .collectMap(Vehicle::getDeviceId)
                .doOnSuccess(map -> log.debug("Found {} vehicles out of {} device IDs", map.size(), validDeviceIds.size()))
                .doOnError(error -> log.error("Failed to find vehicles by device IDs: requested={}, error={}",
                        validDeviceIds.size(), error.getMessage(), error));
    }

    @Override
    @Transactional
    public Mono<Integer> batchUpdate(List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return Mono.just(0);
        }

        return Flux.fromIterable(vehicles)
                .flatMap(this::updateWithRetry, BATCH_UPDATE_CONCURRENCY)
                .reduce(BatchUpdateResult.empty(), BatchUpdateResult::merge)
                .doOnSuccess(result -> {
                    if (result.conflictCount() > 0) {
                        log.warn("Batch update completed: updated={}, conflicts={} (data may be stale for {} vehicles)",
                                result.successCount(), result.conflictCount(), result.conflictCount());
                    } else {
                        log.info("Batch updated {} vehicles successfully", result.successCount());
                    }
                })
                .map(BatchUpdateResult::successCount);
    }

    @Override
    @Transactional
    public Mono<Integer> batchUpdateDirections(Map<String, Integer> vehicleIdToDirection) {
        if (vehicleIdToDirection == null || vehicleIdToDirection.isEmpty()) {
            return Mono.just(0);
        }

        String sql = "UPDATE vehicles SET current_direction = :dir, updated_at = NOW() WHERE id = :id";

        return Flux.fromIterable(vehicleIdToDirection.entrySet())
                .flatMap(entry -> databaseClient.sql(sql)
                        .bind("dir", entry.getValue())
                        .bind("id", entry.getKey())
                        .fetch()
                        .rowsUpdated()
                        .map(Long::intValue))
                .reduce(0, Integer::sum)
                .doOnNext(n -> log.debug("[GPS_PIPELINE] DIR_AUTO_FIX updated direction for {} vehicles", n));
    }

    private Mono<BatchUpdateResult> updateWithRetry(Vehicle vehicle) {
        // Optimistic lock conflicts cannot be resolved by retrying with the same
        // vehicle object (version is stale), so we skip the retry and treat any
        // optimistic-lock failure — direct or wrapped in RetryExhaustedException — as
        // a harmless conflict: the concurrent writer already persisted a newer position.
        return updateSingleVehicle(vehicle)
                .map(rowsUpdated -> BatchUpdateResult.success())
                .onErrorResume(error -> {
                    Throwable root = error.getCause() != null ? error.getCause() : error;
                    if (root instanceof org.springframework.dao.OptimisticLockingFailureException) {
                        log.debug("Optimistic lock conflict for vehicle {} (version {}), skipping — concurrent update won",
                                vehicle.getId(), vehicle.getVersion());
                        metricsRecorder.recordFailureAfterRetries(ENTITY_TYPE, vehicle.getId().getValue(), 0);
                        return Mono.just(BatchUpdateResult.conflict());
                    }
                    return Mono.error(error);
                });
    }

    private record BatchUpdateResult(int successCount, int conflictCount) {

        static BatchUpdateResult empty() {
            return new BatchUpdateResult(0, 0);
        }

        static BatchUpdateResult success() {
            return new BatchUpdateResult(1, 0);
        }

        static BatchUpdateResult conflict() {
            return new BatchUpdateResult(0, 1);
        }

        static BatchUpdateResult merge(BatchUpdateResult r1, BatchUpdateResult r2) {
            return new BatchUpdateResult(
                    r1.successCount() + r2.successCount(),
                    r1.conflictCount() + r2.conflictCount()
            );
        }
    }

    private Mono<Integer> updateSingleVehicle(Vehicle vehicle) {
        String sql = """
            UPDATE vehicles SET
                current_latitude = :latitude,
                current_longitude = :longitude,
                speed_kmh = :speed,
                is_in_motion = :inMotion,
                last_position_update = :lastUpdate,
                course = :course,
                current_direction = :currentDirection,
                last_stop_sequence = :lastStopSequence,
                updated_at = :updatedAt,
                version = version + 1
            WHERE id = :id AND version = :version
            """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);

        spec = bindValue(spec, "latitude", vehicle.getCurrentLatitude());
        spec = bindValue(spec, "longitude", vehicle.getCurrentLongitude());
        spec = bindValue(spec, "speed", vehicle.getSpeedKmh());
        spec = bindValue(spec, "inMotion", vehicle.getIsInMotion());
        spec = bindValue(spec, "lastUpdate", vehicle.getLastPositionUpdate());
        spec = bindValue(spec, "course", vehicle.getCourse());
        spec = bindValue(spec, "currentDirection", vehicle.getCurrentDirection());
        spec = bindValue(spec, "lastStopSequence", vehicle.getLastStopSequence());
        spec = bindValue(spec, "updatedAt", LocalDateTime.now());
        spec = spec.bind("id", vehicle.getId().getValue());
        spec = spec.bind("version", vehicle.getVersion());

        return spec.fetch()
                .rowsUpdated()
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Mono.error(new org.springframework.dao.OptimisticLockingFailureException(
                                String.format("Optimistic lock failure for Vehicle with id: %s (expected version: %d)",
                                        vehicle.getId(), vehicle.getVersion())));
                    }
                    return Mono.just(rowsUpdated.intValue());
                });
    }

    @Override
    @Transactional
    public Flux<Vehicle> batchInsert(List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return Flux.empty();
        }

        String sql = """
            INSERT INTO vehicles (
                id, device_id, license_plate, current_latitude, current_longitude,
                speed_kmh, is_in_motion, last_position_update, assigned_route_id,
                route_number, is_active, course, current_direction, last_stop_sequence,
                created_at, updated_at, version,
                last_garage_id, garage_entry_time, garage_exit_time, is_in_garage,
                route_source, route_confidence, gps_detection_enabled, gps_provider
            ) VALUES (
                :id, :device_id, :license_plate, :current_latitude, :current_longitude,
                :speed_kmh, :is_in_motion, :last_position_update, :assigned_route_id,
                :route_number, :is_active, :course, :current_direction, :last_stop_sequence,
                :created_at, :updated_at, :version,
                :last_garage_id, :garage_entry_time, :garage_exit_time, :is_in_garage,
                :route_source, :route_confidence, :gps_detection_enabled, :gps_provider
            ) RETURNING *
            """;

        return Flux.fromIterable(vehicles)
                .flatMap(vehicle -> {
                    Map<String, Object> values = mapEntityToColumns(vehicle);
                    values.put("created_at", LocalDateTime.now());
                    values.put("updated_at", LocalDateTime.now());
                    values.put("version", 1L);

                    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
                    for (Map.Entry<String, Object> entry : values.entrySet()) {
                        spec = bindValue(spec, entry.getKey(), entry.getValue());
                    }

                    return spec.map(getRowMapper()).one();
                }, BATCH_UPDATE_CONCURRENCY)
                .doOnComplete(() -> log.info("Batch inserted {} vehicles", vehicles.size()));
    }

    @Override
    public Flux<Vehicle> findBySpecification(Specification<Vehicle> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT * FROM vehicles WHERE %s ORDER BY last_position_update DESC, created_at DESC",
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
    public Flux<Vehicle> findBySpecification(Specification<Vehicle> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT * FROM vehicles WHERE %s %s LIMIT :limit OFFSET :offset",
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

    @Override
    public Mono<Long> countBySpecification(Specification<Vehicle> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT COUNT(*) FROM vehicles WHERE %s",
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

    @Override
    @Transactional
    public Mono<Long> clearAllRouteAssignments() {
        String sql = """
            UPDATE vehicles
            SET route_number = NULL,
                assigned_route_id = NULL,
                updated_at = :updatedAt
            WHERE is_active = true
            AND (route_number IS NOT NULL OR assigned_route_id IS NOT NULL)
            """;

        return databaseClient.sql(sql)
                .bind("updatedAt", LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> log.info("Cleared route assignments for {} vehicles", count));
    }

    @Override
    @Transactional
    public Mono<Long> clearRouteAssignmentsExcluding(List<VehicleId> excludeVehicleIds) {
        if (excludeVehicleIds == null || excludeVehicleIds.isEmpty()) {
            return clearAllRouteAssignments();
        }

        List<String> excludeIds = excludeVehicleIds.stream()
                .map(VehicleId::getValue)
                .toList();

        List<String> namedParams = new ArrayList<>();
        for (int i = 0; i < excludeIds.size(); i++) {
            namedParams.add(":excludeId" + i);
        }
        String placeholders = String.join(",", namedParams);

        String sql = String.format("""
            UPDATE vehicles
            SET route_number = NULL,
                assigned_route_id = NULL,
                route_source = NULL,
                route_confidence = 0,
                updated_at = :updatedAt
            WHERE is_active = true
            AND (route_number IS NOT NULL OR assigned_route_id IS NOT NULL)
            AND id NOT IN (%s)
            """, placeholders);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("updatedAt", LocalDateTime.now());

        for (int i = 0; i < excludeIds.size(); i++) {
            spec = spec.bind("excludeId" + i, excludeIds.get(i));
        }

        return spec.fetch()
                .rowsUpdated()
                .doOnSuccess(count -> log.info("Cleared route assignments for {} vehicles (excluded {} with immediate assignments)",
                        count, excludeIds.size()));
    }

    @Override
    public Mono<Map<String, Vehicle>> findAllWithRouteAssignment() {
        String sql = """
            SELECT * FROM vehicles
            WHERE is_active = true
            AND route_number IS NOT NULL
            """;

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .collectMap(Vehicle::getLicensePlate)
                .doOnSuccess(map -> log.debug("Found {} vehicles with route assignments", map.size()));
    }

    @Override
    @Transactional
    public Mono<Integer> updateRouteAssignment(String licensePlate, BusRouteId routeId, String routeNumber) {
        String sql = """
            UPDATE vehicles
            SET route_number = :routeNumber,
                assigned_route_id = :routeId,
                updated_at = :updatedAt
            WHERE license_plate = :licensePlate
            AND is_active = true
            """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("licensePlate", licensePlate)
                .bind("updatedAt", LocalDateTime.now());

        spec = bindValue(spec, "routeNumber", routeNumber);
        spec = bindValue(spec, "routeId", routeId != null ? routeId.getValue() : null);

        return spec.fetch()
                .rowsUpdated()
                .map(Long::intValue)
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.debug("Updated route assignment for vehicle {}: route={}", licensePlate, routeNumber);
                    } else {
                        log.warn("Vehicle not found for route assignment update: {}", licensePlate);
                    }
                });
    }

    @Override
    @Transactional
    public Mono<Integer> clearRouteAssignmentsByLicensePlates(List<String> licensePlates) {
        if (licensePlates == null || licensePlates.isEmpty()) {
            return Mono.just(0);
        }

        List<String> namedParams = new ArrayList<>();
        for (int i = 0; i < licensePlates.size(); i++) {
            namedParams.add(":plate" + i);
        }
        String placeholders = String.join(",", namedParams);

        String sql = String.format("""
            UPDATE vehicles
            SET route_number = NULL,
                assigned_route_id = NULL,
                updated_at = :updatedAt
            WHERE license_plate IN (%s)
            AND is_active = true
            """, placeholders);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("updatedAt", LocalDateTime.now());

        for (int i = 0; i < licensePlates.size(); i++) {
            spec = spec.bind("plate" + i, licensePlates.get(i));
        }

        return spec.fetch()
                .rowsUpdated()
                .map(Long::intValue)
                .doOnSuccess(count -> log.info("Cleared route assignments for {} vehicles (requested: {})",
                        count, licensePlates.size()));
    }

    @Override
    public Mono<Map<GpsProviderType, List<String>>> findAllDeviceIdsGroupedByProvider() {
        String sql = """
            SELECT device_id, gps_provider
            FROM vehicles
            WHERE device_id IS NOT NULL AND is_active = true
            """;

        return databaseClient.sql(sql)
                .map((row, metadata) -> Map.entry(
                        GpsProviderType.fromCodeOrDefault(row.get("gps_provider", String.class)),
                        row.get("device_id", String.class)
                ))
                .all()
                .collectMultimap(Map.Entry::getKey, Map.Entry::getValue)
                .map(multimap -> {
                    Map<GpsProviderType, List<String>> result = new HashMap<>();
                    multimap.forEach((key, values) -> result.put(key, new ArrayList<>(values)));
                    return result;
                })
                .doOnSuccess(map -> log.debug("Found device IDs grouped by provider: {}",
                        map.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue().size())
                                .collect(java.util.stream.Collectors.joining(", "))));
    }

    @Override
    public Flux<String> findDeviceIdsByProvider(GpsProviderType providerType) {
        String sql = """
            SELECT device_id
            FROM vehicles
            WHERE device_id IS NOT NULL
            AND is_active = true
            AND gps_provider = :provider
            """;

        return databaseClient.sql(sql)
                .bind("provider", providerType.getCode())
                .map(row -> row.get("device_id", String.class))
                .all()
                .doOnComplete(() -> log.debug("Found device IDs for provider {}", providerType));
    }

    @Override
    @Transactional
    public Mono<Integer> updateGpsProvider(VehicleId vehicleId, GpsProviderType providerType) {
        String sql = """
            UPDATE vehicles
            SET gps_provider = :provider,
                updated_at = :updatedAt
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", vehicleId.getValue())
                .bind("provider", providerType.getCode())
                .bind("updatedAt", LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .map(Long::intValue)
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Updated GPS provider for vehicle {} to {}", vehicleId, providerType);
                    } else {
                        log.warn("Vehicle not found for GPS provider update: {}", vehicleId);
                    }
                });
    }
}