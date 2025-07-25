package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
@Service
@Slf4j
public class LiveETACalculationService implements ETACalculationService {

    private final VehicleRepository vehicleRepository;
    private final DatabaseClient databaseClient;

    public LiveETACalculationService(VehicleRepository vehicleRepository,
                                     DatabaseClient databaseClient) {
        this.vehicleRepository = vehicleRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<LocalDateTime> calculateEstimatedArrival(String routeNumber, String fromStopName,
                                                         String toStopName, LocalDateTime departureTime) {
        log.debug("Calculating ETA for route {} from {} to {}", routeNumber, fromStopName, toStopName);

        return calculateTravelTimeMinutes(routeNumber, fromStopName, toStopName)
                .map(departureTime::plusMinutes)
                .doOnNext(eta -> log.debug("Calculated ETA: {}", eta));
    }

    @Override
    public int calculateWalkingTimeMinutes(Location from, Location to) {
        double distanceMeters = from.distanceTo(to);

        // Average walking speed: 5 km/h = 83.33 m/min
        int walkingMinutes = (int) Math.ceil(distanceMeters / 83.33);

        log.trace("Walking time from {} to {}: {} minutes ({}m)",
                from.getDescription(), to.getDescription(), walkingMinutes, Math.round(distanceMeters));

        return Math.max(1, walkingMinutes); // Minimum 1 minute
    }

    @Override
    public Mono<Integer> calculateWaitingTimeMinutes(String routeNumber, String stopName, LocalDateTime currentTime) {
        log.debug("Calculating wait time for route {} at stop {}", routeNumber, stopName);

        // Try to get real-time waiting time based on vehicle positions
        return getVehicleBasedWaitingTime(routeNumber, stopName)
                .switchIfEmpty(getFrequencyBasedWaitingTime(routeNumber, currentTime))
                .doOnNext(waitMinutes -> log.debug("Estimated wait time: {} minutes", waitMinutes));
    }

    @Override
    public Mono<Integer> calculateTravelTimeMinutes(String routeNumber, String fromStopName, String toStopName) {
        log.debug("Calculating travel time for route {} from {} to {}", routeNumber, fromStopName, toStopName);

        String sql = """
            SELECT 
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as estimated_minutes,
                ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as distance_meters
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            JOIN bus_routes br ON rs1.route_id = br.id
            JOIN bus_stops bs1 ON rs1.stop_id = bs1.id
            JOIN bus_stops bs2 ON rs2.stop_id = bs2.id
            WHERE br.route_number = :routeNumber
            AND bs1.stop_name ILIKE :fromStopName
            AND bs2.stop_name ILIKE :toStopName
            AND br.is_active = true
            LIMIT 1
            """;

        return databaseClient.sql(sql)
                .bind("routeNumber", routeNumber)
                .bind("fromStopName", "%" + fromStopName + "%")
                .bind("toStopName", "%" + toStopName + "%")
                .map(row -> row.get("estimated_minutes", Integer.class))
                .one()
                .switchIfEmpty(Mono.fromCallable(() -> {
                    // Fallback: simple estimation based on stop names
                    return 15; // Default 15 minutes if no data found
                }))
                .doOnNext(travelMinutes -> log.debug("Estimated travel time: {} minutes", travelMinutes));
    }

    @Override
    public int calculateTransferTimeMinutes(String stopName, boolean isMajorStop) {
        // Transfer time depends on stop infrastructure and size
        int baseTransferTime;

        if (isMajorStop) {
            baseTransferTime = 3; // Major stops are more efficient - better signage, platforms
        } else {
            baseTransferTime = 5; // Regular stops take a bit longer
        }

        // Add extra time for peak hours or complex stops
        if (stopName.toLowerCase().contains("airport") ||
                stopName.toLowerCase().contains("bazaar") ||
                stopName.toLowerCase().contains("center")) {
            baseTransferTime += 2; // Busy locations need more time
        }

        log.trace("Transfer time at {} (major: {}): {} minutes", stopName, isMajorStop, baseTransferTime);
        return baseTransferTime;
    }

    // Private helper methods

    private Mono<Integer> getVehicleBasedWaitingTime(String routeNumber, String stopName) {
        // Find vehicles on this route and calculate when next one arrives at stop
        return databaseClient.sql("""
            SELECT 
                v.current_latitude, v.current_longitude, 
                v.speed_kmh, v.is_in_motion,
                v.last_position_update
            FROM vehicles v
            JOIN bus_routes br ON v.assigned_route_id = br.id
            WHERE br.route_number = :routeNumber
            AND v.is_active = true
            AND v.last_position_update > CURRENT_TIMESTAMP - INTERVAL '10 minutes'
            ORDER BY v.last_position_update DESC
            LIMIT 5
            """)
                .bind("routeNumber", routeNumber)
                .map(row -> {
                    // Simple calculation based on vehicle position and speed
                    // In production, this would be more sophisticated
                    Boolean isInMotion = row.get("is_in_motion", Boolean.class);
                    Double speed = row.get("speed_kmh", Double.class);

                    if (Boolean.TRUE.equals(isInMotion) && speed != null && speed > 5) {
                        return 8; // Moving vehicle, shorter wait
                    } else {
                        return 15; // Stationary or slow vehicle, longer wait
                    }
                })
                .all()
                .collectList()
                .filter(list -> !list.isEmpty())
                .map(waitTimes -> waitTimes.stream().mapToInt(Integer::intValue).min().orElse(10));
    }

    private Mono<Integer> getFrequencyBasedWaitingTime(String routeNumber, LocalDateTime currentTime) {
        return Mono.fromCallable(() -> {
            int hour = currentTime.getHour();

            // Frequency-based calculation depending on time of day
            if (hour >= 6 && hour <= 9) {
                return 8; // Rush hour - more frequent service
            } else if (hour >= 17 && hour <= 20) {
                return 10; // Evening rush hour
            } else if (hour >= 22 || hour <= 5) {
                return 25; // Night time - less frequent service
            } else {
                return 15; // Regular hours
            }
        });
    }
}
