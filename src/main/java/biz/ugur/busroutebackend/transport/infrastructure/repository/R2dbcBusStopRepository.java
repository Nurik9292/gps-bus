package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISQueryBuilder;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
public class R2dbcBusStopRepository extends BaseR2dbcRepository<BusStop, BusStopId>
        implements BusStopRepository {

    public R2dbcBusStopRepository(DatabaseClient databaseClient) {
        super(databaseClient, "bus_stops", BusStop.class);
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
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId().getValue());
        columns.put("stop_name", entity.getStopName());
        columns.put("name_en", entity.getNameEn());
        columns.put("name_tm", entity.getNameTm());
        columns.put("stop_code", entity.getStopCode().getValue());
        columns.put("latitude", entity.getLatitude());
        columns.put("longitude", entity.getLongitude());
        columns.put("is_active", entity.getIsActive());
        columns.put("is_major_stop", entity.getIsMajorStop());
        columns.put("city_id", entity.getCityId());
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

        // Generate PostGIS query fragments using centralized utility
        PostGISQueryBuilder.QueryFragment query = PostGISQueryBuilder.nearbyPointsQuery(
                "longitude", "latitude",
                ":centerLon", ":centerLat",
                ":radiusMeters",
                "distance_km", "km"
        );

        String sql = String.format("""
            SELECT *,
                   %s
            FROM bus_stops
            WHERE is_active = true
            AND %s
            ORDER BY %s
            LIMIT 15
            """,
                query.distanceColumn(),
                query.withinRadiusCondition(),
                query.orderByClause()
        );

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
        String id = row.get("id", String.class);
        String stopName = row.get("stop_name", String.class);
        String cityId = row.get("city_id", String.class);
        String nameEn = safeGet(row, "name_en", String.class, null);
        String nameTm = safeGet(row, "name_tm", String.class, null);
        String stopCode = row.get("stop_code", String.class);
        BigDecimal latitude = row.get("latitude", BigDecimal.class);
        BigDecimal longitude = row.get("longitude", BigDecimal.class);
        Boolean isActive = row.get("is_active", Boolean.class);
        Boolean isMajorStop = safeGet(row, "is_major_stop", Boolean.class, false);

        BusStop busStop = new BusStop(
                BusStopId.of(id),
                stopName,
                nameEn,
                nameTm,
                StopCode.of(stopCode),
                latitude,
                longitude,
                isActive,
                isMajorStop,
                cityId
        );

        busStop.setCreatedAt(safeGet(row, "created_at", java.time.Instant.class, null));
        busStop.setUpdatedAt(safeGet(row, "updated_at", java.time.Instant.class, null));
        busStop.setVersion(safeGet(row, "version", Long.class, 0L));

        return busStop;
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
}