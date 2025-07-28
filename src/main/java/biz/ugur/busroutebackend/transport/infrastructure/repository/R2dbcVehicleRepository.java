package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

@Repository
@Slf4j
public class R2dbcVehicleRepository implements VehicleRepository {

    private final DatabaseClient databaseClient;

    public R2dbcVehicleRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Vehicle> save(Vehicle vehicle) {
        if (vehicle.getId() == null) {
            return insert(vehicle);
        } else {
            return update(vehicle);
        }
    }

    private Mono<Vehicle> insert(Vehicle vehicle) {
        String sql = """
            INSERT INTO vehicles (id, device_id, license_plate, current_latitude, current_longitude,
                                 speed_kmh, is_in_motion, last_position_update, assigned_route_id,
                                 route_number, is_active, created_at, updated_at, version)
            VALUES (:id, :deviceId, :licensePlate, :currentLatitude, :currentLongitude,
                   :speedKmh, :isInMotion, :lastPositionUpdate, :assignedRouteId,
                   :routeNumber, :isActive, :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();
        DatabaseClient.GenericExecuteSpec spec  = databaseClient.sql(sql);
        spec.bind("id", vehicle.getId().getValue())
                .bind("deviceId", vehicle.getDeviceId())
                .bind("licensePlate", vehicle.getLicensePlate())
                .bind("currentLatitude", vehicle.getCurrentLatitude())
                .bind("currentLongitude", vehicle.getCurrentLongitude())
                .bind("speedKmh", vehicle.getSpeedKmh())
                .bind("isInMotion", vehicle.getIsInMotion())
                .bind("lastPositionUpdate", vehicle.getLastPositionUpdate())
                .bind("routeNumber", vehicle.getRouteNumber());

        if (vehicle.getAssignedRouteId() != null) {
            spec = spec.bind("assignedRouteId", vehicle.getAssignedRouteId().getValue());
        } else {
            spec = spec.bindNull("assignedRouteId", String.class);
        }

        return spec
                .bind("isActive", vehicle.getIsActive())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 0L)
                .then()
                .thenReturn(vehicle)
                .doOnSuccess(v -> log.debug("Inserted vehicle: {}", v.getLicensePlate()));
    }


    private Mono<Vehicle> update(Vehicle vehicle) {
        String sql = """
            UPDATE vehicles 
            SET device_id = :deviceId, license_plate = :licensePlate,
                current_latitude = :currentLatitude, current_longitude = :currentLongitude,
                speed_kmh = :speedKmh, is_in_motion = :isInMotion,
                last_position_update = :lastPositionUpdate, assigned_route_id = :assignedRouteId,
                route_number = :routeNumber, is_active = :isActive, 
                updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("id", vehicle.getId().getValue())
                .bind("deviceId", vehicle.getDeviceId())
                .bind("licensePlate", vehicle.getLicensePlate())
                .bind("currentLatitude", vehicle.getCurrentLatitude())
                .bind("currentLongitude", vehicle.getCurrentLongitude())
                .bind("speedKmh", vehicle.getSpeedKmh())
                .bind("isInMotion", vehicle.getIsInMotion())
                .bind("lastPositionUpdate", vehicle.getLastPositionUpdate());

        if (vehicle.getAssignedRouteId() != null) {
            spec = spec.bind("assignedRouteId", vehicle.getAssignedRouteId().getValue());
        } else {
            spec = spec.bindNull("assignedRouteId", String.class);
        }


        return   spec.bind("routeNumber", vehicle.getRouteNumber())
                .bind("isActive", vehicle.getIsActive())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(vehicle);
    }

    @Override
    public Flux<Vehicle> findByRouteNumber(String routeNumber) {
        String sql = "SELECT * FROM vehicles WHERE cached_route_number = :routeNumber AND is_active = true";

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .map(this::mapRowToVehicle)
                .all()
                .doOnNext(v -> log.debug("Found vehicle by route number {}: {}", routeNumber, v.getLicensePlate()));
    }

    @Override
    public Flux<Vehicle> findUnassignedVehicles() {
        String sql = "SELECT * FROM vehicles WHERE cached_route_number IS NULL AND is_active = true";

        return databaseClient.sql(sql)
                .map(this::mapRowToVehicle)
                .all()
                .doOnNext(v -> log.debug("Found unassigned vehicle: {}", v.getLicensePlate()));
    }



