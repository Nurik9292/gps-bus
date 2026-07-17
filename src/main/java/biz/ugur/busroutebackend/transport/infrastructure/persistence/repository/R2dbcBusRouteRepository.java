package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.NearbyRouteQueryRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.NearbyRouteInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteInAreaInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteVehicleStatistics;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.BusRouteEntity;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.BusRouteEntityMapper;
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
public class R2dbcBusRouteRepository extends BaseR2dbcRepository<BusRoute, BusRouteId>
        implements BusRouteRepository, NearbyRouteQueryRepository {

    private static final String SELECT_COLUMNS = String.join(", ",
            "id", "route_number", "route_name", "name_tm", "name_en",
            "route_color", "is_active", "city_id", "estimated_duration_minutes",
            "route_geometry_forward", "route_geometry_backward",
            "total_distance_forward_meters", "total_distance_backward_meters",
            "version", "created_at", "updated_at"
    );

    private final BusRouteEntityMapper entityMapper;

    public R2dbcBusRouteRepository(DatabaseClient databaseClient, BusRouteEntityMapper entityMapper) {
        super(databaseClient, "bus_routes", BusRoute.class);
        this.entityMapper = entityMapper;
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(BusRouteId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, BusRoute> getRowMapper() {
        return this::mapRowToBusRoute;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(BusRoute entity) {
        BusRouteEntity persistenceEntity = entityMapper.toEntity(entity);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", persistenceEntity.getId());
        columns.put("route_number", persistenceEntity.getRouteNumber());
        columns.put("route_name", persistenceEntity.getRouteName());
        columns.put("name_tm", persistenceEntity.getNameTm());
        columns.put("name_en", persistenceEntity.getNameEn());
        columns.put("route_color", persistenceEntity.getRouteColor());
        columns.put("is_active", persistenceEntity.getIsActive());
        columns.put("city_id", persistenceEntity.getCityId());
        columns.put("estimated_duration_minutes", persistenceEntity.getEstimatedDurationMinutes());
        columns.put("route_geometry_forward", persistenceEntity.getRouteGeometryForward());
        columns.put("route_geometry_backward", persistenceEntity.getRouteGeometryBackward());
        columns.put("total_distance_forward_meters", persistenceEntity.getTotalDistanceForwardMeters());
        columns.put("total_distance_backward_meters", persistenceEntity.getTotalDistanceBackwardMeters());
        return columns;
    }

    @Override
    protected Mono<BusRoute> insert(BusRoute entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("created_at", LocalDateTime.now());
        values.put("updated_at", LocalDateTime.now());
        values.put("version", 1L);

        String sql = """
            INSERT INTO bus_routes (
                id, route_number, route_name, name_tm, name_en, route_color, 
                is_active, city_id, estimated_duration_minutes,
                route_geometry_forward, route_geometry_backward,
                geometry_forward, geometry_backward,
                total_distance_forward_meters, total_distance_backward_meters,
                created_at, updated_at, version
            ) VALUES (
                :id, :route_number, :route_name, :name_tm, :name_en, :route_color,
                :is_active, :city_id, :estimated_duration_minutes,
                :route_geometry_forward, :route_geometry_backward,
                CASE 
                    WHEN :route_geometry_forward IS NOT NULL 
                    THEN ST_GeomFromText(:route_geometry_forward, 4326)::geometry
                    ELSE NULL
                END,
                CASE 
                    WHEN :route_geometry_backward IS NOT NULL 
                    THEN ST_GeomFromText(:route_geometry_backward, 4326)::geometry
                    ELSE NULL
                END,
                :total_distance_forward_meters, :total_distance_backward_meters,
                :created_at, :updated_at, :version
            ) RETURNING %s
            """.formatted(selectColumns());

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
                .doOnSuccess(route -> log.info("New route {} created successfully with geometry",
                        route.getRouteNumber()))
                .doOnError(error -> log.error("Failed to create route {}: {}",
                        entity.getRouteNumber(), error.getMessage()));
    }

    @Override
    protected Mono<BusRoute> update(BusRoute entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("updated_at", LocalDateTime.now());
        values.put("version", entity.getVersion() + 1);

        String sql = """
            UPDATE bus_routes SET
                route_number = :route_number,
                route_name = :route_name,
                name_tm = :name_tm,
                name_en = :name_en,
                route_color = :route_color,
                is_active = :is_active,
                city_id = :city_id,
                estimated_duration_minutes = :estimated_duration_minutes,
                route_geometry_forward = :route_geometry_forward,
                route_geometry_backward = :route_geometry_backward,
                geometry_forward = CASE
                    WHEN :route_geometry_forward IS NOT NULL
                    THEN ST_GeomFromText(:route_geometry_forward, 4326)::geometry
                    ELSE geometry_forward
                END,
                geometry_backward = CASE
                    WHEN :route_geometry_backward IS NOT NULL
                    THEN ST_GeomFromText(:route_geometry_backward, 4326)::geometry
                    ELSE geometry_backward
                END,
                total_distance_forward_meters = :total_distance_forward_meters,
                total_distance_backward_meters = :total_distance_backward_meters,
                updated_at = :updated_at,
                version = :version
            WHERE id = :id AND version = :old_version
            RETURNING %s
            """.formatted(selectColumns());

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
                    String msg = "Version conflict for " + entityClass.getSimpleName()
                            + " with id: " + entity.getId();
                    log.error(msg);
                    return Mono.error(new org.springframework.dao.OptimisticLockingFailureException(msg));
                }))
                .doOnSuccess(route -> log.info("Route {} updated successfully with geometry",
                        route.getRouteNumber()))
                .doOnError(error -> log.error("Failed to update route {}: {}",
                        entity.getRouteNumber(), error.getMessage()));
    }

    @Override
    public Mono<BusRoute> findByRouteNumberAndCityId(String routeNumber, String cityId) {
        String sql = String.format(
                "SELECT %s FROM bus_routes WHERE route_number = :routeNumber AND city_id = :cityId AND is_active = true",
                selectColumns()
        );

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .bind("cityId", cityId)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Mono<BusRoute> findPreferredByRouteNumber(String routeNumber) {
        String sql = String.format(
                "SELECT %s FROM bus_routes br" +
                " LEFT JOIN cities c ON c.id = br.city_id" +
                " WHERE br.route_number = :routeNumber AND br.is_active = true" +
                " ORDER BY c.display_order NULLS LAST, br.city_id NULLS LAST" +
                " LIMIT 1",
                selectColumns("br")
        );

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Flux<BusRoute> findActiveRoutes() {
        String sql = String.format(
                "SELECT %s FROM bus_routes WHERE is_active = true ORDER BY route_number",
                selectColumns()
        );

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }


    @Override
    public Flux<biz.ugur.busroutebackend.transport.domain.valueobject.RouteSelectInfo> findActiveRouteSelectInfo() {
        String sql = """
            SELECT br.id, br.route_number, br.route_name, br.city_id, c.name AS city_name
            FROM bus_routes br
            LEFT JOIN cities c ON c.id = br.city_id
            WHERE br.is_active = true
            ORDER BY c.display_order NULLS LAST, br.route_number
            """;

        return databaseClient.sql(sql)
                .map(row -> new biz.ugur.busroutebackend.transport.domain.valueobject.RouteSelectInfo(
                        row.get("id", String.class),
                        row.get("route_number", String.class),
                        row.get("route_name", String.class),
                        row.get("city_id", String.class),
                        row.get("city_name", String.class)))
                .all();
    }

    @Override
    public Mono<Boolean> existsByRouteNumberAndCityId(String routeNumber, String cityId) {
        String sql = "SELECT COUNT(*) FROM bus_routes WHERE route_number = :routeNumber"
                + " AND (:cityId::VARCHAR IS NULL OR city_id = :cityId)";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("routeNumber", routeNumber);
        spec = cityId == null ? spec.bindNull("cityId", String.class) : spec.bind("cityId", cityId);
        return spec
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    public Mono<Long> countActiveRoutes() {
        String sql = "SELECT COUNT(*) FROM bus_routes WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<BusRoute> searchRoutesByNameOrNumber(String query, Integer limit) {
        String sql = String.format("""
            SELECT %s FROM bus_routes br
            WHERE br.is_active = true
            AND (LOWER(br.route_number) LIKE LOWER(:query)
                 OR LOWER(br.route_name) LIKE LOWER(:searchPattern)
                 OR LOWER(br.name_tm) LIKE LOWER(:searchPattern))
            ORDER BY
                CASE
                    WHEN LOWER(br.route_number) = LOWER(:query) THEN 1
                    WHEN LOWER(br.route_number) LIKE LOWER(:query) THEN 2
                    WHEN LOWER(br.route_name) LIKE LOWER(:searchPattern) THEN 3
                    ELSE 4
                END,
                br.route_number
            LIMIT :limit
            """, selectColumns("br"));

        String searchPattern = "%" + query + "%";

        return databaseClient.sql(sql)
                .bind("query", query.trim())
                .bind("searchPattern", searchPattern)
                .bind("limit", limit)
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Route search completed for query: '{}'", query));
    }

    @Override
    public Flux<RouteInAreaInfo> findRoutesIntersectingArea(Double latitude, Double longitude, Integer radiusMeters) {
        String sql = """
            WITH search_area AS (
                SELECT ST_Buffer(
                    ST_GeogFromText('POINT(' || :centerLon || ' ' || :centerLat || ')')::geography,
                    :radiusMeters
                )::geometry as geom
            ),
            route_intersections AS (
                SELECT 
                    br.id as route_id,
                    br.route_number,
                    br.route_name,
                    br.route_color,
                    0 as direction,
                    ST_Y(ST_ClosestPoint(br.geometry_forward, ST_Point(:centerLon, :centerLat))) as nearest_point_lat,
                    ST_X(ST_ClosestPoint(br.geometry_forward, ST_Point(:centerLon, :centerLat))) as nearest_point_lon,
                    ST_Distance(
                        ST_GeogFromText('POINT(' || :centerLon || ' ' || :centerLat || ')'),
                        ST_ClosestPoint(br.geometry_forward, ST_Point(:centerLon, :centerLat))::geography
                    ) as distance_to_center
                FROM bus_routes br, search_area sa
                WHERE br.is_active = true 
                AND br.geometry_forward IS NOT NULL
                AND ST_Intersects(br.geometry_forward, sa.geom)
                
                UNION ALL
                
                SELECT 
                    br.id as route_id,
                    br.route_number,
                    br.route_name,
                    br.route_color,
                    1 as direction,
                    ST_Y(ST_ClosestPoint(br.geometry_backward, ST_Point(:centerLon, :centerLat))) as nearest_point_lat,
                    ST_X(ST_ClosestPoint(br.geometry_backward, ST_Point(:centerLon, :centerLat))) as nearest_point_lon,
                    ST_Distance(
                        ST_GeogFromText('POINT(' || :centerLon || ' ' || :centerLat || ')'),
                        ST_ClosestPoint(br.geometry_backward, ST_Point(:centerLon, :centerLat))::geography
                    ) as distance_to_center
                FROM bus_routes br, search_area sa
                WHERE br.is_active = true 
                AND br.geometry_backward IS NOT NULL
                AND ST_Intersects(br.geometry_backward, sa.geom)
            )
            SELECT * FROM route_intersections
            ORDER BY distance_to_center
            """;

        return databaseClient.sql(sql)
                .bind("centerLat", latitude)
                .bind("centerLon", longitude)
                .bind("radiusMeters", radiusMeters)
                .map(this::mapToRouteInAreaInfo)
                .all()
                .doOnComplete(() -> log.debug("Found routes intersecting area at ({}, {}) within {}m",
                        latitude, longitude, radiusMeters));
    }

    @Override
    public Flux<RouteStopInfo> getRouteStopsInfo(BusRouteId routeId) {
        String sql = """
            SELECT bs.id, bs.stop_name, bs.stop_code, bs.latitude, bs.longitude,
                   rs.stop_sequence, rs.estimated_travel_time_minutes, rs.distance_from_start_meters, 
                   rs.direction, bs.is_major_stop
            FROM route_stops rs
            JOIN bus_stops bs ON rs.stop_id = bs.id
            WHERE rs.route_id = :routeId
            AND bs.is_active = true
            ORDER BY rs.direction, rs.stop_sequence
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId.getValue())
                .map(this::mapToRouteStopInfo)
                .all();
    }

    @Override
    public Flux<RouteStopInfo> getRouteStopsInfoByRouteId(String routeId, Integer direction) {
        String sql = """
            SELECT bs.id, bs.stop_name, bs.stop_code, bs.latitude, bs.longitude,
                   rs.stop_sequence, rs.estimated_travel_time_minutes, rs.distance_from_start_meters, 
                   rs.direction, bs.is_major_stop
            FROM route_stops rs
            JOIN bus_stops bs ON rs.stop_id = bs.id
            JOIN bus_routes br ON rs.route_id = br.id
            WHERE br.id = :routeId 
            AND rs.direction = :direction
            AND bs.is_active = true AND br.is_active = true
            ORDER BY rs.stop_sequence
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .bind("direction", direction)
                .map(this::mapToRouteStopInfo)
                .all();
    }

    @Override
    public Mono<RouteVehicleStatistics> getRouteVehicleStatistics(BusRouteId routeId) {
        String sql = """
            SELECT 
                COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count,
                COUNT(v.id) FILTER (WHERE v.is_active = true AND v.is_in_motion = true) as vehicles_in_motion_count,
                COUNT(v.id) FILTER (WHERE v.is_active = true AND v.last_position_update > (CURRENT_TIMESTAMP - INTERVAL '5 minutes')) as vehicles_with_recent_position_count
            FROM vehicles v
            WHERE v.assigned_route_id = :routeId
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId.getValue())
                .map(this::mapToRouteVehicleStatistics)
                .one();
    }

    private BusRoute mapRowToBusRoute(Row row, RowMetadata metadata) {
        BusRouteEntity entity = BusRouteEntity.builder()
                .id(row.get("id", String.class))
                .routeNumber(row.get("route_number", String.class))
                .routeName(row.get("route_name", String.class))
                .nameTm(row.get("name_tm", String.class))
                .nameEn(row.get("name_en", String.class))
                .routeColor(row.get("route_color", String.class))
                .cityId(row.get("city_id", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .estimatedDurationMinutes(row.get("estimated_duration_minutes", Integer.class))
                .routeGeometryForward(row.get("route_geometry_forward", String.class))
                .routeGeometryBackward(row.get("route_geometry_backward", String.class))
                .totalDistanceForwardMeters(row.get("total_distance_forward_meters", Integer.class))
                .totalDistanceBackwardMeters(row.get("total_distance_backward_meters", Integer.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build();

        return entityMapper.toDomain(entity);
    }

    private RouteStopInfo mapToRouteStopInfo(Row row, RowMetadata metadata) {
        return new RouteStopInfo(
                row.get("id", String.class),
                row.get("stop_name", String.class),
                row.get("stop_code", String.class),
                row.get("stop_sequence", Integer.class),
                row.get("direction", Integer.class),
                row.get("estimated_travel_time_minutes", Integer.class),
                row.get("distance_from_start_meters", Integer.class),
                row.get("latitude", BigDecimal.class),
                row.get("longitude", BigDecimal.class),
                row.get("is_major_stop", Boolean.class)
        );
    }

    private RouteVehicleStatistics mapToRouteVehicleStatistics(Row row, RowMetadata metadata) {
        return new RouteVehicleStatistics(
                row.get("active_vehicles_count", Long.class),
                row.get("vehicles_in_motion_count", Long.class),
                row.get("vehicles_with_recent_position_count", Long.class)
        );
    }

    private RouteInAreaInfo mapToRouteInAreaInfo(Row row, RowMetadata metadata) {
        return new RouteInAreaInfo(
                row.get("route_id", String.class),
                row.get("route_number", String.class),
                row.get("route_name", String.class),
                row.get("route_color", String.class),
                row.get("direction", Integer.class),
                row.get("nearest_point_lat", Double.class),
                row.get("nearest_point_lon", Double.class),
                row.get("distance_to_center", Double.class)
        );
    }

    private String mapSortField(String sortField) {
        return switch (sortField != null ? sortField.toLowerCase() : "route_name") {
            case "routenumber" -> "route_number";
            case "nametm" -> "name_tm";
            case "nameen" -> "name_en";
            case "isactive", "active" -> "is_active";
            case "createdat", "created" -> "created_at";
            case "updatedat", "updated" -> "updated_at";
            default -> "route_name";
        };
    }

    @Override
    public Flux<BusRoute> findBySpecification(Specification<BusRoute> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT %s FROM bus_routes WHERE %s ORDER BY route_number ASC, created_at DESC",
            selectColumns(),
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
    public Flux<BusRoute> findBySpecification(Specification<BusRoute> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT %s FROM bus_routes WHERE %s %s LIMIT :limit OFFSET :offset",
            selectColumns(),
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
    public Mono<Long> countBySpecification(Specification<BusRoute> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT COUNT(*) FROM bus_routes WHERE %s",
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
    public Flux<BusRoute> searchWithRelevance(String query, Boolean isActive, Pageable pageable) {
        String searchQuery = query.trim().toLowerCase();
        String searchPattern = "%" + searchQuery + "%";

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append(String.format("""
            SELECT %s FROM bus_routes
            WHERE (LOWER(route_number) LIKE :searchPattern
                   OR LOWER(route_name) LIKE :searchPattern
                   OR LOWER(name_tm) LIKE :searchPattern
                   OR LOWER(name_en) LIKE :searchPattern)
            """, selectColumns()));

        if (isActive != null) {
            sqlBuilder.append(" AND is_active = :isActive");
        }

        sqlBuilder.append("""
            ORDER BY
                CASE
                    WHEN LOWER(route_number) = :exactQuery THEN 1
                    WHEN LOWER(route_number) LIKE :startsWithPattern THEN 2
                    WHEN LOWER(route_number) LIKE :searchPattern THEN 3
                    WHEN LOWER(route_name) LIKE :startsWithPattern THEN 4
                    ELSE 5
                END,
                LENGTH(route_number),
                route_number
            LIMIT :limit OFFSET :offset
            """);

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sqlBuilder.toString())
                .bind("searchPattern", searchPattern)
                .bind("exactQuery", searchQuery)
                .bind("startsWithPattern", searchQuery + "%")
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());

        if (isActive != null) {
            executeSpec = executeSpec.bind("isActive", isActive);
        }

        return executeSpec
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Relevance search completed for query: '{}'", query));
    }

    @Override
    public Mono<Long> countBySearch(String query, Boolean isActive) {
        String searchPattern = "%" + query.trim().toLowerCase() + "%";

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
            SELECT COUNT(*) FROM bus_routes
            WHERE (LOWER(route_number) LIKE :searchPattern
                   OR LOWER(route_name) LIKE :searchPattern
                   OR LOWER(name_tm) LIKE :searchPattern
                   OR LOWER(name_en) LIKE :searchPattern)
            """);

        if (isActive != null) {
            sqlBuilder.append(" AND is_active = :isActive");
        }

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sqlBuilder.toString())
                .bind("searchPattern", searchPattern);

        if (isActive != null) {
            executeSpec = executeSpec.bind("isActive", isActive);
        }

        return executeSpec
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<NearbyRouteInfo> findRoutesNearLocation(Double latitude, Double longitude, Integer radiusMeters) {
        log.debug("Searching for routes near ({}, {}) within {}m", latitude, longitude, radiusMeters);

        String sql = """
            SELECT DISTINCT ON (br.route_number)
                br.id as route_id,
                br.route_number,
                br.route_color,
                MIN(ST_Distance(
                    ST_SetSRID(ST_Point(bs.longitude, bs.latitude), 4326)::geography,
                    ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography
                )) OVER (PARTITION BY br.id) as min_distance
            FROM bus_stops bs
            JOIN route_stops rs ON bs.id = rs.stop_id
            JOIN bus_routes br ON rs.route_id = br.id
            WHERE bs.is_active = true
            AND br.is_active = true
            AND ST_DWithin(
                ST_SetSRID(ST_Point(bs.longitude, bs.latitude), 4326)::geography,
                ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography,
                :radiusMeters
            )
            ORDER BY br.route_number, min_distance
            """;

        return databaseClient.sql(sql)
                .bind("centerLat", latitude)
                .bind("centerLon", longitude)
                .bind("radiusMeters", radiusMeters)
                .map(this::mapToNearbyRouteInfo)
                .all()
                .doOnComplete(() -> log.debug("Completed search for routes near ({}, {}) within {}m",
                        latitude, longitude, radiusMeters));
    }

    private NearbyRouteInfo mapToNearbyRouteInfo(Row row, RowMetadata metadata) {
        return new NearbyRouteInfo(
                row.get("route_id", String.class),
                row.get("route_number", String.class),
                row.get("route_color", String.class)
        );
    }
}