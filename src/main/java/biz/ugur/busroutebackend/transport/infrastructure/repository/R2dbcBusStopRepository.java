package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
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
        // Реализация сохранения - пока заглушка
        return Mono.just(busStop);
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

    // ГЛАВНЫЙ МЕТОД ДЛЯ TRIP PLANNING
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

    @Override
    public Flux<BusStop> findByStopName(String stopName) {
        String sql = "SELECT * FROM bus_stops WHERE stop_name ILIKE :stopName AND is_active = true";

        return databaseClient.sql(sql)
                .bind("stopName", "%" + stopName + "%")
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

    private BusStop mapRowToBusStop(Row row, RowMetadata metadata) {
        return new BusStop(
                BusStopId.of(row.get("id", String.class)),
                row.get("stop_name", String.class),
                row.get("stop_code", String.class),
                row.get("latitude", BigDecimal.class),
                row.get("longitude", BigDecimal.class),
                row.get("is_active", Boolean.class),
                row.get("is_major_stop", Boolean.class),
                row.get("has_shelter", Boolean.class)
        );
    }
}