    @Override
    public Mono<Vehicle> findById(VehicleId vehicleId) {
        String sql = "SELECT * FROM vehicles WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", vehicleId.getValue())
                .map(this::mapRowToVehicle)
                .one()
                .doOnNext(v -> log.debug("Found vehicle by ID: {}", vehicleId.getValue()));
    }

    @Override
    public Mono<Vehicle> findByDeviceId(String deviceId) {
        String sql = "SELECT * FROM vehicles WHERE device_id = :deviceId";

        return databaseClient.sql(sql)
                .bind("deviceId", deviceId)
                .map(this::mapRowToVehicle)
                .one()
                .doOnNext(v -> log.debug("Found vehicle by device ID: {}", deviceId));
    }

    @Override
    public Mono<Vehicle> findByLicensePlate(String licensePlate) {
        String sql = "SELECT * FROM vehicles WHERE license_plate = :licensePlate";

        return databaseClient.sql(sql)
                .bind("licensePlate", licensePlate)
                .map(this::mapRowToVehicle)
                .one()
                .doOnNext(v -> log.debug("Found vehicle by plate: {}", licensePlate));
    }

    @Override
    public Flux<Vehicle> findByAssignedRouteId(BusRouteId routeId) {
        String sql = "SELECT * FROM vehicles WHERE assigned_route_id = :routeId AND is_active = true";

        return databaseClient.sql(sql)
                .bind("routeId", routeId.getValue())
                .map(this::mapRowToVehicle)
                .all()
                .doOnComplete(() -> log.debug("Found vehicles for route: {}", routeId.getValue()));
    }

    @Override
    public Flux<Vehicle> findActiveVehicles() {
        String sql = "SELECT * FROM vehicles WHERE is_active = true ORDER BY license_plate";

        return databaseClient.sql(sql)
                .map(this::mapRowToVehicle)
                .all()
                .doOnComplete(() -> log.debug("Found all active vehicles"));
    }

    @Override
    public Flux<Vehicle> findVehiclesInMotion() {
        String sql = """
            SELECT * FROM vehicles 
            WHERE is_active = true AND is_in_motion = true 
            AND last_position_update > :cutoffTime
            ORDER BY last_position_update DESC
            """;

        Instant cutoffTime = Instant.now().minusSeconds(300); // 5 минут назад

        return databaseClient.sql(sql)
                .bind("cutoffTime", cutoffTime)
                .map(this::mapRowToVehicle)
                .all()
                .doOnComplete(() -> log.debug("Found vehicles in motion"));
    }

    @Override
    public Flux<Vehicle> findVehiclesWithinRadius(Double centerLat, Double centerLon, Integer radiusMeters) {
        String sql = """
            SELECT *, 
                   (6371000 * acos(cos(radians(:centerLat)) * cos(radians(current_latitude)) 
                   * cos(radians(current_longitude) - radians(:centerLon)) 
                   + sin(radians(:centerLat)) * sin(radians(current_latitude)))) as distance
            FROM vehicles 
            WHERE is_active = true 
            AND current_latitude IS NOT NULL 
            AND current_longitude IS NOT NULL
            AND (6371000 * acos(cos(radians(:centerLat)) * cos(radians(current_latitude)) 
                * cos(radians(current_longitude) - radians(:centerLon)) 
                + sin(radians(:centerLat)) * sin(radians(current_latitude)))) <= :radiusMeters
            ORDER BY distance
            """;

        return databaseClient.sql(sql)
                .bind("centerLat", centerLat)
                .bind("centerLon", centerLon)
                .bind("radiusMeters", radiusMeters)
                .map(this::mapRowToVehicle)
                .all()
                .doOnComplete(() -> log.debug("Found vehicles within {} meters of ({}, {})",
                        radiusMeters, centerLat, centerLon));
    }

    @Override
    public Flux<Vehicle> findVehiclesWithRecentPosition() {
        String sql = """
            SELECT * FROM vehicles 
            WHERE is_active = true 
            AND last_position_update > :cutoffTime
            ORDER BY last_position_update DESC
            """;

        Instant cutoffTime = Instant.now().minusSeconds(300);

        return databaseClient.sql(sql)
                .bind("cutoffTime", cutoffTime)
                .map(this::mapRowToVehicle)
                .all()
                .doOnComplete(() -> log.debug("Found vehicles with recent position"));
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
    public Mono<Void> deleteById(VehicleId vehicleId) {
        String sql = "DELETE FROM vehicles WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", vehicleId.getValue())
                .then()
                .doOnSuccess(v -> log.debug("Deleted vehicle: {}", vehicleId.getValue()));
    }

    @Override
    public Mono<Long> countActiveVehicles() {
        String sql = "SELECT COUNT(*) FROM vehicles WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnNext(count -> log.debug("Active vehicles count: {}", count));
    }

    private Vehicle mapRowToVehicle(Row row, RowMetadata metadata) {
        return new Vehicle(
                VehicleId.of(row.get("id", String.class)),
                row.get("device_id", String.class),
                row.get("license_plate", String.class),
                row.get("current_latitude", Double.class),
                row.get("current_longitude", Double.class),
                row.get("speed_kmh", Double.class),
                row.get("is_in_motion", Boolean.class),
                row.get("last_position_update", Instant.class),
                Optional.ofNullable(row.get("assigned_route_id", String.class))
                        .map(BusRouteId::of)
                        .orElse(null),
                row.get("route_number", String.class),
                row.get("is_active", Boolean.class)
        );
    }
}