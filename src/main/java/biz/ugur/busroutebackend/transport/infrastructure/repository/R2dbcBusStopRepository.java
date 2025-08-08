package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
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

@Repository
@Slf4j
public class R2dbcBusStopRepository implements BusStopRepository {

    protected final DatabaseClient databaseClient;

    public R2dbcBusStopRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<BusStop> save(BusStop busStop) {
        return findById(busStop.getId())
                .flatMap(existing -> updateExisting(busStop))
                .switchIfEmpty(insertNew(busStop))
                .doOnSuccess(saved -> log.debug("Bus stop saved: {}", saved.getStopName()))
                .doOnError(error -> log.error("Failed to save bus stop: {}", busStop.getStopName(), error));
    }

    @Override
    public Mono<BusStop> findById(BusStopId stopId) {
        String sql = "SELECT * FROM bus_stops WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", stopId.getValue())
                .map(this::mapRowToBusStop)
                .one()
                .doOnNext(stop -> log.debug("Found bus stop by ID: {}", stopId.getValue()));
    }

    @Override
    public Flux<BusStop> findStopsWithinRadius(Double centerLat, Double centerLon, Double radiusKm) {
        String sql = """
            SELECT *,
                   (6371 * acos(cos(radians(:centerLat)) * cos(radians(latitude)) 
                   * cos(radians(longitude) - radians(:centerLon)) 
                   + sin(radians(:centerLat)) * sin(radians(latitude)))) as distance
            FROM bus_stops 
            WHERE is_active = true 
            AND (6371 * acos(cos(radians(:centerLat)) * cos(radians(latitude)) 
                * cos(radians(longitude) - radians(:centerLon)) 
                + sin(radians(:centerLat)) * sin(radians(latitude)))) <= :radiusKm
            ORDER BY distance
            LIMIT 10
            """;

        return databaseClient.sql(sql)
                .bind("centerLat", centerLat)
                .bind("centerLon", centerLon)
                .bind("radiusKm", radiusKm)
                .map(this::mapRowToBusStop)
                .all()
                .doOnComplete(() -> log.debug("Found stops within {}km of ({}, {})",
                        radiusKm, centerLat, centerLon));
    }

    private Mono<BusStop> insertNew(BusStop busStop) {
        String sql = """
            INSERT INTO bus_stops (id, stop_name, name_en, name_tm, stop_code, latitude, longitude, 
                                  is_active, is_major_stop, created_at, updated_at, version, city_id)
            VALUES (:id, :stopName, :nameEn, :nameTm, :stopCode, :latitude, :longitude, 
                   :isActive, :isMajorStop, NOW(), NOW(), 0, :cityId)
            """;

        return databaseClient.sql(sql)
                .bind("id", busStop.getId().getValue())
                .bind("stopName", busStop.getStopName())
                .bind("nameEn", busStop.getNameEn())
                .bind("nameTm", busStop.getNameTm())
                .bind("stopCode", busStop.getStopCode().getValue())
                .bind("latitude", busStop.getLatitude())
                .bind("longitude", busStop.getLongitude())
                .bind("isActive", busStop.getIsActive())
                .bind("isMajorStop", busStop.getIsMajorStop())
                .bind("cityId", busStop.getCityId())
                .then()
                .thenReturn(busStop);
    }

    private Mono<BusStop> updateExisting(BusStop busStop) {
        String sql = """
            UPDATE bus_stops 
            SET stop_name = :stopName, name_en = :nameEn, name_tm = :nameTm, stop_code = :stopCode, 
                latitude = :latitude, longitude = :longitude,
                is_active = :isActive, is_major_stop = :isMajorStop,
                updated_at = NOW(), version = version + 1
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("stopName", busStop.getStopName())
                .bind("nameEn", busStop.getNameEn())
                .bind("nameTm", busStop.getNameTm())
                .bind("stopCode", busStop.getStopCode().getValue())
                .bind("latitude", busStop.getLatitude())
                .bind("longitude", busStop.getLongitude())
                .bind("isActive", busStop.getIsActive())
                .bind("isMajorStop", busStop.getIsMajorStop())
                .bind("id", busStop.getId().getValue())
                .then()
                .thenReturn(busStop);
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
                .map(this::mapRowToBusStop)
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
                .map(this::mapRowToBusStop)
                .all();
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
                .map(this::mapRowToBusStop)
                .all();
    }

    @Override
    public Flux<BusStop> findActiveStops() {
        String sql = "SELECT * FROM bus_stops WHERE is_active = true ORDER BY stop_name";

        return databaseClient.sql(sql)
                .map(this::mapRowToBusStop)
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
    public Mono<Void> deleteById(BusStopId stopId) {
        String sql = "DELETE FROM bus_stops WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", stopId.getValue())
                .then();
    }

    @Override
    public Mono<Long> countActiveStops() {
        String sql = "SELECT COUNT(*) FROM bus_stops WHERE is_active = true";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<BusStop> findAllWithPagination(Pageable pageable) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM bus_stops");

        sqlBuilder.append(" ORDER BY ");
        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            String sortField = mapSortField(order.getProperty());
            String direction = order.getDirection().name();
            sqlBuilder.append(sortField).append(" ").append(direction);
        } else {
            sqlBuilder.append("stop_name ASC");
        }

        sqlBuilder.append(" LIMIT :limit OFFSET :offset");

        return databaseClient.sql(sqlBuilder.toString())
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(this::mapRowToBusStop)
                .all()
                .doOnComplete(() -> log.debug("Found stops with pagination: page={}, size={}",
                        pageable.getPageNumber(), pageable.getPageSize()));
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

        return new BusStop(
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