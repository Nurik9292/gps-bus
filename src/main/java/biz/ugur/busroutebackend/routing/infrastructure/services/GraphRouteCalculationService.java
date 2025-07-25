package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import io.r2dbc.spi.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class GraphRouteCalculationService implements RouteCalculationService {

    private final BusStopRepository busStopRepository;
    private final BusRouteRepository busRouteRepository;
    private final DatabaseClient databaseClient;

    public GraphRouteCalculationService(BusStopRepository busStopRepository,
                                        BusRouteRepository busRouteRepository,
                                        DatabaseClient databaseClient) {
        this.busStopRepository = busStopRepository;
        this.busRouteRepository = busRouteRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<BusStop> findNearbyStops(Location location, double radiusKm) {
        log.debug("Finding stops within {}km of ({}, {})", radiusKm, location.getLatitude(), location.getLongitude());

        return busStopRepository.findStopsWithinRadius(
                location.getLatitude(),
                location.getLongitude(),
                radiusKm
        ).doOnNext(stop -> log.trace("Found nearby stop: {} at distance {}m",
                stop.getStopName(), location.distanceTo(stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue())));
    }

    @Override
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        log.debug("Finding direct routes between {} origin stops and {} destination stops",
                fromStops.size(), toStops.size());

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        String sql = """
            SELECT DISTINCT 
                br.id as route_id, br.route_number, br.route_name, br.route_color,
                rs1.stop_id as from_stop_id, bs1.stop_name as from_stop_name,
                bs1.latitude as from_lat, bs1.longitude as from_lon,
                rs2.stop_id as to_stop_id, bs2.stop_name as to_stop_name,
                bs2.latitude as to_lat, bs2.longitude as to_lon,
                rs1.stop_sequence as from_sequence, rs2.stop_sequence as to_sequence,
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as estimated_travel_minutes
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            JOIN bus_routes br ON rs1.route_id = br.id
            JOIN bus_stops bs1 ON rs1.stop_id = bs1.id  
            JOIN bus_stops bs2 ON rs2.stop_id = bs2.id
            WHERE rs1.stop_id = ANY(:fromStopIds) 
            AND rs2.stop_id = ANY(:toStopIds)
            AND rs1.stop_id != rs2.stop_id
            AND ABS(rs2.stop_sequence - rs1.stop_sequence) > 0
            AND br.is_active = true
            AND bs1.is_active = true 
            AND bs2.is_active = true
            ORDER BY estimated_travel_minutes, ABS(rs2.stop_sequence - rs1.stop_sequence)
            LIMIT 10
            """;

        String[] fromStopIds = fromStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);
        String[] toStopIds = toStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);

        return databaseClient.sql(sql)
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .map(row -> {
                    // Create BusRoute from row data
                    BusRoute route = new BusRoute(
                            row.get("route_number", String.class),
                            row.get("route_name", String.class),
                            null, // route_name_tm not needed here
                            row.get("route_color", String.class) != null ?
                                    row.get("route_color", String.class) : "#1976D2"
                    );

                    // Create BusStops from row data
                    BusStop fromStop = new BusStop(
                            row.get("from_stop_name", String.class),
                            row.get("from_stop_id", String.class),
                            row.get("from_lat", BigDecimal.class),
                            row.get("from_lon", BigDecimal.class)
                    );

                    BusStop toStop = new BusStop(
                            row.get("to_stop_name", String.class),
                            row.get("to_stop_id", String.class),
                            row.get("to_lat", BigDecimal.class),
                            row.get("to_lon", BigDecimal.class)
                    );

                    return new DirectRouteResult(
                            route,
                            fromStop,
                            toStop,
                            row.get("estimated_travel_minutes", Integer.class),
                            0.0, // Walking distance will be calculated separately
                            0.0  // Walking distance will be calculated separately
                    );
                })
                .all()
                .doOnComplete(() -> log.debug("Direct routes search completed"));
    }

    @Override
    public Flux<TransferRouteResult> findRoutesWithOneTransfer(List<BusStop> fromStops, List<BusStop> toStops,
                                                               double maxTransferDistanceKm) {
        log.debug("Finding routes with one transfer (max transfer distance: {}km)", maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        String sql = """
            WITH potential_transfers AS (
                SELECT DISTINCT
                    rs1.route_id as first_route_id,
                    rs1.stop_id as from_stop_id,
                    rs2.stop_id as transfer_stop_id,
                    rs3.route_id as second_route_id,
                    rs4.stop_id as to_stop_id,
                    bs_transfer.stop_name as transfer_stop_name,
                    bs_transfer.latitude as transfer_lat,
                    bs_transfer.longitude as transfer_lon,
                    bs_transfer.is_major_stop as transfer_is_major,
                    ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as first_route_minutes,
                    ABS(rs4.stop_sequence - rs3.stop_sequence) * 2 as second_route_minutes
                FROM route_stops rs1
                JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
                JOIN route_stops rs3 ON rs2.stop_id = rs3.stop_id AND rs3.direction = rs2.direction
                JOIN route_stops rs4 ON rs3.route_id = rs4.route_id AND rs3.direction = rs4.direction
                JOIN bus_stops bs_transfer ON rs2.stop_id = bs_transfer.id
                WHERE rs1.stop_id = ANY(:fromStopIds)
                AND rs4.stop_id = ANY(:toStopIds)
                AND rs1.route_id != rs3.route_id
                AND rs1.stop_sequence < rs2.stop_sequence
                AND rs3.stop_sequence < rs4.stop_sequence
            )
            SELECT 
                pt.*,
                br1.route_number as first_route_number,
                br1.route_name as first_route_name,
                br1.route_color as first_route_color,
                br2.route_number as second_route_number,
                br2.route_name as second_route_name,
                br2.route_color as second_route_color,
                bs_from.stop_name as from_stop_name,
                bs_from.latitude as from_lat,
                bs_from.longitude as from_lon,
                bs_to.stop_name as to_stop_name,
                bs_to.latitude as to_lat,
                bs_to.longitude as to_lon
            FROM potential_transfers pt
            JOIN bus_routes br1 ON pt.first_route_id = br1.id
            JOIN bus_routes br2 ON pt.second_route_id = br2.id
            JOIN bus_stops bs_from ON pt.from_stop_id = bs_from.id
            JOIN bus_stops bs_to ON pt.to_stop_id = bs_to.id
            WHERE br1.is_active = true AND br2.is_active = true
            ORDER BY (pt.first_route_minutes + pt.second_route_minutes)
            LIMIT 8
            """;

        String[] fromStopIds = fromStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);
        String[] toStopIds = toStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);

        return databaseClient.sql(sql)
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .map(row -> {
                    // Create first route
                    BusRoute firstRoute = new BusRoute(
                            row.get("first_route_number", String.class),
                            row.get("first_route_name", String.class),
                            null,
                            row.get("first_route_color", String.class) != null ?
                                    row.get("first_route_color", String.class) : "#1976D2"
                    );

                    // Create second route
                    BusRoute secondRoute = new BusRoute(
                            row.get("second_route_number", String.class),
                            row.get("second_route_name", String.class),
                            null,
                            row.get("second_route_color", String.class) != null ?
                                    row.get("second_route_color", String.class) : "#1976D2"
                    );

                    // Create stops
                    BusStop fromStop = new BusStop(
                            row.get("from_stop_name", String.class),
                            row.get("from_stop_id", String.class),
                            row.get("from_lat", BigDecimal.class),
                            row.get("from_lon", BigDecimal.class)
                    );

                    BusStop transferStop = new BusStop(
                            row.get("transfer_stop_name", String.class),
                            row.get("transfer_stop_id", String.class),
                            row.get("transfer_lat", BigDecimal.class),
                            row.get("transfer_lon", BigDecimal.class)
                    );
                    // Set major stop flag for transfer calculation
                    transferStop = new BusStop(
                            transferStop.getId(),
                            transferStop.getStopName(),
                            transferStop.getStopCode(),
                            transferStop.getLatitude(),
                            transferStop.getLongitude(),
                            true, // is_active
                            row.get("transfer_is_major", Boolean.class), // is_major_stop
                            false // has_shelter
                    );

                    BusStop toStop = new BusStop(
                            row.get("to_stop_name", String.class),
                            row.get("to_stop_id", String.class),
                            row.get("to_lat", BigDecimal.class),
                            row.get("to_lon", BigDecimal.class)
                    );

                    return new TransferRouteResult(
                            firstRoute, fromStop, transferStop,
                            secondRoute, toStop,
                            row.get("first_route_minutes", Integer.class),
                            5, // Transfer wait time - will be calculated by ETA service
                            row.get("second_route_minutes", Integer.class),
                            0.0, 0.0 // Walking distances
                    );
                })
                .all()
                .doOnComplete(() -> log.debug("One transfer routes search completed"));
    }

    @Override
    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(List<BusStop> fromStops, List<BusStop> toStops,
                                                                   double maxTransferDistanceKm) {
        log.debug("Finding routes with two transfers (complex search)");

        // For now, return empty - two transfers is complex and rarely needed
        // In production, this would implement a more sophisticated graph algorithm
        return Flux.empty();
    }

    @Override
    public Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2) {
        String sql = """
            SELECT COUNT(*) > 0 as connected
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            WHERE rs1.stop_id = :stop1Id AND rs2.stop_id = :stop2Id
            """;

        return databaseClient.sql(sql)
                .bind("stop1Id", stop1.getId().getValue())
                .bind("stop2Id", stop2.getId().getValue())
                .map(row -> row.get("connected", Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Flux<BusRoute> getConnectingRoutes(BusStop fromStop, BusStop toStop) {
        String sql = """
            SELECT DISTINCT br.*
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            JOIN bus_routes br ON rs1.route_id = br.id
            WHERE rs1.stop_id = :fromStopId 
            AND rs2.stop_id = :toStopId
            AND br.is_active = true
            """;

        return databaseClient.sql(sql)
                .bind("fromStopId", fromStop.getId().getValue())
                .bind("toStopId", toStop.getId().getValue())
                .map(row -> new BusRoute(
                        row.get("route_number", String.class),
                        row.get("route_name", String.class),
                        row.get("route_name_tm", String.class),
                        row.get("route_color", String.class)
                ))
                .all();
    }
}