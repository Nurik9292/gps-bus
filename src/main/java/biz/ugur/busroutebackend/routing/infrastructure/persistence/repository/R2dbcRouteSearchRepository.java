package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.repository.RouteSearchRepository;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.DirectRouteResult;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TransferRouteResult;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TwoTransferRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;


@Repository
@Slf4j
@RequiredArgsConstructor
public class R2dbcRouteSearchRepository implements RouteSearchRepository {

    private final DatabaseClient databaseClient;

    private static final int DIRECT_ROUTES_LIMIT = 10;
    private static final int ONE_TRANSFER_LIMIT = 12;
    private static final int TWO_TRANSFER_LIMIT = 6;


    @Override
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.info("Empty stop lists provided for direct route search");
            return Flux.empty();
        }

        String[] fromStopIds = extractStopIds(fromStops);
        String[] toStopIds = extractStopIds(toStops);

        if (fromStopIds.length == 0 || toStopIds.length == 0) {
            log.warn("No valid stop IDs after filtering");
            return Flux.empty();
        }

        log.info("Searching direct routes: {} from stops, {} to stops", fromStopIds.length, toStopIds.length);
        long startTime = System.currentTimeMillis();

        return databaseClient.sql(buildDirectRoutesQuery())
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .bind("limit", DIRECT_ROUTES_LIMIT)
                .map(this::mapToDirectRouteResult)
                .all()
                .filter(Objects::nonNull)
                .doOnComplete(() -> log.info("Direct route SQL completed in {}ms",
                        System.currentTimeMillis() - startTime));
    }

    private String buildDirectRoutesQuery() {
        return """
            WITH candidate_routes AS (
                SELECT DISTINCT
                    br.id as route_id,
                    br.route_number,
                    br.route_name,
                    br.route_color,
                    CASE
                        WHEN rs1.direction = 0 THEN br.total_distance_forward_meters
                        WHEN rs1.direction = 1 THEN br.total_distance_backward_meters
                    END as total_distance_meters,
                    rs1.stop_id as from_stop_id,
                    bs1.stop_name as from_stop_name,
                    bs1.latitude as from_lat,
                    bs1.longitude as from_lon,
                    rs2.stop_id as to_stop_id,
                    bs2.stop_name as to_stop_name,
                    bs2.latitude as to_lat,
                    bs2.longitude as to_lon,
                    rs1.stop_sequence as from_sequence,
                    rs2.stop_sequence as to_sequence,
                    rs1.direction,
                    ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as estimated_travel_minutes,
                    ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as distance_meters
                FROM route_stops rs1
                JOIN route_stops rs2 ON rs1.route_id = rs2.route_id
                                    AND rs1.direction = rs2.direction
                                    AND rs1.stop_sequence < rs2.stop_sequence
                JOIN bus_routes br ON rs1.route_id = br.id
                JOIN bus_stops bs1 ON rs1.stop_id = bs1.id
                JOIN bus_stops bs2 ON rs2.stop_id = bs2.id
                WHERE rs1.stop_id = ANY(:fromStopIds)
                  AND rs2.stop_id = ANY(:toStopIds)
                  AND br.is_active = true
                  AND bs1.is_active = true
                  AND bs2.is_active = true
                GROUP BY br.id, br.route_number, br.route_name, br.route_color,
                         br.total_distance_forward_meters, br.total_distance_backward_meters,
                         rs1.stop_id, bs1.stop_name, bs1.latitude, bs1.longitude,
                         rs2.stop_id, bs2.stop_name, bs2.latitude, bs2.longitude,
                         rs1.stop_sequence, rs2.stop_sequence, rs1.direction,
                         rs1.distance_from_start_meters, rs2.distance_from_start_meters
            )
            SELECT *
            FROM candidate_routes
            WHERE estimated_travel_minutes >= 2
              AND estimated_travel_minutes <= 120
              AND distance_meters >= 100
            ORDER BY estimated_travel_minutes, distance_meters
            LIMIT :limit
            """;
    }

    private DirectRouteResult mapToDirectRouteResult(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        Integer direction = row.get("direction", Integer.class);
        Integer totalDistance = row.get("total_distance_meters", Integer.class);

        BusRoute route = BusRoute.builder()
                .id(BusRouteId.of(row.get("route_id", String.class)))
                .routeNumber(row.get("route_number", String.class))
                .routeName(row.get("route_name", String.class))
                .routeColor(row.get("route_color", String.class) != null ?
                        row.get("route_color", String.class) : "#1976D2")
                .isActive(true)
                .totalDistanceForwardMeters(direction == 0 ? totalDistance : null)
                .totalDistanceBackwardMeters(direction == 1 ? totalDistance : null)
                .build();

        BusStop fromStop = BusStop.builder()
                .id(new BusStopId(row.get("from_stop_id", String.class)))
                .stopName(row.get("from_stop_name", String.class))
                .latitude(row.get("from_lat", BigDecimal.class))
                .longitude(row.get("from_lon", BigDecimal.class))
                .build();

        BusStop toStop = BusStop.builder()
                .id(new BusStopId(row.get("to_stop_id", String.class)))
                .stopName(row.get("to_stop_name", String.class))
                .latitude(row.get("to_lat", BigDecimal.class))
                .longitude(row.get("to_lon", BigDecimal.class))
                .build();

        Integer estimatedMinutes = row.get("estimated_travel_minutes", Integer.class);

        return new DirectRouteResult(route, fromStop, toStop,
                estimatedMinutes != null ? estimatedMinutes : 10, 0.0, 0.0);
    }


    @Override
    public Flux<TransferRouteResult> findOneTransferRoutes(
            List<BusStop> fromStops,
            List<BusStop> toStops,
            double maxTransferDistanceKm) {

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        String[] fromStopIds = extractStopIds(fromStops);
        String[] toStopIds = extractStopIds(toStops);

        if (fromStopIds.length == 0 || toStopIds.length == 0) {
            return Flux.empty();
        }

        // P1 OPTIMIZATION NOTE: maxTransferDistanceKm is used for pre-filtering stops by caller
        // The fromStops and toStops lists are already filtered to be within maxTransferDistanceKm
        // This ensures users don't have to walk too far to/from bus stops
        log.debug("Searching one-transfer routes: {} from, {} to, max walking distance {}km",
                fromStopIds.length, toStopIds.length, maxTransferDistanceKm);

        return databaseClient.sql(buildOneTransferRoutesQuery())
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .bind("limit", ONE_TRANSFER_LIMIT)
                .map(this::mapToTransferRouteResult)
                .all()
                .filter(Objects::nonNull)
                .doOnComplete(() -> log.debug("One-transfer routes search completed"));
    }

    private String buildOneTransferRoutesQuery() {
        // P2 OPTIMIZATION: Extract duplicate vehicle count subquery to CTE
        // Old: Same subquery executed 8 times (4 UNION sections × 2 routes each)
        // New: Calculate once in CTE, reference in all JOINs
        return """
            WITH route_vehicle_counts AS (
                SELECT assigned_route_id, COUNT(*) as vehicle_count
                FROM vehicles
                WHERE is_active = true
                GROUP BY assigned_route_id
            )
            SELECT * FROM (
                -- Forward-Forward
                SELECT
                    br1.id as first_route_id, br1.route_number as first_route_number,
                    br1.route_name as first_route_name, br1.route_color as first_route_color,
                    bs_from.id as from_stop_id, bs_from.stop_name as from_stop_name,
                    bs_from.latitude as from_lat, bs_from.longitude as from_lon,
                    bs_transfer.id as transfer_stop_id, bs_transfer.stop_name as transfer_stop_name,
                    bs_transfer.latitude as transfer_lat, bs_transfer.longitude as transfer_lon,
                    bs_transfer.is_major_stop,
                    br2.id as second_route_id, br2.route_number as second_route_number,
                    br2.route_name as second_route_name, br2.route_color as second_route_color,
                    bs_to.id as to_stop_id, bs_to.stop_name as to_stop_name,
                    bs_to.latitude as to_lat, bs_to.longitude as to_lon,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2 as first_segment_minutes,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2 as second_segment_minutes,
                    v1.vehicle_count as first_route_vehicles,
                    v2.vehicle_count as second_route_vehicles,
                    'FF' as direction_combo
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 0 AND rs1_end.direction = 0
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 0
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 0 AND rs2_end.direction = 0
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer ON rs1_end.stop_id = bs_transfer.id
                JOIN bus_stops bs_to ON rs2_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs2_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id
                  AND br1.is_active = true AND br2.is_active = true

                UNION ALL

                -- Forward-Backward
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer.id, bs_transfer.stop_name, bs_transfer.latitude, bs_transfer.longitude,
                    bs_transfer.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, 'FB'
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 0 AND rs1_end.direction = 0
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 1
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 1 AND rs2_end.direction = 1
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer ON rs1_end.stop_id = bs_transfer.id
                JOIN bus_stops bs_to ON rs2_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs2_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id
                  AND br1.is_active = true AND br2.is_active = true

                UNION ALL

                -- Backward-Forward
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer.id, bs_transfer.stop_name, bs_transfer.latitude, bs_transfer.longitude,
                    bs_transfer.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, 'BF'
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 1 AND rs1_end.direction = 1
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 0
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 0 AND rs2_end.direction = 0
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer ON rs1_end.stop_id = bs_transfer.id
                JOIN bus_stops bs_to ON rs2_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs2_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id
                  AND br1.is_active = true AND br2.is_active = true

                UNION ALL

                -- Backward-Backward
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer.id, bs_transfer.stop_name, bs_transfer.latitude, bs_transfer.longitude,
                    bs_transfer.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, 'BB'
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 1 AND rs1_end.direction = 1
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 1
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 1 AND rs2_end.direction = 1
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer ON rs1_end.stop_id = bs_transfer.id
                JOIN bus_stops bs_to ON rs2_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs2_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id
                  AND br1.is_active = true AND br2.is_active = true
            ) combined_routes
            WHERE first_segment_minutes >= 2
              AND second_segment_minutes >= 2
              AND (first_segment_minutes + second_segment_minutes) <= 120
            ORDER BY
                -- P1 OPTIMIZATION: Prefer major stops for transfers (better infrastructure, more reliable)
                is_major_stop DESC NULLS LAST,
                (first_segment_minutes + second_segment_minutes),
                direction_combo
            LIMIT :limit
            """;
    }

    private TransferRouteResult mapToTransferRouteResult(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        BusRoute firstRoute = BusRoute.builder()
                .id(BusRouteId.of(row.get("first_route_id", String.class)))
                .routeNumber(row.get("first_route_number", String.class))
                .routeName(row.get("first_route_name", String.class))
                .routeColor(row.get("first_route_color", String.class))
                .build();

        BusRoute secondRoute = BusRoute.builder()
                .id(BusRouteId.of(row.get("second_route_id", String.class)))
                .routeNumber(row.get("second_route_number", String.class))
                .routeName(row.get("second_route_name", String.class))
                .routeColor(row.get("second_route_color", String.class))
                .build();

        BusStop fromStop = BusStop.builder()
                .id(new BusStopId(row.get("from_stop_id", String.class)))
                .stopName(row.get("from_stop_name", String.class))
                .latitude(row.get("from_lat", BigDecimal.class))
                .longitude(row.get("from_lon", BigDecimal.class))
                .build();

        BusStop transferStop = BusStop.builder()
                .id(new BusStopId(row.get("transfer_stop_id", String.class)))
                .stopName(row.get("transfer_stop_name", String.class))
                .latitude(row.get("transfer_lat", BigDecimal.class))
                .longitude(row.get("transfer_lon", BigDecimal.class))
                .isMajorStop(row.get("is_major_stop", Boolean.class))
                .build();

        BusStop toStop = BusStop.builder()
                .id(new BusStopId(row.get("to_stop_id", String.class)))
                .stopName(row.get("to_stop_name", String.class))
                .latitude(row.get("to_lat", BigDecimal.class))
                .longitude(row.get("to_lon", BigDecimal.class))
                .build();

        Integer firstSegmentMinutes = row.get("first_segment_minutes", Integer.class);
        Integer secondSegmentMinutes = row.get("second_segment_minutes", Integer.class);
        Long firstRouteVehicles = row.get("first_route_vehicles", Long.class);
        Long secondRouteVehicles = row.get("second_route_vehicles", Long.class);
        Boolean isMajorStop = row.get("is_major_stop", Boolean.class);

        int adjustedFirstMinutes = adjustTravelTimeByVehicleCount(firstSegmentMinutes, firstRouteVehicles);
        int adjustedSecondMinutes = adjustTravelTimeByVehicleCount(secondSegmentMinutes, secondRouteVehicles);
        int transferWaitMinutes = calculateTransferWaitTime(isMajorStop, firstRouteVehicles, secondRouteVehicles);

        return new TransferRouteResult(
                firstRoute, fromStop, transferStop,
                secondRoute, toStop,
                adjustedFirstMinutes, transferWaitMinutes, adjustedSecondMinutes,
                0.0, 0.0
        );
    }


    @Override
    public Flux<TwoTransferRouteResult> findTwoTransferRoutes(
            List<BusStop> fromStops,
            List<BusStop> toStops,
            double maxTransferDistanceKm) {

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        String[] fromStopIds = extractStopIds(fromStops);
        String[] toStopIds = extractStopIds(toStops);

        if (fromStopIds.length == 0 || toStopIds.length == 0) {
            return Flux.empty();
        }

        log.debug("Searching two-transfer routes: {} from, {} to, max distance {}km",
                fromStopIds.length, toStopIds.length, maxTransferDistanceKm);

        return databaseClient.sql(buildTwoTransferRoutesQuery())
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .bind("limit", TWO_TRANSFER_LIMIT)
                .map(this::mapToTwoTransferRouteResult)
                .all()
                .filter(Objects::nonNull)
                .doOnComplete(() -> log.debug("Two-transfer routes search completed"));
    }

    private String buildTwoTransferRoutesQuery() {
        return """
            WITH route_vehicle_counts AS (
                SELECT assigned_route_id, COUNT(*) as vehicle_count
                FROM vehicles
                WHERE is_active = true
                GROUP BY assigned_route_id
            )
            SELECT * FROM (
                -- FFF: Forward-Forward-Forward
                SELECT
                -- First Route
                br1.id as first_route_id, br1.route_number as first_route_number,
                br1.route_name as first_route_name, br1.route_color as first_route_color,

                -- Stops
                bs_from.id as from_stop_id, bs_from.stop_name as from_stop_name,
                bs_from.latitude as from_lat, bs_from.longitude as from_lon,

                bs_transfer1.id as first_transfer_stop_id, bs_transfer1.stop_name as first_transfer_stop_name,
                bs_transfer1.latitude as first_transfer_lat, bs_transfer1.longitude as first_transfer_lon,
                bs_transfer1.is_major_stop as first_transfer_is_major,

                -- Second Route
                br2.id as second_route_id, br2.route_number as second_route_number,
                br2.route_name as second_route_name, br2.route_color as second_route_color,

                bs_transfer2.id as second_transfer_stop_id, bs_transfer2.stop_name as second_transfer_stop_name,
                bs_transfer2.latitude as second_transfer_lat, bs_transfer2.longitude as second_transfer_lon,
                bs_transfer2.is_major_stop as second_transfer_is_major,

                -- Third Route
                br3.id as third_route_id, br3.route_number as third_route_number,
                br3.route_name as third_route_name, br3.route_color as third_route_color,

                bs_to.id as to_stop_id, bs_to.stop_name as to_stop_name,
                bs_to.latitude as to_lat, bs_to.longitude as to_lon,

                -- Travel times
                ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2 as first_segment_minutes,
                ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2 as second_segment_minutes,
                ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) * 2 as third_segment_minutes,

                -- Vehicle counts for wait time calculation
                v1.vehicle_count as first_route_vehicles,
                v2.vehicle_count as second_route_vehicles,
                v3.vehicle_count as third_route_vehicles

            FROM route_stops rs1_start

            -- First segment
            JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                AND rs1_start.direction = 0 AND rs1_end.direction = 0
                AND rs1_start.stop_sequence < rs1_end.stop_sequence
            JOIN bus_routes br1 ON rs1_start.route_id = br1.id
            JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
            JOIN bus_stops bs_transfer1 ON rs1_end.stop_id = bs_transfer1.id

            -- Second segment
            JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id
                AND rs2_start.direction = 0
            JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                AND rs2_start.direction = 0 AND rs2_end.direction = 0
                AND rs2_start.stop_sequence < rs2_end.stop_sequence
            JOIN bus_routes br2 ON rs2_start.route_id = br2.id
            JOIN bus_stops bs_transfer2 ON rs2_end.stop_id = bs_transfer2.id

            -- Third segment
            JOIN route_stops rs3_start ON rs2_end.stop_id = rs3_start.stop_id
                AND rs3_start.direction = 0
            JOIN route_stops rs3_end ON rs3_start.route_id = rs3_end.route_id
                AND rs3_start.direction = 0 AND rs3_end.direction = 0
                AND rs3_start.stop_sequence < rs3_end.stop_sequence
            JOIN bus_routes br3 ON rs3_start.route_id = br3.id
            JOIN bus_stops bs_to ON rs3_end.stop_id = bs_to.id

            -- Vehicle counts
            LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
            LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
            LEFT JOIN route_vehicle_counts v3 ON br3.id = v3.assigned_route_id

            WHERE rs1_start.stop_id = ANY(:fromStopIds)
              AND rs3_end.stop_id = ANY(:toStopIds)
              AND br1.id != br2.id AND br2.id != br3.id AND br1.id != br3.id
              AND br1.is_active = true AND br2.is_active = true AND br3.is_active = true
              AND ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) <= 12
              AND ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) <= 12
              AND ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) <= 12

                UNION ALL

                -- FFB: Forward-Forward-Backward
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer1.id, bs_transfer1.stop_name, bs_transfer1.latitude, bs_transfer1.longitude,
                    bs_transfer1.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_transfer2.id, bs_transfer2.stop_name, bs_transfer2.latitude, bs_transfer2.longitude,
                    bs_transfer2.is_major_stop,
                    br3.id, br3.route_number, br3.route_name, br3.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, v3.vehicle_count
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 0 AND rs1_end.direction = 0
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer1 ON rs1_end.stop_id = bs_transfer1.id
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 0
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 0 AND rs2_end.direction = 0
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_transfer2 ON rs2_end.stop_id = bs_transfer2.id
                JOIN route_stops rs3_start ON rs2_end.stop_id = rs3_start.stop_id AND rs3_start.direction = 1
                JOIN route_stops rs3_end ON rs3_start.route_id = rs3_end.route_id
                    AND rs3_start.direction = 1 AND rs3_end.direction = 1
                    AND rs3_start.stop_sequence < rs3_end.stop_sequence
                JOIN bus_routes br3 ON rs3_start.route_id = br3.id
                JOIN bus_stops bs_to ON rs3_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                LEFT JOIN route_vehicle_counts v3 ON br3.id = v3.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs3_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id AND br2.id != br3.id AND br1.id != br3.id
                  AND br1.is_active = true AND br2.is_active = true AND br3.is_active = true
                  AND ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) <= 12
                  AND ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) <= 12
                  AND ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) <= 12

                UNION ALL

                -- FBF: Forward-Backward-Forward (Phase 2.2)
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer1.id, bs_transfer1.stop_name, bs_transfer1.latitude, bs_transfer1.longitude,
                    bs_transfer1.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_transfer2.id, bs_transfer2.stop_name, bs_transfer2.latitude, bs_transfer2.longitude,
                    bs_transfer2.is_major_stop,
                    br3.id, br3.route_number, br3.route_name, br3.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, v3.vehicle_count
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 0 AND rs1_end.direction = 0
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer1 ON rs1_end.stop_id = bs_transfer1.id
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 1
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 1 AND rs2_end.direction = 1
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_transfer2 ON rs2_end.stop_id = bs_transfer2.id
                JOIN route_stops rs3_start ON rs2_end.stop_id = rs3_start.stop_id AND rs3_start.direction = 0
                JOIN route_stops rs3_end ON rs3_start.route_id = rs3_end.route_id
                    AND rs3_start.direction = 0 AND rs3_end.direction = 0
                    AND rs3_start.stop_sequence < rs3_end.stop_sequence
                JOIN bus_routes br3 ON rs3_start.route_id = br3.id
                JOIN bus_stops bs_to ON rs3_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                LEFT JOIN route_vehicle_counts v3 ON br3.id = v3.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs3_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id AND br2.id != br3.id AND br1.id != br3.id
                  AND br1.is_active = true AND br2.is_active = true AND br3.is_active = true
                  AND ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) <= 12
                  AND ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) <= 12
                  AND ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) <= 12

                UNION ALL

                -- FBB: Forward-Backward-Backward (Phase 2.2)
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer1.id, bs_transfer1.stop_name, bs_transfer1.latitude, bs_transfer1.longitude,
                    bs_transfer1.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_transfer2.id, bs_transfer2.stop_name, bs_transfer2.latitude, bs_transfer2.longitude,
                    bs_transfer2.is_major_stop,
                    br3.id, br3.route_number, br3.route_name, br3.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, v3.vehicle_count
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 0 AND rs1_end.direction = 0
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer1 ON rs1_end.stop_id = bs_transfer1.id
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 1
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 1 AND rs2_end.direction = 1
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_transfer2 ON rs2_end.stop_id = bs_transfer2.id
                JOIN route_stops rs3_start ON rs2_end.stop_id = rs3_start.stop_id AND rs3_start.direction = 1
                JOIN route_stops rs3_end ON rs3_start.route_id = rs3_end.route_id
                    AND rs3_start.direction = 1 AND rs3_end.direction = 1
                    AND rs3_start.stop_sequence < rs3_end.stop_sequence
                JOIN bus_routes br3 ON rs3_start.route_id = br3.id
                JOIN bus_stops bs_to ON rs3_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                LEFT JOIN route_vehicle_counts v3 ON br3.id = v3.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs3_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id AND br2.id != br3.id AND br1.id != br3.id
                  AND br1.is_active = true AND br2.is_active = true AND br3.is_active = true
                  AND ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) <= 12
                  AND ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) <= 12
                  AND ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) <= 12

                UNION ALL

                -- BFF: Backward-Forward-Forward (Phase 2.2)
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer1.id, bs_transfer1.stop_name, bs_transfer1.latitude, bs_transfer1.longitude,
                    bs_transfer1.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_transfer2.id, bs_transfer2.stop_name, bs_transfer2.latitude, bs_transfer2.longitude,
                    bs_transfer2.is_major_stop,
                    br3.id, br3.route_number, br3.route_name, br3.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, v3.vehicle_count
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 1 AND rs1_end.direction = 1
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer1 ON rs1_end.stop_id = bs_transfer1.id
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 0
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 0 AND rs2_end.direction = 0
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_transfer2 ON rs2_end.stop_id = bs_transfer2.id
                JOIN route_stops rs3_start ON rs2_end.stop_id = rs3_start.stop_id AND rs3_start.direction = 0
                JOIN route_stops rs3_end ON rs3_start.route_id = rs3_end.route_id
                    AND rs3_start.direction = 0 AND rs3_end.direction = 0
                    AND rs3_start.stop_sequence < rs3_end.stop_sequence
                JOIN bus_routes br3 ON rs3_start.route_id = br3.id
                JOIN bus_stops bs_to ON rs3_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                LEFT JOIN route_vehicle_counts v3 ON br3.id = v3.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs3_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id AND br2.id != br3.id AND br1.id != br3.id
                  AND br1.is_active = true AND br2.is_active = true AND br3.is_active = true
                  AND ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) <= 12
                  AND ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) <= 12
                  AND ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) <= 12

                UNION ALL

                -- BFB: Backward-Forward-Backward (Phase 2.2)
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer1.id, bs_transfer1.stop_name, bs_transfer1.latitude, bs_transfer1.longitude,
                    bs_transfer1.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_transfer2.id, bs_transfer2.stop_name, bs_transfer2.latitude, bs_transfer2.longitude,
                    bs_transfer2.is_major_stop,
                    br3.id, br3.route_number, br3.route_name, br3.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, v3.vehicle_count
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 1 AND rs1_end.direction = 1
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer1 ON rs1_end.stop_id = bs_transfer1.id
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 0
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 0 AND rs2_end.direction = 0
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_transfer2 ON rs2_end.stop_id = bs_transfer2.id
                JOIN route_stops rs3_start ON rs2_end.stop_id = rs3_start.stop_id AND rs3_start.direction = 1
                JOIN route_stops rs3_end ON rs3_start.route_id = rs3_end.route_id
                    AND rs3_start.direction = 1 AND rs3_end.direction = 1
                    AND rs3_start.stop_sequence < rs3_end.stop_sequence
                JOIN bus_routes br3 ON rs3_start.route_id = br3.id
                JOIN bus_stops bs_to ON rs3_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                LEFT JOIN route_vehicle_counts v3 ON br3.id = v3.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs3_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id AND br2.id != br3.id AND br1.id != br3.id
                  AND br1.is_active = true AND br2.is_active = true AND br3.is_active = true
                  AND ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) <= 12
                  AND ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) <= 12
                  AND ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) <= 12

                UNION ALL

                -- BBF: Backward-Backward-Forward (Phase 2.2)
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer1.id, bs_transfer1.stop_name, bs_transfer1.latitude, bs_transfer1.longitude,
                    bs_transfer1.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_transfer2.id, bs_transfer2.stop_name, bs_transfer2.latitude, bs_transfer2.longitude,
                    bs_transfer2.is_major_stop,
                    br3.id, br3.route_number, br3.route_name, br3.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, v3.vehicle_count
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 1 AND rs1_end.direction = 1
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer1 ON rs1_end.stop_id = bs_transfer1.id
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 1
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 1 AND rs2_end.direction = 1
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_transfer2 ON rs2_end.stop_id = bs_transfer2.id
                JOIN route_stops rs3_start ON rs2_end.stop_id = rs3_start.stop_id AND rs3_start.direction = 0
                JOIN route_stops rs3_end ON rs3_start.route_id = rs3_end.route_id
                    AND rs3_start.direction = 0 AND rs3_end.direction = 0
                    AND rs3_start.stop_sequence < rs3_end.stop_sequence
                JOIN bus_routes br3 ON rs3_start.route_id = br3.id
                JOIN bus_stops bs_to ON rs3_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                LEFT JOIN route_vehicle_counts v3 ON br3.id = v3.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs3_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id AND br2.id != br3.id AND br1.id != br3.id
                  AND br1.is_active = true AND br2.is_active = true AND br3.is_active = true
                  AND ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) <= 12
                  AND ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) <= 12
                  AND ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) <= 12

                UNION ALL

                -- BBB: Backward-Backward-Backward (Phase 2.2)
                SELECT
                    br1.id, br1.route_number, br1.route_name, br1.route_color,
                    bs_from.id, bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                    bs_transfer1.id, bs_transfer1.stop_name, bs_transfer1.latitude, bs_transfer1.longitude,
                    bs_transfer1.is_major_stop,
                    br2.id, br2.route_number, br2.route_name, br2.route_color,
                    bs_transfer2.id, bs_transfer2.stop_name, bs_transfer2.latitude, bs_transfer2.longitude,
                    bs_transfer2.is_major_stop,
                    br3.id, br3.route_number, br3.route_name, br3.route_color,
                    bs_to.id, bs_to.stop_name, bs_to.latitude, bs_to.longitude,
                    ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) * 2,
                    ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) * 2,
                    ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) * 2,
                    v1.vehicle_count, v2.vehicle_count, v3.vehicle_count
                FROM route_stops rs1_start
                JOIN route_stops rs1_end ON rs1_start.route_id = rs1_end.route_id
                    AND rs1_start.direction = 1 AND rs1_end.direction = 1
                    AND rs1_start.stop_sequence < rs1_end.stop_sequence
                JOIN bus_routes br1 ON rs1_start.route_id = br1.id
                JOIN bus_stops bs_from ON rs1_start.stop_id = bs_from.id
                JOIN bus_stops bs_transfer1 ON rs1_end.stop_id = bs_transfer1.id
                JOIN route_stops rs2_start ON rs1_end.stop_id = rs2_start.stop_id AND rs2_start.direction = 1
                JOIN route_stops rs2_end ON rs2_start.route_id = rs2_end.route_id
                    AND rs2_start.direction = 1 AND rs2_end.direction = 1
                    AND rs2_start.stop_sequence < rs2_end.stop_sequence
                JOIN bus_routes br2 ON rs2_start.route_id = br2.id
                JOIN bus_stops bs_transfer2 ON rs2_end.stop_id = bs_transfer2.id
                JOIN route_stops rs3_start ON rs2_end.stop_id = rs3_start.stop_id AND rs3_start.direction = 1
                JOIN route_stops rs3_end ON rs3_start.route_id = rs3_end.route_id
                    AND rs3_start.direction = 1 AND rs3_end.direction = 1
                    AND rs3_start.stop_sequence < rs3_end.stop_sequence
                JOIN bus_routes br3 ON rs3_start.route_id = br3.id
                JOIN bus_stops bs_to ON rs3_end.stop_id = bs_to.id
                LEFT JOIN route_vehicle_counts v1 ON br1.id = v1.assigned_route_id
                LEFT JOIN route_vehicle_counts v2 ON br2.id = v2.assigned_route_id
                LEFT JOIN route_vehicle_counts v3 ON br3.id = v3.assigned_route_id
                WHERE rs1_start.stop_id = ANY(:fromStopIds)
                  AND rs3_end.stop_id = ANY(:toStopIds)
                  AND br1.id != br2.id AND br2.id != br3.id AND br1.id != br3.id
                  AND br1.is_active = true AND br2.is_active = true AND br3.is_active = true
                  AND ABS(rs1_end.stop_sequence - rs1_start.stop_sequence) <= 12
                  AND ABS(rs2_end.stop_sequence - rs2_start.stop_sequence) <= 12
                  AND ABS(rs3_end.stop_sequence - rs3_start.stop_sequence) <= 12

            ) combined_routes
            ORDER BY (first_segment_minutes + second_segment_minutes + third_segment_minutes)
            LIMIT :limit
            """;
    }

    private TwoTransferRouteResult mapToTwoTransferRouteResult(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        // CRITICAL FIX: Implemented proper mapping for two-transfer routes

        // Build three routes
        BusRoute firstRoute = BusRoute.builder()
                .id(BusRouteId.of(row.get("first_route_id", String.class)))
                .routeNumber(row.get("first_route_number", String.class))
                .routeName(row.get("first_route_name", String.class))
                .routeColor(row.get("first_route_color", String.class))
                .build();

        BusRoute secondRoute = BusRoute.builder()
                .id(BusRouteId.of(row.get("second_route_id", String.class)))
                .routeNumber(row.get("second_route_number", String.class))
                .routeName(row.get("second_route_name", String.class))
                .routeColor(row.get("second_route_color", String.class))
                .build();

        BusRoute thirdRoute = BusRoute.builder()
                .id(BusRouteId.of(row.get("third_route_id", String.class)))
                .routeNumber(row.get("third_route_number", String.class))
                .routeName(row.get("third_route_name", String.class))
                .routeColor(row.get("third_route_color", String.class))
                .build();

        // Build four stops
        BusStop fromStop = BusStop.builder()
                .id(new BusStopId(row.get("from_stop_id", String.class)))
                .stopName(row.get("from_stop_name", String.class))
                .latitude(row.get("from_lat", BigDecimal.class))
                .longitude(row.get("from_lon", BigDecimal.class))
                .build();

        BusStop firstTransferStop = BusStop.builder()
                .id(new BusStopId(row.get("first_transfer_stop_id", String.class)))
                .stopName(row.get("first_transfer_stop_name", String.class))
                .latitude(row.get("first_transfer_lat", BigDecimal.class))
                .longitude(row.get("first_transfer_lon", BigDecimal.class))
                .isMajorStop(row.get("first_transfer_is_major", Boolean.class))
                .build();

        BusStop secondTransferStop = BusStop.builder()
                .id(new BusStopId(row.get("second_transfer_stop_id", String.class)))
                .stopName(row.get("second_transfer_stop_name", String.class))
                .latitude(row.get("second_transfer_lat", BigDecimal.class))
                .longitude(row.get("second_transfer_lon", BigDecimal.class))
                .isMajorStop(row.get("second_transfer_is_major", Boolean.class))
                .build();

        BusStop toStop = BusStop.builder()
                .id(new BusStopId(row.get("to_stop_id", String.class)))
                .stopName(row.get("to_stop_name", String.class))
                .latitude(row.get("to_lat", BigDecimal.class))
                .longitude(row.get("to_lon", BigDecimal.class))
                .build();

        Integer firstSegmentMinutes = row.get("first_segment_minutes", Integer.class);
        Integer secondSegmentMinutes = row.get("second_segment_minutes", Integer.class);
        Integer thirdSegmentMinutes = row.get("third_segment_minutes", Integer.class);

        Long firstRouteVehicles = row.get("first_route_vehicles", Long.class);
        Long secondRouteVehicles = row.get("second_route_vehicles", Long.class);
        Long thirdRouteVehicles = row.get("third_route_vehicles", Long.class);

        int adjustedFirstMinutes = adjustTravelTimeByVehicleCount(firstSegmentMinutes, firstRouteVehicles);
        int adjustedSecondMinutes = adjustTravelTimeByVehicleCount(secondSegmentMinutes, secondRouteVehicles);
        int adjustedThirdMinutes = adjustTravelTimeByVehicleCount(thirdSegmentMinutes, thirdRouteVehicles);

        Boolean isFirstTransferMajor = row.get("first_transfer_is_major", Boolean.class);
        Boolean isSecondTransferMajor = row.get("second_transfer_is_major", Boolean.class);

        int firstTransferWait = calculateTransferWaitTime(isFirstTransferMajor, firstRouteVehicles, secondRouteVehicles);
        int secondTransferWait = calculateTransferWaitTime(isSecondTransferMajor, secondRouteVehicles, thirdRouteVehicles);

        return new TwoTransferRouteResult(
                firstRoute, fromStop, firstTransferStop,
                secondRoute, secondTransferStop,
                thirdRoute, toStop,
                adjustedFirstMinutes,
                firstTransferWait,
                adjustedSecondMinutes,
                secondTransferWait,
                adjustedThirdMinutes,
                0.0,
                0.0
        );
    }


    private String[] extractStopIds(List<BusStop> stops) {
        return stops.stream()
                .map(stop -> stop.getId().getValue())
                .filter(Objects::nonNull)
                .toArray(String[]::new);
    }

    private int adjustTravelTimeByVehicleCount(Integer baseMinutes, Long vehicleCount) {
        if (baseMinutes == null) return 10;
        if (vehicleCount == null || vehicleCount == 0) return baseMinutes + 3;
        if (vehicleCount >= 3) return baseMinutes;
        return baseMinutes + 2;
    }

    private int calculateTransferWaitTime(Boolean isMajorStop, Long fromRouteVehicles, Long toRouteVehicles) {
        int baseTransferTime = Boolean.TRUE.equals(isMajorStop) ? 3 : 5;

        long fromVehicles = fromRouteVehicles != null ? fromRouteVehicles : 0L;
        long toVehicles = toRouteVehicles != null ? toRouteVehicles : 0L;
        long avgVehicles = (fromVehicles + toVehicles) / 2;

        if (avgVehicles == 0) return baseTransferTime + 5;
        if (avgVehicles >= 4) return baseTransferTime;
        if (avgVehicles >= 2) return baseTransferTime + 2;
        return baseTransferTime + 3;
    }
}
