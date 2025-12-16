package biz.ugur.busroutebackend.routing.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.routing.domain.repository.ETARepository;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;


@Repository
@Slf4j
@RequiredArgsConstructor
public class R2dbcETARepository implements ETARepository {

    private final DatabaseClient databaseClient;
    private final ETAProperties etaProperties;

    @Override
    public Mono<Integer> getVehicleBasedWaitingTime(String routeNumber, String stopName) {
        int maxAgeMinutes = etaProperties.getPosition().getMaxAgeMinutes();

        return databaseClient.sql("""
            WITH route_vehicles AS (
                SELECT
                    v.id, v.current_latitude, v.current_longitude,
                    v.speed_kmh, v.is_in_motion, v.last_position_update,
                    ST_Distance(
                        ST_Point(v.current_longitude, v.current_latitude)::geography,
                        ST_Point(bs.longitude, bs.latitude)::geography
                    ) as distance_to_stop
                FROM vehicles v
                JOIN bus_routes br ON v.assigned_route_id = br.id
                JOIN route_stops rs ON br.id = rs.route_id
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE br.route_number = :routeNumber
                AND bs.stop_name ILIKE :stopName
                AND v.is_active = true
                AND v.last_position_update > CURRENT_TIMESTAMP - (INTERVAL '1 minute' * :maxAgeMinutes)
                ORDER BY distance_to_stop
                LIMIT 3
            )
            SELECT
                CASE
                    WHEN COUNT(*) = 0 THEN NULL
                    WHEN MIN(distance_to_stop) < 500 THEN 2
                    WHEN MIN(distance_to_stop) < 1000 THEN 5
                    WHEN AVG(speed_kmh) > 10 THEN 8
                    ELSE 12
                END as estimated_wait_minutes
            FROM route_vehicles
            """)
                .bind("routeNumber", routeNumber)
                .bind("stopName", "%" + stopName + "%")
                .bind("maxAgeMinutes", maxAgeMinutes)
                .map(row -> row.get("estimated_wait_minutes", Integer.class))
                .one()
                .filter(waitTime -> waitTime != null)
                .doOnNext(waitTime -> log.debug("Vehicle-based wait time for route {}: {} minutes",
                        routeNumber, waitTime));
    }

    @Override
    public Mono<Integer> getStatisticalWaitingTime(String routeNumber, LocalDateTime currentTime) {
        return databaseClient.sql("""
            WITH popular_routes AS (
                -- Динамическое определение популярных маршрутов по количеству активных автобусов
                SELECT route_number
                FROM mv_active_routes_summary
                WHERE active_vehicles >= 3
                ORDER BY active_vehicles DESC, moving_vehicles DESC
                LIMIT 10
            )
            SELECT
                CASE
                    WHEN br.route_number IN ('1', '2', '3', '4', '5') THEN 6    -- Основные маршруты
                    WHEN br.route_number IN (SELECT route_number FROM popular_routes) THEN 8  -- Популярные маршруты (динамически)
                    WHEN br.route_number SIMILAR TO '%[A-Z]' THEN 12            -- Экспресс маршруты
                    ELSE 10                                                      -- Обычные маршруты
                END as base_wait_time,
                CASE
                    WHEN :hour BETWEEN 7 AND 9 THEN -2    -- Час пик утром - чаще
                    WHEN :hour BETWEEN 17 AND 19 THEN -2  -- Час пик вечером - чаще
                    WHEN :hour BETWEEN 22 AND 6 THEN 5    -- Ночь - реже
                    ELSE 0                                 -- Обычное время
                END as time_adjustment
            FROM bus_routes br
            WHERE br.route_number = :routeNumber
            AND br.is_active = true
            LIMIT 1
            """)
                .bind("routeNumber", routeNumber)
                .bind("hour", currentTime.getHour())
                .map(row -> {
                    Integer baseTime = row.get("base_wait_time", Integer.class);
                    Integer adjustment = row.get("time_adjustment", Integer.class);
                    return (baseTime != null ? baseTime : 10) + (adjustment != null ? adjustment : 0);
                })
                .one()
                .doOnNext(waitTime -> log.debug("Statistical wait time for route {}: {} minutes",
                        routeNumber, waitTime));
    }

    @Override
    public Mono<Integer> calculateTravelTimeFromDatabase(String routeNumber, String fromStopName, String toStopName) {
        return databaseClient.sql("""
            WITH route_segments AS (
                SELECT
                    rs1.stop_sequence as from_sequence,
                    rs2.stop_sequence as to_sequence,
                    rs1.direction,
                    ABS(rs2.stop_sequence - rs1.stop_sequence) as stops_count,
                    ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as distance_meters,
                    rs2.estimated_travel_time_minutes - rs1.estimated_travel_time_minutes as scheduled_time
                FROM route_stops rs1
                JOIN route_stops rs2 ON rs1.route_id = rs2.route_id
                                    AND rs1.direction = rs2.direction
                                    AND rs1.stop_sequence < rs2.stop_sequence
                JOIN bus_routes br ON rs1.route_id = br.id
                JOIN bus_stops bs1 ON rs1.stop_id = bs1.id
                JOIN bus_stops bs2 ON rs2.stop_id = bs2.id
                WHERE br.route_number = :routeNumber
                AND bs1.stop_name ILIKE :fromStopName
                AND bs2.stop_name ILIKE :toStopName
                AND br.is_active = true
                ORDER BY stops_count
                LIMIT 1
            )
            SELECT
                COALESCE(scheduled_time, stops_count * 2) as estimated_minutes,
                stops_count,
                distance_meters
            FROM route_segments
            """)
                .bind("routeNumber", routeNumber)
                .bind("fromStopName", "%" + fromStopName + "%")
                .bind("toStopName", "%" + toStopName + "%")
                .map(row -> {
                    Integer estimatedMinutes = row.get("estimated_minutes", Integer.class);
                    Integer stopsCount = row.get("stops_count", Integer.class);
                    Integer distanceMeters = row.get("distance_meters", Integer.class);

                    if (estimatedMinutes != null && estimatedMinutes > 0) {
                        return adjustForTrafficConditions(estimatedMinutes);
                    }

                    int calculatedTime = (stopsCount != null ? stopsCount * 2 : 10);

                    if (distanceMeters != null && distanceMeters > 0) {
                        int timeFromDistance = (int) Math.ceil(distanceMeters / (20.0 * 1000.0 / 60.0));
                        calculatedTime = Math.max(calculatedTime, timeFromDistance);
                    }

                    return adjustForTrafficConditions(calculatedTime);
                })
                .one()
                .doOnNext(travelTime -> log.debug("Travel time for route {} from {} to {}: {} minutes",
                        routeNumber, fromStopName, toStopName, travelTime));
    }


    private int adjustForTrafficConditions(int baseTime) {
        return (int) Math.ceil(baseTime * 1.1);
    }
}
