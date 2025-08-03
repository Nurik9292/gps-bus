package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.RouteWithGeometryDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
    public Mono<RouteWithGeometryDTO> findByRouteNumberWithGeometry(String routeNumber) {
        String sql = """
            SELECT id, route_number, route_name, route_color, 
                   route_geometry_forward, route_geometry_backward,
                   total_distance_forward_meters, total_distance_backward_meters
            FROM bus_routes 
            WHERE route_number = :routeNumber AND is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .map(this::mapToRouteWithGeometryDTO)
                .one()
                .doOnNext(route -> log.debug("Found route with geometry: {}", routeNumber));
    }

    @Override
    public Flux<RouteWithGeometryDTO> findAllActiveWithBasicInfo() {
        String sql = """
            SELECT br.id, br.route_number, br.route_name, br.route_color,
                   br.total_distance_forward_meters, br.total_distance_backward_meters,
                   COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count
            FROM bus_routes br
            LEFT JOIN vehicles v ON br.id = v.assigned_route_id
            WHERE br.is_active = true
            GROUP BY br.id, br.route_number, br.route_name, br.route_color,
                     br.total_distance_forward_meters, br.total_distance_backward_meters
            ORDER BY br.route_number
            """;

        return databaseClient.sql(sql)
                .map(this::mapToBasicRouteDTO)
                .all();
    }

    @Override
    public Flux<RouteStopDTO> findRouteStopsOrdered(String routeNumber, Integer direction) {
        String sql = """
            SELECT bs.id, bs.stop_name, bs.stop_code, bs.latitude, bs.longitude,
                   rs.stop_sequence, rs.estimated_travel_time_minutes, rs.distance_from_start_meters,
                   bs.is_major_stop, bs.has_shelter
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
                .map(this::mapToRouteStopDTO)
                .all();
    }

    @Override
    public Mono<RouteVehicleStatistics> getRouteVehicleStatistics(String routeId) {
        String sql = """
            SELECT 
                COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count,
                COUNT(v.id) FILTER (WHERE v.is_active = true AND v.is_in_motion = true) as vehicles_in_motion_count,
                COUNT(v.id) FILTER (WHERE v.is_active = true AND v.last_position_update > (CURRENT_TIMESTAMP - INTERVAL '5 minutes')) as vehicles_with_recent_position_count
            FROM vehicles v
            WHERE v.assigned_route_id = :routeId
            """;

        return databaseClient.sql(sql)
                .bind("routeId", routeId)
                .map(row -> new RouteVehicleStatistics(
                        row.get("active_vehicles_count", Long.class),
                        row.get("vehicles_in_motion_count", Long.class),
                        row.get("vehicles_with_recent_position_count", Long.class)
                ))
                .one();
    }

    @Override
    public Mono<BusRoute> save(BusRoute busRoute) {
        if (busRoute.getId() == null) {
            return insert(busRoute);
        } else {
            return update(busRoute);
        }
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
    public Mono<Boolean> existsByRouteNumber(String routeNumber) {
        String sql = "SELECT COUNT(*) FROM bus_routes WHERE route_number = :routeNumber";

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    public Mono<Void> deleteById(BusRouteId routeId) {
        String sql = "DELETE FROM bus_routes WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", routeId.getValue())
                .then();
    }

    @Override
    public Mono<Long> countActiveRoutes() {
        String sql = "SELECT COUNT(*) FROM bus_routes WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }


    private RouteWithGeometryDTO mapToRouteWithGeometryDTO(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        RouteWithGeometryDTO dto = new RouteWithGeometryDTO();
        dto.setRouteId(row.get("id", String.class));
        dto.setRouteNumber(row.get("route_number", String.class));
        dto.setRouteName(row.get("route_name", String.class));
        dto.setRouteColor(row.get("route_color", String.class));

        String forwardGeometry = row.get("route_geometry_forward", String.class);
        String backwardGeometry = row.get("route_geometry_backward", String.class);

        try {
            if (forwardGeometry != null) {
                dto.setGeometryForward(objectMapper.readValue(forwardGeometry, Object.class));
            }
            if (backwardGeometry != null) {
                dto.setGeometryBackward(objectMapper.readValue(backwardGeometry, Object.class));
            }
        } catch (Exception e) {
            log.warn("Failed to parse route geometry for route {}: {}", dto.getRouteNumber(), e.getMessage());
        }

        // Конвертируем расстояния в километры
        Integer forwardMeters = row.get("total_distance_forward_meters", Integer.class);
        Integer backwardMeters = row.get("total_distance_backward_meters", Integer.class);

        if (forwardMeters != null) {
            dto.setTotalDistanceForwardKm(new BigDecimal(forwardMeters).divide(new BigDecimal(1000), 2, BigDecimal.ROUND_HALF_UP));
        }
        if (backwardMeters != null) {
            dto.setTotalDistanceBackwardKm(new BigDecimal(backwardMeters).divide(new BigDecimal(1000), 2, BigDecimal.ROUND_HALF_UP));
        }

        return dto;
    }

    private RouteWithGeometryDTO mapToBasicRouteDTO(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        RouteWithGeometryDTO dto = new RouteWithGeometryDTO();
        dto.setRouteId(row.get("id", String.class));
        dto.setRouteNumber(row.get("route_number", String.class));
        dto.setRouteName(row.get("route_name", String.class));
        dto.setRouteColor(row.get("route_color", String.class));
        dto.setActiveVehiclesCount(row.get("active_vehicles_count", Long.class));

        Integer forwardMeters = row.get("total_distance_forward_meters", Integer.class);
        if (forwardMeters != null) {
            dto.setTotalDistanceForwardKm(new BigDecimal(forwardMeters).divide(new BigDecimal(1000), 2, BigDecimal.ROUND_HALF_UP));
        }

        return dto;
    }

    private RouteStopDTO mapToRouteStopDTO(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new RouteStopDTO(
                row.get("id", String.class),
                row.get("stop_name", String.class),
                row.get("stop_code", String.class),
                row.get("latitude", Double.class),
                row.get("longitude", Double.class),
                row.get("stop_sequence", Integer.class),
                row.get("estimated_travel_time_minutes", Integer.class),
                row.get("distance_from_start_meters", Integer.class),
                row.get("is_major_stop", Boolean.class),
                row.get("has_shelter", Boolean.class)
        );
    }

    private RouteInAreaResult mapToRouteInAreaResult(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        // Извлекаем координаты из PostGIS Point геометрии
        Object intersectionPoint = row.get("intersection_point");
        Double lat = null, lon = null;

        // Здесь нужно парсить PostGIS Point, но для простоты используем заглушку
        // В реальной реализации нужно использовать ST_X, ST_Y функции в SQL

        return new RouteInAreaResult(
                row.get("route_id", String.class),
                row.get("route_number", String.class),
                row.get("route_name", String.class),
                row.get("route_color", String.class),
                row.get("direction", Integer.class),
                lat != null ? lat : 0.0, // Временная заглушка
                lon != null ? lon : 0.0, // Временная заглушка
                row.get("distance_to_center", Double.class),
                0L // Пока без подсчета автобусов
        );
    }

    private BusRoute mapRowToBusRoute(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new BusRoute(
                BusRouteId.of(row.get("id", String.class)),
                row.get("route_number", String.class),
                row.get("route_name", String.class),
                row.get("route_name_tm", String.class),
                row.get("route_color", String.class),
                row.get("is_active", Boolean.class),
                row.get("fare_price", BigDecimal.class),
                row.get("estimated_duration_minutes", Integer.class),
                row.get("route_geometry_forward", String.class),
                row.get("route_geometry_backward", String.class),
                row.get("total_distance_forward_meters", Integer.class),
                row.get("total_distance_backward_meters", Integer.class)
        );
    }

    private Mono<BusRoute> insert(BusRoute busRoute) {
        String sql = """
            INSERT INTO bus_routes (id, route_number, route_name, route_name_tm, route_color,
                                   is_active, fare_price, estimated_duration_minutes,
                                   route_geometry_forward, route_geometry_backward,
                                   total_distance_forward_meters, total_distance_backward_meters,
                                   created_at, updated_at, version)
            VALUES (:id, :routeNumber, :routeName, :routeNameTm, :routeColor,
                   :isActive, :farePrice, :estimatedDurationMinutes,
                   :routeGeometryForward, :routeGeometryBackward,
                   :totalDistanceForwardMeters, :totalDistanceBackwardMeters,
                   :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();
        return databaseClient.sql(sql)
                .bind("id", busRoute.getId().getValue())
                .bind("routeNumber", busRoute.getRouteNumber())
                .bind("routeName", busRoute.getRouteName())
                .bind("routeNameTm", busRoute.getRouteNameTm())
                .bind("routeColor", busRoute.getRouteColor())
                .bind("isActive", busRoute.getIsActive())
                .bind("farePrice", busRoute.getFarePrice())
                .bind("estimatedDurationMinutes", busRoute.getEstimatedDurationMinutes())
                .bind("routeGeometryForward", busRoute.getRouteGeometryForward())
                .bind("routeGeometryBackward", busRoute.getRouteGeometryBackward())
                .bind("totalDistanceForwardMeters", busRoute.getTotalDistanceForwardMeters())
                .bind("totalDistanceBackwardMeters", busRoute.getTotalDistanceBackwardMeters())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 0L)
                .then()
                .thenReturn(busRoute);
    }

    private Mono<BusRoute> update(BusRoute busRoute) {
        String sql = """
            UPDATE bus_routes 
            SET route_number = :routeNumber, route_name = :routeName, route_name_tm = :routeNameTm,
                route_color = :routeColor, is_active = :isActive, fare_price = :farePrice,
                estimated_duration_minutes = :estimatedDurationMinutes,
                route_geometry_forward = :routeGeometryForward, route_geometry_backward = :routeGeometryBackward,
                total_distance_forward_meters = :totalDistanceForwardMeters, total_distance_backward_meters = :totalDistanceBackwardMeters,
                updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", busRoute.getId().getValue())
                .bind("routeNumber", busRoute.getRouteNumber())
                .bind("routeName", busRoute.getRouteName())
                .bind("routeNameTm", busRoute.getRouteNameTm())
                .bind("routeColor", busRoute.getRouteColor())
                .bind("isActive", busRoute.getIsActive())
                .bind("farePrice", busRoute.getFarePrice())
                .bind("estimatedDurationMinutes", busRoute.getEstimatedDurationMinutes())
                .bind("routeGeometryForward", busRoute.getRouteGeometryForward())
                .bind("routeGeometryBackward", busRoute.getRouteGeometryBackward())
                .bind("totalDistanceForwardMeters", busRoute.getTotalDistanceForwardMeters())
                .bind("totalDistanceBackwardMeters", busRoute.getTotalDistanceBackwardMeters())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(busRoute);
    }

    @Override
    public Flux<RouteWithGeometryDTO> searchRoutesByNameOrNumber(String query, Integer limit) {
        String sql = """
            SELECT id, route_number, route_name, route_color, 
                   total_distance_forward_meters, total_distance_backward_meters,
                   COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count
            FROM bus_routes br
            LEFT JOIN vehicles v ON br.id = v.assigned_route_id
            WHERE br.is_active = true 
            AND (LOWER(br.route_number) LIKE LOWER(:query) 
                 OR LOWER(br.route_name) LIKE LOWER(:searchPattern)
                 OR LOWER(br.route_name_tm) LIKE LOWER(:searchPattern))
            GROUP BY br.id, br.route_number, br.route_name, br.route_color,
                     br.total_distance_forward_meters, br.total_distance_backward_meters
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
                .map(this::mapToBasicRouteDTO)
                .all()
                .doOnComplete(() -> log.debug("Route search completed for query: '{}'", query));
    }

    @Override
    public Flux<RouteInAreaResult> findRoutesIntersectingArea(Double latitude, Double longitude, Integer radiusMeters) {
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
            SELECT 
                ri.*,
                COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count,
                COUNT(v.id) FILTER (WHERE v.is_active = true AND v.is_in_motion = true) as vehicles_in_motion_count
            FROM route_intersections ri
            LEFT JOIN vehicles v ON ri.route_id = v.assigned_route_id
            GROUP BY ri.route_id, ri.route_number, ri.route_name, ri.route_color, 
                     ri.direction, ri.nearest_point_lat, ri.nearest_point_lon, ri.distance_to_center
            ORDER BY ri.distance_to_center
            """;

        return databaseClient.sql(sql)
                .bind("centerLat", latitude)
                .bind("centerLon", longitude)
                .bind("radiusMeters", radiusMeters)
                .map(this::mapToRouteInAreaResult)
                .all()
                .doOnComplete(() -> log.debug("Found routes intersecting area at ({}, {}) within {}m",
                        latitude, longitude, radiusMeters));
    }


}