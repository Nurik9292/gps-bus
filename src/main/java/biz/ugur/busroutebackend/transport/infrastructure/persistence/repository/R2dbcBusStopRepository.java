package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISQueryBuilder;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopWithRouteDistance;
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

        int dwellTimeSeconds = etaProperties.getStop().getDwellTimeSeconds();
        int stoppedPenaltySeconds = etaProperties.getStop().getStoppedPenaltySeconds();
        int terminalWaitSeconds = etaProperties.getStop().getTerminalWaitSeconds();

        String sql = """
        WITH
        -- Use Turkmenistan local time (Asia/Ashgabat, UTC+5) for traffic multiplier calculation
        traffic_multiplier AS (
            SELECT
                CASE
                    WHEN EXTRACT(HOUR FROM (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ashgabat')) BETWEEN 7 AND 9 THEN :trafficMorningRush
                    WHEN EXTRACT(HOUR FROM (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ashgabat')) BETWEEN 17 AND 19 THEN :trafficEveningRush
                    WHEN EXTRACT(HOUR FROM (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ashgabat')) BETWEEN 10 AND 16 THEN :trafficDaytime
                    WHEN EXTRACT(HOUR FROM (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ashgabat')) BETWEEN 20 AND 22 THEN :trafficEvening
                    ELSE :trafficNight
                END as multiplier,
                :dwellTimeSeconds as dwell_time_sec,
                :stoppedPenaltySeconds as stopped_penalty_sec,
                :terminalWaitSeconds as terminal_wait_sec,
                :movingThreshold as moving_threshold
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
                    WHEN tsr.route_geometry IS NOT NULL
                         AND tsr.route_geometry LIKE 'LINESTRING%' THEN
                        ST_LineLocatePoint(
                            ST_GeomFromText(tsr.route_geometry, 4326),
                            ST_SetSRID(ST_Point(v.current_longitude, v.current_latitude), 4326)
                        )
                    ELSE NULL
                END as vehicle_position_on_route,
                CASE
                    WHEN tsr.route_geometry IS NOT NULL
                         AND tsr.route_geometry LIKE 'LINESTRING%' THEN
                        ST_LineLocatePoint(
                            ST_GeomFromText(tsr.route_geometry, 4326),
                            ST_SetSRID(ST_Point(:stopLon, :stopLat), 4326)
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
                    -- Если last_stop_sequence >= target - точно проехал
                    WHEN rv.last_stop_sequence IS NOT NULL AND rv.last_stop_sequence >= rv.target_sequence
                    THEN false
                    -- Если позиция на маршруте известна - используем её (более точно)
                    WHEN rv.vehicle_position_on_route IS NOT NULL
                         AND rv.stop_position_on_route IS NOT NULL
                    THEN rv.vehicle_position_on_route < rv.stop_position_on_route
                    -- Если позиция неизвестна но есть last_stop_sequence - используем его
                    WHEN rv.last_stop_sequence IS NOT NULL
                    THEN rv.last_stop_sequence < rv.target_sequence
                    -- Если ничего не известно - не показываем (безопаснее)
                    ELSE false
                END as is_before_stop
            FROM route_vehicles rv
        ),

        route_stops_with_coords AS (
            SELECT
                rs.route_id,
                rs.direction,
                rs.stop_sequence,
                bs.stop_name,
                MAX(rs.stop_sequence) OVER (PARTITION BY rs.route_id, rs.direction) as max_stop_sequence
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
            ORDER BY vwd.vehicle_id,
                     ABS((rsc.stop_sequence::float / NULLIF(rsc.max_stop_sequence, 0)) - vwd.vehicle_position_on_route)
        ),

        -- Count intermediate stops between vehicle position and target stop
        intermediate_stops AS (
            SELECT
                vwd.vehicle_id,
                vwd.route_id,
                vwd.direction,
                vwd.target_sequence,
                COALESCE(vwd.last_stop_sequence, vcs.current_sequence, 1) as vehicle_sequence,
                -- Count stops between current position and target (excluding current, including target)
                GREATEST(0, vwd.target_sequence - COALESCE(vwd.last_stop_sequence, vcs.current_sequence, 1) - 1) as stops_count
            FROM vehicles_with_distance vwd
            LEFT JOIN vehicle_current_stop vcs ON vwd.vehicle_id = vcs.vehicle_id
            WHERE vwd.is_before_stop = true
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
                vwd.last_stop_sequence,
                COALESCE(istops.stops_count, 0) as intermediate_stops,
                COALESCE(vcs.current_stop_name, 'В пути') as current_stop_name,
                tm.multiplier as traffic_multiplier,
                CASE
                    WHEN vwd.is_before_stop = false THEN NULL
                    -- Show 1 min only if BOTH direct distance AND route distance are KNOWN and small AND no intermediate stops
                    WHEN vwd.distance_to_stop_direct < 300
                         AND vwd.distance_on_route IS NOT NULL
                         AND vwd.distance_on_route < 500
                         AND COALESCE(istops.stops_count, 0) <= 1 THEN 1
                    -- Calculate ETA with stop dwell times and penalties
                    ELSE GREATEST(1, ROUND(
                        (
                            -- Base travel time calculation
                            CASE
                                WHEN vwd.speed_kmh > tm.moving_threshold THEN
                                    GREATEST(COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.5), vwd.distance_to_stop_direct) / (vwd.speed_kmh * 1000.0 / 60.0)
                                WHEN EXTRACT(hour FROM (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ashgabat')) BETWEEN 7 AND 9 THEN
                                    GREATEST(COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.5), vwd.distance_to_stop_direct) / (:morningRushSpeed * 1000.0 / 60.0)
                                WHEN EXTRACT(hour FROM (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ashgabat')) BETWEEN 17 AND 19 THEN
                                    GREATEST(COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.5), vwd.distance_to_stop_direct) / (:eveningRushSpeed * 1000.0 / 60.0)
                                WHEN EXTRACT(hour FROM (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ashgabat')) BETWEEN 12 AND 14 THEN
                                    GREATEST(COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.5), vwd.distance_to_stop_direct) / (:lunchSpeed * 1000.0 / 60.0)
                                ELSE
                                    GREATEST(COALESCE(vwd.distance_on_route, vwd.distance_to_stop_direct * 1.5), vwd.distance_to_stop_direct) / (:normalSpeed * 1000.0 / 60.0)
                            END
                            * tm.multiplier
                            -- Add dwell time for intermediate stops (stops_count * dwell_time_sec / 60.0)
                            + (COALESCE(istops.stops_count, 0) * tm.dwell_time_sec / 60.0)
                            -- Add penalty for stopped buses (not moving)
                            + CASE WHEN vwd.speed_kmh < tm.moving_threshold AND NOT COALESCE(vwd.is_in_motion, false)
                                   THEN tm.stopped_penalty_sec / 60.0 ELSE 0 END
                            -- Add terminal wait if bus is at first stop
                            + CASE WHEN vwd.last_stop_sequence = 1 THEN tm.terminal_wait_sec / 60.0 ELSE 0 END
                        )
                    )::integer)
                END as calculated_eta,
                CASE
                    WHEN vwd.is_before_stop = false THEN 'passed'
                    WHEN vwd.distance_to_stop_direct < :atStopDistance THEN 'at_stop'
                    ELSE 'approaching'
                END as calculated_status
            FROM vehicles_with_distance vwd
            LEFT JOIN vehicle_current_stop vcs ON vwd.vehicle_id = vcs.vehicle_id
            LEFT JOIN intermediate_stops istops ON vwd.vehicle_id = istops.vehicle_id
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
            vwe.course,
            COALESCE(vwe.distance_on_route, vwe.distance_to_stop_direct) as distance_meters
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
                .bind("dwellTimeSeconds", dwellTimeSeconds)
                .bind("stoppedPenaltySeconds", stoppedPenaltySeconds)
                .bind("terminalWaitSeconds", terminalWaitSeconds)
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
        Double distance = row.get("distance_meters", Double.class);
        Integer distanceMeters = distance != null ? (int) Math.round(distance) : null;
        Integer direction = row.get("direction", Integer.class);

        BusArrivalInfo info = new BusArrivalInfo(
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
                course != null ? course : 0.0,
                distanceMeters
        );
        info.setDirection(direction);
        return info;
    }

    @Override
    public Flux<BusStop> findStopsOnRouteAhead(String routeNumber, Double vehicleLat, Double vehicleLon, int maxStops) {
        return databaseClient.sql("""
            WITH route_stops_ordered AS (
                SELECT
                    bs.id, bs.stop_name, bs.stop_code, bs.latitude, bs.longitude,
                    bs.city_id, bs.is_active, bs.created_at, bs.updated_at,
                    rs.stop_sequence,
                    ST_Distance(
                        ST_Point(:vehicleLon, :vehicleLat)::geography,
                        ST_Point(bs.longitude, bs.latitude)::geography
                    ) as distance_to_vehicle
                FROM bus_stops bs
                JOIN route_stops rs ON bs.id = rs.stop_id
                JOIN bus_routes br ON rs.route_id = br.id
                WHERE br.route_number = :routeNumber
                AND br.is_active = true
                AND bs.is_active = true
                ORDER BY distance_to_vehicle
            ),
            nearest_stop AS (
                SELECT stop_sequence FROM route_stops_ordered LIMIT 1
            )
            SELECT DISTINCT ON (rso.id)
                rso.id, rso.stop_name, rso.stop_code, rso.latitude, rso.longitude,
                rso.city_id, rso.is_active, rso.created_at, rso.updated_at,
                rso.distance_to_vehicle
            FROM route_stops_ordered rso
            WHERE rso.stop_sequence >= (SELECT stop_sequence FROM nearest_stop)
            ORDER BY rso.id, rso.stop_sequence
            LIMIT :maxStops
            """)
                .bind("routeNumber", routeNumber)
                .bind("vehicleLat", vehicleLat)
                .bind("vehicleLon", vehicleLon)
                .bind("maxStops", maxStops)
                .map(this::mapRowToBusStop)
                .all()
                .doOnNext(stop -> log.trace("Found stop ahead on route {}: {} ({}m away)",
                        routeNumber, stop.getStopName(), "calculated"));
    }

    @Override
    public Flux<StopWithRouteDistance> findStopsOnRouteAheadWithRouteDistance(
            String routeNumber,
            Double vehicleLat,
            Double vehicleLon,
            Integer direction,
            int maxStops) {

        String sql = """
            WITH route_info AS (
                -- Get route geometry and total distance based on direction
                SELECT
                    br.id as route_id,
                    br.route_number,
                    CASE WHEN :direction = 1 THEN br.route_geometry_backward
                         ELSE br.route_geometry_forward
                    END as route_geometry,
                    CASE WHEN :direction = 1 THEN COALESCE(br.total_distance_backward_meters, 10000)
                         ELSE COALESCE(br.total_distance_forward_meters, 10000)
                    END as total_distance,
                    COALESCE(:direction, 0) as eff_direction
                FROM bus_routes br
                WHERE br.route_number = :routeNumber
                AND br.is_active = true
                LIMIT 1
            ),

            vehicle_position AS (
                -- Project vehicle position onto route line
                SELECT
                    ri.*,
                    CASE
                        WHEN ri.route_geometry IS NOT NULL
                             AND ri.route_geometry LIKE 'LINESTRING%'
                             AND LENGTH(ri.route_geometry) > 20
                        THEN ST_LineLocatePoint(
                            ST_GeomFromText(ri.route_geometry, 4326),
                            ST_SetSRID(ST_Point(:vehicleLon, :vehicleLat), 4326)
                        )
                        ELSE NULL
                    END as vehicle_fraction
                FROM route_info ri
            ),

            stops_with_fractions AS (
                -- Calculate position of each stop on the route line
                SELECT
                    bs.id as stop_id,
                    bs.stop_name,
                    bs.latitude,
                    bs.longitude,
                    rs.stop_sequence,
                    rs.direction,
                    vp.total_distance,
                    vp.vehicle_fraction,
                    CASE
                        WHEN vp.route_geometry IS NOT NULL
                             AND vp.route_geometry LIKE 'LINESTRING%'
                             AND LENGTH(vp.route_geometry) > 20
                        THEN ST_LineLocatePoint(
                            ST_GeomFromText(vp.route_geometry, 4326),
                            ST_SetSRID(ST_Point(bs.longitude, bs.latitude), 4326)
                        )
                        ELSE NULL
                    END as stop_fraction,
                    -- Direct distance for fallback
                    ST_Distance(
                        ST_Point(:vehicleLon, :vehicleLat)::geography,
                        ST_Point(bs.longitude, bs.latitude)::geography
                    ) as direct_distance
                FROM bus_stops bs
                JOIN route_stops rs ON bs.id = rs.stop_id
                JOIN bus_routes br ON rs.route_id = br.id
                CROSS JOIN vehicle_position vp
                WHERE br.route_number = :routeNumber
                  AND br.is_active = true
                  AND bs.is_active = true
                  AND rs.direction = vp.eff_direction
            ),

            stops_with_route_distance AS (
                SELECT
                    stop_id,
                    stop_name,
                    latitude,
                    longitude,
                    stop_sequence,
                    direction,
                    direct_distance as direct_distance_meters,
                    vehicle_fraction,
                    stop_fraction,
                    CASE
                        -- If we have valid fractions and stop is ahead on route
                        WHEN vehicle_fraction IS NOT NULL
                             AND stop_fraction IS NOT NULL
                             AND stop_fraction > vehicle_fraction
                        THEN (stop_fraction - vehicle_fraction) * total_distance
                        -- Fallback: use direct distance with correction factor
                        ELSE direct_distance * :correctionFactor
                    END as distance_on_route_meters,
                    -- Determine if stop is ahead
                    CASE
                        WHEN vehicle_fraction IS NOT NULL AND stop_fraction IS NOT NULL
                        THEN stop_fraction > vehicle_fraction
                        ELSE true  -- If no geometry, include by sequence
                    END as is_ahead
                FROM stops_with_fractions
            )

            SELECT
                stop_id,
                stop_name,
                latitude,
                longitude,
                stop_sequence,
                direction,
                direct_distance_meters,
                distance_on_route_meters
            FROM stops_with_route_distance
            WHERE is_ahead = true
              AND distance_on_route_meters > 0
            ORDER BY
                CASE
                    WHEN stop_fraction IS NOT NULL THEN stop_fraction
                    ELSE stop_sequence::float / 1000.0
                END
            LIMIT :maxStops
            """;

        Integer effectiveDirection = direction != null ? direction : 0;
        double correctionFactor = etaProperties.getFallback().getRouteDistanceCorrectionFactor();

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .bind("vehicleLat", vehicleLat)
                .bind("vehicleLon", vehicleLon)
                .bind("direction", effectiveDirection)
                .bind("correctionFactor", correctionFactor)
                .bind("maxStops", maxStops)
                .map(this::mapToStopWithRouteDistance)
                .all()
                .doOnNext(stop -> log.trace(
                        "Stop {} on route {}: route_dist={}m, direct_dist={}m ({})",
                        stop.getStopName(),
                        routeNumber,
                        Math.round(stop.getDistanceOnRouteMeters()),
                        Math.round(stop.getDirectDistanceMeters()),
                        stop.hasRouteDistance() ? "geometry" : "fallback"
                ));
    }

    private StopWithRouteDistance mapToStopWithRouteDistance(Row row, RowMetadata metadata) {
        return StopWithRouteDistance.builder()
                .stopId(row.get("stop_id", String.class))
                .stopName(row.get("stop_name", String.class))
                .latitude(safeGetDouble(row, "latitude"))
                .longitude(safeGetDouble(row, "longitude"))
                .stopSequence(row.get("stop_sequence", Integer.class))
                .direction(row.get("direction", Integer.class))
                .distanceOnRouteMeters(safeGetDouble(row, "distance_on_route_meters"))
                .directDistanceMeters(safeGetDouble(row, "direct_distance_meters"))
                .build();
    }

    private Double safeGetDouble(Row row, String columnName) {
        try {
            BigDecimal value = row.get(columnName, BigDecimal.class);
            return value != null ? value.doubleValue() : null;
        } catch (Exception e) {
            try {
                return row.get(columnName, Double.class);
            } catch (Exception e2) {
                return null;
            }
        }
    }
}