package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISQueryBuilder;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
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

    private final VehicleEntityMapper entityMapper;

    public R2dbcVehicleRepository(DatabaseClient databaseClient, VehicleEntityMapper entityMapper) {
        super(databaseClient, "vehicles", Vehicle.class);
        this.entityMapper = entityMapper;
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
                route_number, is_active, created_at, updated_at, course, version
            ) VALUES (
                :id, :device_id, :license_plate, :current_latitude, :current_longitude,
                :speed_kmh, :is_in_motion, :last_position_update, :assigned_route_id,
                :route_number, :is_active, :created_at, :updated_at, :course, :version
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
        String sql = "SELECT * FROM vehicles WHERE is_in_motion = true AND is_active = true";

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all()
                .doOnNext(v -> log.debug("Found vehicle in motion: {}", v.getLicensePlate()));
    }

    @Override
    public Flux<Vehicle> findVehiclesWithinRadius(Double centerLat, Double centerLon, Integer radiusMeters) {
        // Generate PostGIS query fragments using centralized utility
        // NOTE: Migrated from Web Mercator (EPSG:3857) to geography for better accuracy
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

        // Use named parameters instead of positional ones
        List<String> namedParams = new ArrayList<>();
        for (int i = 0; i < deviceIds.size(); i++) {
            namedParams.add(":deviceId" + i);
        }
        String placeholders = String.join(",", namedParams);

        String sql = "SELECT * FROM vehicles WHERE device_id IN (" + placeholders + ")";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int i = 0; i < deviceIds.size(); i++) {
            spec = spec.bind("deviceId" + i, deviceIds.get(i));
        }

        return spec.map(getRowMapper())
                .all()
                .collectMap(Vehicle::getDeviceId)
                .doOnSuccess(map -> log.debug("Found {} vehicles by device IDs", map.size()));
    }

    @Override
    @Transactional
    public Mono<Integer> batchUpdate(List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return Mono.just(0);
        }

        String sql = """
            UPDATE vehicles SET
                current_latitude = :latitude,
                current_longitude = :longitude,
                speed_kmh = :speed,
                is_in_motion = :inMotion,
                last_position_update = :lastUpdate,
                course = :course,
                updated_at = :updatedAt,
                version = version + 1
            WHERE id = :id AND version = :version
            """;

        return Flux.fromIterable(vehicles)
                .flatMap(vehicle -> {
                    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);

                    // Use bindValue to properly handle null values
                    spec = bindValue(spec, "latitude", vehicle.getCurrentLatitude());
                    spec = bindValue(spec, "longitude", vehicle.getCurrentLongitude());
                    spec = bindValue(spec, "speed", vehicle.getSpeedKmh());
                    spec = bindValue(spec, "inMotion", vehicle.getIsInMotion());
                    spec = bindValue(spec, "lastUpdate", vehicle.getLastPositionUpdate());
                    spec = bindValue(spec, "course", vehicle.getCourse());
                    spec = bindValue(spec, "updatedAt", LocalDateTime.now());
                    spec = spec.bind("id", vehicle.getId().getValue());
                    spec = spec.bind("version", vehicle.getVersion());

                    return spec.fetch()
                            .rowsUpdated()
                            .map(Long::intValue);
                })
                .reduce(0, Integer::sum)
                .doOnSuccess(count -> log.info("Batch updated {} vehicles", count));
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
                route_number, is_active, course, created_at, updated_at, version
            ) VALUES (
                :id, :device_id, :license_plate, :current_latitude, :current_longitude,
                :speed_kmh, :is_in_motion, :last_position_update, :assigned_route_id,
                :route_number, :is_active, :course, :created_at, :updated_at, :version
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
                })
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
}