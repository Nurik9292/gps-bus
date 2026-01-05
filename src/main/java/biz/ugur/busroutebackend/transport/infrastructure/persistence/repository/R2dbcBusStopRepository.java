package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISQueryBuilder;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.BusStopEntity;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.BusStopEntityMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
public class R2dbcBusStopRepository extends BaseR2dbcRepository<BusStop, BusStopId>
        implements BusStopRepository {

    private final BusStopEntityMapper entityMapper;
    private final ETAProperties etaProperties;

    public R2dbcBusStopRepository(DatabaseClient databaseClient,
                                   BusStopEntityMapper entityMapper,
                                   ETAProperties etaProperties) {
        super(databaseClient, "bus_stops", BusStop.class);
        this.entityMapper = entityMapper;
        this.etaProperties = etaProperties;
    }

    @Override
    protected String convertIdToDatabase(BusStopId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, BusStop> getRowMapper() {
        return this::mapRowToBusStop;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(BusStop entity) {
        BusStopEntity persistenceEntity = entityMapper.toEntity(entity);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", persistenceEntity.getId());
        columns.put("stop_name", persistenceEntity.getStopName());
        columns.put("name_en", persistenceEntity.getNameEn());
        columns.put("name_tm", persistenceEntity.getNameTm());
        columns.put("stop_code", persistenceEntity.getStopCode());
        columns.put("latitude", persistenceEntity.getLatitude());
        columns.put("longitude", persistenceEntity.getLongitude());
        columns.put("is_active", persistenceEntity.getIsActive());
        columns.put("is_major_stop", persistenceEntity.getIsMajorStop());
        columns.put("city_id", persistenceEntity.getCityId());
        return columns;
    }

    @Override
    public Flux<BusStop> findByStopName(String stopName) {
        String sql = """
            SELECT * FROM bus_stops 
            WHERE (stop_name ILIKE :stopName 
               OR name_en ILIKE :stopName 
               OR name_tm ILIKE :stopName) 
            AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("stopName", "%" + stopName + "%")
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<BusStop> findStopsWithinRadius(Double centerLat, Double centerLon, Double radiusKm) {
        log.debug("Searching for stops within {}km of ({}, {})", radiusKm, centerLat, centerLon);

        String sql = """
            SELECT *,
                ST_Distance(
                    ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
                    ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography
                ) as distance_meters
            FROM bus_stops
            WHERE is_active = true
            AND ST_DWithin(
                ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
                ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography,
                :radiusMeters
            )
            ORDER BY distance_meters
            LIMIT 15
            """;

        return databaseClient.sql(sql)
                .bind("centerLat", centerLat)
                .bind("centerLon", centerLon)
                .bind("radiusMeters", radiusKm * 1000.0)
                .map(getRowMapper())
                .all()
                .doOnNext(stop -> log.debug("Found stop: {} at coordinates ({}, {})",
                        stop.getStopName(), stop.getLatitude(), stop.getLongitude()))
                .doOnComplete(() -> log.info("Completed search for stops within {}km of ({}, {})",
                        radiusKm, centerLat, centerLon));
    }

    @Override
    public Flux<BusStop> findByRouteId(String routeId) {
        String sql = """
            SELECT bs.* FROM bus_stops bs
            JOIN route_stops rs ON bs.id = rs.stop_id
            WHERE rs.route_id = :routeId AND bs.is_active = true
            ORDER BY rs.stop_sequence
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<BusStop> findActiveStops() {
        String sql = "SELECT * FROM bus_stops WHERE is_active = true ORDER BY stop_name";

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Boolean> existsByStopCode(String stopCode) {
        String sql = "SELECT COUNT(*) FROM bus_stops WHERE stop_code = :stopCode";

        return databaseClient.sql(sql)
                .bind("stopCode", stopCode)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    public Mono<Long> countActiveStops() {
        String sql = "SELECT COUNT(*) FROM bus_stops WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }


    @Override
    public Flux<BusStop> searchByName(String query, Integer limit) {
        String sql = """
            SELECT * FROM bus_stops 
            WHERE (stop_name ILIKE :query 
               OR name_en ILIKE :query 
               OR name_tm ILIKE :query)
            ORDER BY 
                CASE 
                    WHEN stop_name ILIKE :exactQuery THEN 1
                    WHEN name_en ILIKE :exactQuery THEN 2
                    WHEN name_tm ILIKE :exactQuery THEN 3
                    ELSE 4
                END,
                stop_name
            LIMIT :limit
            """;

        return databaseClient.sql(sql)
                .bind("query", "%" + query + "%")
                .bind("exactQuery", query + "%")
                .bind("limit", limit)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Boolean> existsByStopName(String stopName) {
        String sql = "SELECT COUNT(*) FROM bus_stops WHERE LOWER(stop_name) = LOWER(:stopName)";

        return databaseClient.sql(sql)
                .bind("stopName", stopName)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    private BusStop mapRowToBusStop(Row row, RowMetadata metadata) {
        BusStopEntity entity = BusStopEntity.builder()
                .id(row.get("id", String.class))
                .stopName(row.get("stop_name", String.class))
                .cityId(row.get("city_id", String.class))
                .nameEn(safeGet(row, "name_en", String.class, null))
                .nameTm(safeGet(row, "name_tm", String.class, null))
                .stopCode(row.get("stop_code", String.class))
                .latitude(row.get("latitude", BigDecimal.class))
                .longitude(row.get("longitude", BigDecimal.class))
                .isActive(row.get("is_active", Boolean.class))
                .isMajorStop(safeGet(row, "is_major_stop", Boolean.class, false))
                .servingRoutesCount(safeGet(row, "serving_routes_count", Integer.class, 0))
                .createdAt(safeGet(row, "created_at", java.time.LocalDateTime.class, null))
                .updatedAt(safeGet(row, "updated_at", java.time.LocalDateTime.class, null))
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
    protected String getOrderByClause(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "ORDER BY stop_name ASC";
        }
        return "ORDER BY " + pageable.getSort().stream()
                .map(order -> mapSortField(order.getProperty()) + " " + order.getDirection().name())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String mapSortField(String sortField) {
        return switch (sortField != null ? sortField.toLowerCase() : "stop_name") {
            case "stopname", "name" -> "stop_name";
            case "nametm" -> "name_tm";
            case "nameen" -> "name_en";
            case "stopcode", "code" -> "stop_code";
            case "latitude" -> "latitude";
            case "longitude" -> "longitude";
            case "isactive", "active" -> "is_active";
            case "ismajorstop", "major" -> "is_major_stop";
            case "createdat", "created" -> "created_at";
            case "updatedat", "updated" -> "updated_at";
            default -> "stop_name";
        };
    }

    @Override
    public Flux<BusStop> findBySpecification(Specification<BusStop> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT * FROM bus_stops WHERE %s ORDER BY stop_name ASC, created_at DESC",
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
    public Flux<BusStop> findBySpecification(Specification<BusStop> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT * FROM bus_stops WHERE %s %s LIMIT :limit OFFSET :offset",
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
    public Mono<Long> countBySpecification(Specification<BusStop> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT COUNT(*) FROM bus_stops WHERE %s",
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
    public Flux<BusArrivalInfo> findArrivingVehicles(BusStopId stopId, Double stopLatitude, Double stopLongitude) {
        int maxAgeMinutes = etaProperties.getPosition().getMaxAgeMinutes();
        int maxEtaMinutes = etaProperties.getPosition().getMaxEtaMinutes();
        int atStopDistance = etaProperties.getPosition().getAtStopDistanceMeters();

        double movingThreshold = etaProperties.getSpeed().getMovingThresholdKmh();
        double morningRushSpeed = etaProperties.getSpeed().getMorningRushKmh();
        double eveningRushSpeed = etaProperties.getSpeed().getEveningRushKmh();
        double lunchSpeed = etaProperties.getSpeed().getLunchTimeKmh();
        double normalSpeed = etaProperties.getSpeed().getNormalKmh();

        double trafficMorningRush = etaProperties.getTraffic().getMorningRush();
        double trafficEveningRush = etaProperties.getTraffic().getEveningRush();
        double trafficDaytime = etaProperties.getTraffic().getDaytime();
        double trafficEvening = etaProperties.getTraffic().getEvening();
        double trafficNight = etaProperties.getTraffic().getNight();

        String sql = """
        WITH
        traffic_multiplier AS (
            SELECT
                CASE
                    WHEN EXTRACT(HOUR FROM CURRENT_TIMESTAMP) BETWEEN 7 AND 9 THEN :trafficMorningRush
                    WHEN EXTRACT(HOUR FROM CURRENT_TIMESTAMP) BETWEEN 17 AND 19 THEN :trafficEveningRush
                    WHEN EXTRACT(HOUR FROM CURRENT_TIMESTAMP) BETWEEN 10 AND 16 THEN :trafficDaytime
                    WHEN EXTRACT(HOUR FROM CURRENT_TIMESTAMP) BETWEEN 20 AND 22 THEN :trafficEvening
                    ELSE :trafficNight
                END as multiplier
        ),

        target_stop_routes AS (
            SELECT DISTINCT
                rs.route_id,
                rs.direction,
                rs.stop_sequence as target_sequence,
                br.route_number,
                br.route_name,
                br.route_color,
                CASE WHEN rs.direction = 0
                    THEN br.route_geometry_forward
                    ELSE br.route_geometry_backward
                END as route_geometry,
                CASE WHEN rs.direction = 0
                    THEN COALESCE(br.total_distance_forward_meters, 10000)
                    ELSE COALESCE(br.total_distance_backward_meters, 10000)
                END as total_route_distance
            FROM route_stops rs
            JOIN bus_routes br ON rs.route_id = br.id
            WHERE rs.stop_id = :stopId
            AND br.is_active = true
        ),

        route_vehicles AS (
            SELECT
                v.id as vehicle_id,
                v.license_plate,
                v.current_latitude,
                v.current_longitude,
                v.speed_kmh,
                v.is_in_motion,
                v.course,
                v.last_stop_sequence,
                tsr.route_id,
                tsr.direction,
                tsr.target_sequence,
                tsr.route_number,
                tsr.route_name,
                tsr.route_color,
                tsr.total_route_distance,
                ST_Distance(
                    ST_Point(v.current_longitude, v.current_latitude)::geography,
                    ST_Point(:stopLon, :stopLat)::geography
                ) as distance_to_stop_direct,
                CASE
                    WHEN tsr.route_geometry IS NOT NULL THEN
                        ST_LineLocatePoint(
                            ST_GeomFromText(tsr.route_geometry, 4326),
                            ST_Point(v.current_longitude, v.current_latitude)
                        )
                    ELSE NULL
                END as vehicle_position_on_route,
                CASE
                    WHEN tsr.route_geometry IS NOT NULL THEN
                        ST_LineLocatePoint(
                            ST_GeomFromText(tsr.route_geometry, 4326),
                            ST_Point(:stopLon, :stopLat)
                        )
                    ELSE NULL
                END as stop_position_on_route
            FROM vehicles v
            JOIN target_stop_routes tsr ON v.assigned_route_id = tsr.route_id
                AND (v.current_direction IS NULL OR v.current_direction = tsr.direction)
            WHERE v.is_active = true
            AND v.last_position_update > CURRENT_TIMESTAMP - (INTERVAL '1 minute' * :maxAgeMinutes)
            AND v.current_latitude IS NOT NULL
            AND v.current_longitude IS NOT NULL
            AND (v.last_stop_sequence IS NULL OR v.last_stop_sequence < tsr.target_sequence)
        ),

        vehicles_with_distance AS (
            SELECT
                rv.*,
                CASE
                    WHEN rv.vehicle_position_on_route IS NOT NULL
                         AND rv.stop_position_on_route IS NOT NULL
                         AND rv.stop_position_on_route > rv.vehicle_position_on_route
                    THEN (rv.stop_position_on_route - rv.vehicle_position_on_route) * rv.total_route_distance
                    ELSE NULL
                END as distance_on_route,
                CASE
                    WHEN rv.last_stop_sequence IS NOT NULL AND rv.last_stop_sequence >= rv.target_sequence
                    THEN false
                    WHEN rv.vehicle_position_on_route IS NOT NULL
                         AND rv.stop_position_on_route IS NOT NULL
                    THEN rv.vehicle_position_on_route < rv.stop_position_on_route
                    ELSE true
                END as is_before_stop
            FROM route_vehicles rv
        ),

        route_stops_with_coords AS (
            SELECT
                rs.route_id,
                rs.direction,
                rs.stop_sequence,
                bs.stop_name
            FROM route_stops rs
            JOIN bus_stops bs ON rs.stop_id = bs.id
            JOIN vehicles_with_distance vwd ON rs.route_id = vwd.route_id AND rs.direction = vwd.direction
            WHERE bs.latitude IS NOT NULL
        ),

        vehicle_current_stop AS (
            SELECT DISTINCT ON (vwd.vehicle_id)
                vwd.vehicle_id,
                rsc.stop_name as current_stop_name,
                rsc.stop_sequence as current_sequence
            FROM vehicles_with_distance vwd
            JOIN route_stops_with_coords rsc ON vwd.route_id = rsc.route_id AND vwd.direction = rsc.direction
            WHERE vwd.vehicle_position_on_route IS NOT NULL
            ORDER BY vwd.vehicle_id, ABS(rsc.stop_sequence - (vwd.vehicle_position_on_route * 100))
        ),

        vehicles_with_eta AS (
            SELECT
                vwd.vehicle_id,
                vwd.license_plate,
                vwd.current_latitude,
                vwd.current_longitude,
                vwd.speed_kmh,
                vwd.is_in_motion,
                vwd.course,
                vwd.route_id,
                vwd.direction,
                vwd.route_number,
                vwd.route_name,
                vwd.route_color,
                vwd.distance_on_route,
                vwd.distance_to_stop_direct,
                vwd.is_before_stop,
                COALESCE(vcs.current_stop_name, 'В пути') as current_stop_name,
                tm.multiplier as traffic_multiplier,
                CASE
                    WHEN vwd.is_before_stop = false THEN NULL
                    WHEN COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct) < 300 THEN 1
                    ELSE GREATEST(2, ROUND(
                        CASE
                            WHEN vwd.speed_kmh > :movingThreshold THEN
                                COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.3) / (vwd.speed_kmh * 1000.0 / 60.0)
                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 7 AND 9 THEN
                                COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.3) / (:morningRushSpeed * 1000.0 / 60.0)
                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 17 AND 19 THEN
                                COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.3) / (:eveningRushSpeed * 1000.0 / 60.0)
                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 12 AND 14 THEN
                                COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.3) / (:lunchSpeed * 1000.0 / 60.0)
                            ELSE
                                COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.3) / (:normalSpeed * 1000.0 / 60.0)
                        END
                        * tm.multiplier
                    )::integer)
                END as calculated_eta,
                CASE
                    WHEN vwd.is_before_stop = false THEN 'passed'
                    WHEN COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct) < :atStopDistance THEN 'at_stop'
                    ELSE 'approaching'
                END as calculated_status
            FROM vehicles_with_distance vwd
            LEFT JOIN vehicle_current_stop vcs ON vwd.vehicle_id = vcs.vehicle_id
            CROSS JOIN traffic_multiplier tm
            WHERE vwd.is_before_stop = true
        )

        SELECT DISTINCT ON (route_number)
            vwe.vehicle_id,
            vwe.license_plate,
            vwe.route_id,
            vwe.route_number,
            vwe.route_name,
            vwe.route_color,
            vwe.calculated_eta as estimated_arrival_minutes,
            vwe.calculated_status as arrival_status,
            vwe.current_latitude,
            vwe.current_longitude,
            vwe.speed_kmh,
            vwe.is_in_motion,
            vwe.current_stop_name,
            vwe.course
        FROM vehicles_with_eta vwe
        WHERE vwe.calculated_eta IS NOT NULL
        AND vwe.calculated_eta > 0
        AND vwe.calculated_eta < :maxEtaMinutes
        ORDER BY vwe.route_number, vwe.calculated_eta
        """;

        return databaseClient.sql(sql)
                .bind("stopId", stopId.getValue())
                .bind("stopLat", stopLatitude)
                .bind("stopLon", stopLongitude)
                .bind("maxAgeMinutes", maxAgeMinutes)
                .bind("maxEtaMinutes", maxEtaMinutes)
                .bind("atStopDistance", atStopDistance)
                .bind("movingThreshold", movingThreshold)
                .bind("morningRushSpeed", morningRushSpeed)
                .bind("eveningRushSpeed", eveningRushSpeed)
                .bind("lunchSpeed", lunchSpeed)
                .bind("normalSpeed", normalSpeed)
                .bind("trafficMorningRush", trafficMorningRush)
                .bind("trafficEveningRush", trafficEveningRush)
                .bind("trafficDaytime", trafficDaytime)
                .bind("trafficEvening", trafficEvening)
                .bind("trafficNight", trafficNight)
                .map(this::mapToBusArrivalInfo)
                .all()
                .doOnNext(arrival -> log.trace("Route {} closest bus: {} ETA {} min (traffic adjusted)",
                        arrival.getRouteNumber(), arrival.getLicensePlate(), arrival.getEstimatedArrivalMinutes()));
    }

    private BusArrivalInfo mapToBusArrivalInfo(Row row, RowMetadata metadata) {
        String vehicleId = row.get("vehicle_id", String.class);
        String routeId = row.get("route_id", String.class);
        String licensePlate = row.get("license_plate", String.class);
        String routeNumber = row.get("route_number", String.class);
        String routeName = row.get("route_name", String.class);
        String routeColor = row.get("route_color", String.class);
        Integer etaMinutes = row.get("estimated_arrival_minutes", Integer.class);
        String arrivalStatus = row.get("arrival_status", String.class);
        Double currentLat = row.get("current_latitude", Double.class);
        Double currentLon = row.get("current_longitude", Double.class);
        Double speedKmh = row.get("speed_kmh", Double.class);
        Boolean isInMotion = row.get("is_in_motion", Boolean.class);
        String currentStopName = row.get("current_stop_name", String.class);
        Double course = row.get("course", Double.class);

        return new BusArrivalInfo(
                vehicleId,
                licensePlate,
                routeId,
                routeNumber,
                routeName,
                routeColor,
                etaMinutes != null ? etaMinutes : 999,
                arrivalStatus,
                currentLat,
                currentLon,
                speedKmh != null ? speedKmh : 0.0,
                Boolean.TRUE.equals(isInMotion),
                currentStopName,
                LocalDateTime.now(),
                course != null ? course : 0.0
        );
    }
}