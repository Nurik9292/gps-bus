package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.repository.BusRouteConnectionRepository;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
@Slf4j
@RequiredArgsConstructor
public class R2dbcBusRouteConnectionRepository implements BusRouteConnectionRepository {

    private final DatabaseClient databaseClient;

    @Override
    public Flux<BusRoute> findConnectingRoutes(BusStop fromStop, BusStop toStop) {
        log.debug("🔍 Finding connecting routes: {} → {}", fromStop.getStopName(), toStop.getStopName());

        String sql = """
            SELECT DISTINCT br.*
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id
                                AND rs1.direction = rs2.direction
                                AND rs1.stop_sequence < rs2.stop_sequence
            JOIN bus_routes br ON rs1.route_id = br.id
            WHERE rs1.stop_id = :fromStopId
              AND rs2.stop_id = :toStopId
              AND br.is_active = true
            ORDER BY ABS(rs2.stop_sequence - rs1.stop_sequence)
            LIMIT 10
            """;

        return databaseClient.sql(sql)
                .bind("fromStopId", fromStop.getId().getValue())
                .bind("toStopId", toStop.getId().getValue())
                .map(this::mapToBusRoute)
                .all()
                .doOnComplete(() -> log.debug("✅ Completed connecting routes search"));
    }

    @Override
    public Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2) {
        return findConnectingRoutes(stop1, stop2)
                .hasElements()
                .doOnNext(connected -> {
                    if (connected) {
                        log.debug("✅ Stops {} and {} are connected", stop1.getStopName(), stop2.getStopName());
                    } else {
                        log.debug("❌ Stops {} and {} are NOT connected", stop1.getStopName(), stop2.getStopName());
                    }
                });
    }

    private BusRoute mapToBusRoute(Row row, io.r2dbc.spi.RowMetadata metadata) {
        return BusRoute.builder()
                .routeNumber(row.get("route_number", String.class))
                .routeName(row.get("route_name", String.class))
                .nameTm(row.get("name_tm", String.class))
                .nameEn(row.get("name_en", String.class))
                .routeColor(row.get("route_color", String.class))
                .build();
    }
}
