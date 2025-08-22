package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteInAreaInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteVehicleStatistics;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
public class R2dbcBusRouteRepository extends BaseR2dbcRepository<BusRoute, BusRouteId>
        implements BusRouteRepository {

    public R2dbcBusRouteRepository(DatabaseClient databaseClient) {
        super(databaseClient, "bus_routes", BusRoute.class);
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
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId().getValue());
        columns.put("route_number", entity.getRouteNumber());
        columns.put("route_name", entity.getRouteName());
        columns.put("name_tm", entity.getNameTm());
        columns.put("name_en", entity.getNameEn());
        columns.put("route_color", entity.getRouteColor());
        columns.put("is_active", entity.getIsActive());
        columns.put("city_id", entity.getCityId());
        columns.put("estimated_duration_minutes", entity.getEstimatedDurationMinutes());
        columns.put("route_geometry_forward", entity.getRouteGeometryForward());
        columns.put("route_geometry_backward", entity.getRouteGeometryBackward());
        columns.put("total_distance_forward_meters", entity.getTotalDistanceForwardMeters());
        columns.put("total_distance_backward_meters", entity.getTotalDistanceBackwardMeters());
        return columns;
    }

    @Override
    protected Mono<BusRoute> insert(BusRoute entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
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
                .doOnSuccess(route -> log.info("New route {} created successfully with geometry",
                        route.getRouteNumber()))
                .doOnError(error -> log.error("Failed to create route {}: {}",
                        entity.getRouteNumber(), error.getMessage()));
    }

    @Override
    protected Mono<BusRoute> update(BusRoute entity) {
        Map<String, Object> values = mapEntityToColumns(entity);
        values.put("updated_at", Instant.now());
        values.put("version", entity.getVersion() + 1);

        String sql = """
            UPDATE bus_routes SET
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
    public Mono<BusRoute> findByRouteNumber(String routeNumber) {
        String sql = "SELECT * FROM bus_routes WHERE route_number = :routeNumber AND is_active = true";

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Flux<BusRoute> findActiveRoutes() {
        String sql = "SELECT * FROM bus_routes WHERE is_active = true ORDER BY route_number";

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }


    @Override
    public Mono<Boolean> existsByRouteNumber(String routeNumber) {
        String sql = "SELECT COUNT(*) FROM bus_routes WHERE route_number = :routeNumber";

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
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
        String sql = """
            SELECT * FROM bus_routes br
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
            """;

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
    public Flux<RouteStopInfo> getRouteStopsInfoByNumber(String routeNumber, Integer direction) {
        String sql = """
            SELECT bs.id, bs.stop_name, bs.stop_code, bs.latitude, bs.longitude,
                   rs.stop_sequence, rs.estimated_travel_time_minutes, rs.distance_from_start_meters, 
                   rs.direction, bs.is_major_stop
            FROM route_stops rs
            JOIN bus_stops bs ON rs.stop_id = bs.id
            JOIN bus_routes br ON rs.route_id = br.id
            WHERE br.route_number = :routeNumber 
            AND rs.direction = :direction
            AND bs.is_active = true AND br.is_active = true
            ORDER BY rs.stop_sequence
            """;

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
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
        BusRoute busRoute = new BusRoute(
                BusRouteId.of(row.get("id", String.class)),
                row.get("route_number", String.class),
                row.get("route_name", String.class),
                row.get("name_tm", String.class),
                row.get("name_en", String.class),
                row.get("route_color", String.class),
                row.get("city_id", String.class),
                row.get("is_active", Boolean.class),
                row.get("estimated_duration_minutes", Integer.class),
                row.get("route_geometry_forward", String.class),
                row.get("route_geometry_backward", String.class),
                row.get("total_distance_forward_meters", Integer.class),
                row.get("total_distance_backward_meters", Integer.class)
        );

        busRoute.setCreatedAt(row.get("created_at", Instant.class));
        busRoute.setUpdatedAt(row.get("updated_at", Instant.class));
        busRoute.setVersion(row.get("version", Long.class));

        return busRoute;
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
}