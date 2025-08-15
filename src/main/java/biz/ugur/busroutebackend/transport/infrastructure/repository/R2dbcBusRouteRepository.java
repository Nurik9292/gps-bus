package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.RouteWithGeometryDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteInAreaInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteVehicleStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.math.RoundingMode;
import java.time.Instant;

@Repository
@Slf4j
public class R2dbcBusRouteRepository implements BusRouteRepository {

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public R2dbcBusRouteRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<BusRoute> save(BusRoute busRoute) {
        return findById(busRoute.getId())
                .flatMap(existing -> update(busRoute))
                .switchIfEmpty(insert(busRoute))
                .doOnSuccess(saved -> log.debug("Bus stop saved: {}", saved.getRouteName()))
                .doOnError(error -> log.error("Failed to save bus stop: {}", busRoute.getRouteName(), error));

    }


    private Mono<BusRoute> insert(BusRoute busRoute) {
        String sql = """
        INSERT INTO bus_routes (
            id, route_number, route_name, name_tm, name_en, route_color, 
            is_active, city_id, estimated_duration_minutes,
            route_geometry_forward, route_geometry_backward,
            geometry_forward, geometry_backward,
            total_distance_forward_meters, total_distance_backward_meters,
            created_at, updated_at
        ) VALUES (
            :id, :routeNumber, :routeName, :nameTm, :nameEn, :routeColor,
            :isActive, :cityId, :estimatedDurationMinutes,
            :routeGeometryForward, :routeGeometryBackward,
            CASE 
                WHEN :routeGeometryForward IS NOT NULL 
                THEN ST_GeomFromText(:routeGeometryForward, 4326)::geometry
                ELSE NULL
            END,
            CASE 
                WHEN :routeGeometryBackward IS NOT NULL 
                THEN ST_GeomFromText(:routeGeometryBackward, 4326)::geometry
                ELSE NULL
            END,
            :totalDistanceForwardMeters, :totalDistanceBackwardMeters,
            :createdAt, :updatedAt
        )
        """;

        return databaseClient.sql(sql)
                .bind("id", busRoute.getId().getValue())
                .bind("routeNumber", busRoute.getRouteNumber())
                .bind("routeName", busRoute.getRouteName())
                .bind("nameTm", busRoute.getNameTm())
                .bind("nameEn", busRoute.getNameEn())
                .bind("routeColor", busRoute.getRouteColor())
                .bind("isActive", busRoute.getIsActive())
                .bind("cityId", busRoute.getCityId())
                .bind("estimatedDurationMinutes", busRoute.getEstimatedDurationMinutes())
                .bind("routeGeometryForward", busRoute.getRouteGeometryForward())
                .bind("routeGeometryBackward", busRoute.getRouteGeometryBackward())
                .bind("totalDistanceForwardMeters", busRoute.getTotalDistanceForwardMeters())
                .bind("totalDistanceBackwardMeters", busRoute.getTotalDistanceBackwardMeters())
                .bind("createdAt", busRoute.getCreatedAt())
                .bind("updatedAt", busRoute.getUpdatedAt())
                .then()
                .thenReturn(busRoute)
                .doOnSuccess(route -> log.info("New route {} created successfully with geometry", route.getRouteNumber()))
                .doOnError(error -> log.error("Failed to create route {}: {}", busRoute.getRouteNumber(), error.getMessage()));
    }

