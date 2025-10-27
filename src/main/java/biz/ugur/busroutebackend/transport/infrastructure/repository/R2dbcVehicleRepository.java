package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISQueryBuilder;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@Repository
@Slf4j
@Transactional(readOnly = true)
public class R2dbcVehicleRepository extends BaseR2dbcRepository<Vehicle, VehicleId> implements VehicleRepository {

    public R2dbcVehicleRepository(DatabaseClient databaseClient) {
        super(databaseClient, "vehicles", Vehicle.class);
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
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId().getValue());
        columns.put("device_id", entity.getDeviceId());
        columns.put("license_plate", entity.getLicensePlate());
        columns.put("current_latitude", entity.getCurrentLatitude());
        columns.put("current_longitude", entity.getCurrentLongitude());
        columns.put("speed_kmh", entity.getSpeedKmh());
        columns.put("is_in_motion", entity.getIsInMotion());
        columns.put("last_position_update", entity.getLastPositionUpdate());
        columns.put("assigned_route_id", entity.getAssignedRouteId() != null ?
                entity.getAssignedRouteId().getValue() : null);
        columns.put("route_number", entity.getRouteNumber());
        columns.put("is_active", entity.getIsActive());
        columns.put("course", entity.getCourse());
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
                route_number, is_active, created_at, updated_at, course version
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

    private Vehicle mapRowToVehicle(Row row, RowMetadata metadata) {
        Vehicle vehicle = new Vehicle(
                VehicleId.of(row.get("id", String.class)),
                row.get("device_id", String.class),
                row.get("license_plate", String.class),
                row.get("current_latitude", Double.class),
                row.get("current_longitude", Double.class),
                row.get("speed_kmh", Double.class),
                row.get("is_in_motion", Boolean.class),
                row.get("last_position_update", LocalDateTime.class),
                Optional.ofNullable(row.get("assigned_route_id", String.class)).map(BusRouteId::of).orElse(null),
                row.get("route_number", String.class),
                row.get("is_active", Boolean.class),
                row.get("course", Double.class)
        );

        vehicle.setCreatedAt(safeGet(row, "created_at", LocalDateTime.class, null));
        vehicle.setUpdatedAt(safeGet(row, "updated_at", LocalDateTime.class, null));
        vehicle.setVersion(safeGet(row, "version", Long.class, 0L));

        return vehicle;
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

        String placeholders = String.join(",", deviceIds.stream()
                .map(id -> "?")
                .toList());

        String sql = "SELECT * FROM vehicles WHERE device_id IN (" + placeholders + ")";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int i = 0; i < deviceIds.size(); i++) {
            spec = spec.bind(i, deviceIds.get(i));
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
                    return databaseClient.sql(sql)
                            .bind("latitude", vehicle.getCurrentLatitude())
                            .bind("longitude", vehicle.getCurrentLongitude())
                            .bind("speed", vehicle.getSpeedKmh())
                            .bind("inMotion", vehicle.getIsInMotion())
                            .bind("lastUpdate", vehicle.getLastPositionUpdate())
                            .bind("course", vehicle.getCourse())
                            .bind("updatedAt", LocalDateTime.now())
                            .bind("id", vehicle.getId().getValue())
                            .bind("version", vehicle.getVersion())
                            .fetch()
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
}