    private Mono<BusRoute> update(BusRoute busRoute) {
        log.debug("Updating route rdb {}", busRoute);
        log.debug("Updating route rdb2 created {}", busRoute.getCreatedAt());
        String sql = """
        UPDATE bus_routes SET
            route_name = :routeName,
            name_tm = :nameTm,
            name_en = :nameEn,
            route_color = :routeColor,
            is_active = :isActive,
            city_id = :cityId,
            estimated_duration_minutes = :estimatedDurationMinutes,
            route_geometry_forward = :routeGeometryForward,
            route_geometry_backward = :routeGeometryBackward,
            geometry_forward = CASE 
                WHEN :routeGeometryForward IS NOT NULL 
                THEN ST_GeomFromText(:routeGeometryForward, 4326)::geometry
                ELSE geometry_forward
            END,
            geometry_backward = CASE 
                WHEN :routeGeometryBackward IS NOT NULL 
                THEN ST_GeomFromText(:routeGeometryBackward, 4326)::geometry
                ELSE geometry_backward
            END,
            total_distance_forward_meters = :totalDistanceForwardMeters,
            total_distance_backward_meters = :totalDistanceBackwardMeters,
            created_at = :createdAt,
            updated_at = :updatedAt
        WHERE id = :id
        """;

        return databaseClient.sql(sql)
                .bind("id", busRoute.getId().getValue())
                .bind("routeName", busRoute.getRouteName())
                .bind("nameTm", busRoute.getNameTm())
                .bind("nameEn", busRoute.getNameEn())
                .bind("routeColor", busRoute.getRouteColor())
                .bind("isActive", busRoute.getIsActive())
                .bind("cityId", busRoute.getCityId())
                .bind("estimatedDurationMinutes", busRoute.getEstimatedDurationMinutes())
                .bind("routeGeometryForward", busRoute.getRouteGeometryForward())
                .bind("routeGeometryBackward", busRoute.getRouteGeometryBackward())
                .bind("totalDistanceForwardMeters", busRoute.getTotalDistanceForwardMeters())
                .bind("totalDistanceBackwardMeters", busRoute.getTotalDistanceBackwardMeters())
                .bind("createdAt", busRoute.getCreatedAt())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(busRoute)
                .doOnSuccess(route -> log.info("Route {} updated successfully with geometry", route.getRouteNumber()))
                .doOnError(error -> log.error("Failed to update route {}: {}", busRoute.getRouteNumber(), error.getMessage()));
    }

    @Override
    public Mono<BusRoute> findById(BusRouteId routeId) {
        String sql = "SELECT * FROM bus_routes WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", routeId.getValue())
                .map(this::mapRowToBusRoute)
                .one();
    }

    @Override
    public Flux<BusRoute> getRoutesWithPagination(Pageable pageable) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT br.* FROM bus_routes br ");
        sqlBuilder.append("WHERE (:activeOnly = false OR br.is_active = true) ");

        sqlBuilder.append(" ORDER BY ");
        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            String sortField = mapSortField(order.getProperty());
            String direction = order.getDirection().name();
            sqlBuilder.append(sortField).append(" ").append(direction);
        } else {
            sqlBuilder.append("route_number ASC");
        }

        sqlBuilder.append(" LIMIT :limit OFFSET :offset");

        return databaseClient.sql(sqlBuilder.toString())
                .bind("activeOnly", true)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(this::mapRowToBusRoute)
                .all()
                .doOnComplete(() -> log.debug("Found routes with pagination: page={}, size={}",
                        pageable.getPageNumber(), pageable.getPageSize()));
    }

    @Override
    public Mono<BusRoute> findByRouteNumber(String routeNumber) {
        String sql = "SELECT * FROM bus_routes WHERE route_number = :routeNumber AND is_active = true";

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .map(this::mapRowToBusRoute)
                .one();
    }

    @Override
    public Flux<BusRoute> findActiveRoutes() {
        String sql = "SELECT * FROM bus_routes WHERE is_active = true ORDER BY route_number";

        return databaseClient.sql(sql)
                .map(this::mapRowToBusRoute)
                .all();
    }

    @Override
    public Mono<Void> deleteById(BusRouteId routeId) {
        String sql = "DELETE FROM bus_routes WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", routeId.getValue())
                .then();
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
                .map(this::mapRowToBusRoute)
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
        return switch (sortField != null ? sortField.toLowerCase() : "stop_name") {
            case "routnumber" -> "rout_number";
            case "nametm" -> "name_tm";
            case "nameen" -> "name_en";
            case "latitude" -> "latitude";
            case "longitude" -> "longitude";
            case "isactive", "active" -> "is_active";
            case "createdat", "created" -> "created_at";
            case "updatedat", "updated" -> "updated_at";
            default -> "route_name";
        };
    }
}