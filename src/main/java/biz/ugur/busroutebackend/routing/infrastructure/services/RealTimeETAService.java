package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Computes real-time ETA using live GPS positions from the prediction engine.
 * Instead of static DB averages ("route 160 typically takes 15 min"),
 * this finds the actual nearest bus and computes "bus 6252 AGJ will arrive in 3 min 20 sec".
 */
@Service
@Slf4j
public class RealTimeETAService {

    private final VehiclePositionPredictionService predictionService;
    private final RouteGeometryCache routeGeometryCache;

    public RealTimeETAService(VehiclePositionPredictionService predictionService,
                               RouteGeometryCache routeGeometryCache) {
        this.predictionService = predictionService;
        this.routeGeometryCache = routeGeometryCache;
    }

    /**
     * Find the nearest approaching bus on the given route to the given stop.
     * Returns the ETA in minutes, or empty if no bus is approaching.
     *
     * @param routeNumber route to search (e.g. "160")
     * @param stopName    target stop name
     * @return Mono with NearestBusInfo, or empty if no bus found
     */
    public Mono<NearestBusInfo> findNearestBus(String routeNumber, String stopName) {
        return findNearestBus(routeNumber, stopName, Double.NaN, Double.NaN);
    }

    public Mono<NearestBusInfo> findNearestBus(String routeNumber, String stopName,
                                                double stopLat, double stopLon) {
        return Mono.fromCallable(() -> {
            List<VehiclePredictionState> allStates = predictionService.getActiveStates();
            if (allStates.isEmpty()) return null;

            // Filter vehicles on this route
            List<VehiclePredictionState> onRoute = allStates.stream()
                    .filter(s -> routeNumber.equals(s.getRouteNumber()))
                    .filter(s -> s.isInMotion() && s.getSpeedKmh() > 0)
                    .filter(s -> s.getLastGpsFraction() >= 0)
                    .toList();

            if (onRoute.isEmpty()) {
                log.debug("No active vehicles on route {} for real-time ETA", routeNumber);
                return null;
            }

            // Find stop fraction on each direction
            NearestBusInfo best = null;

            for (int direction = 0; direction <= 1; direction++) {
                OptionalDouble stopFracOpt = routeGeometryCache.getStopFractionByName(routeNumber, direction, stopName);
                if (stopFracOpt.isEmpty() && !Double.isNaN(stopLat) && !Double.isNaN(stopLon)) {
                    stopFracOpt = routeGeometryCache.getStopFractionByCoordinates(
                            routeNumber, direction, stopLat, stopLon, 80.0);
                }
                if (stopFracOpt.isEmpty()) continue;

                double stopFrac = stopFracOpt.getAsDouble();
                double totalDist = routeGeometryCache.getTotalDistance(routeNumber, direction);
                if (totalDist <= 0) continue;

                for (VehiclePredictionState vehicle : onRoute) {
                    if (vehicle.getDirection() != direction) continue;

                    double vehicleFrac = vehicle.getLastGpsFraction();
                    // Vehicle must be BEFORE the stop (lower fraction) to be approaching.
                    // Allow a tiny epsilon (0.5%) so a bus right AT the stop still counts.
                    if (vehicleFrac >= stopFrac + 0.005) continue;
                    if (vehicleFrac < stopFrac - 0.5) continue; // > half the route away → ignore

                    double distanceMeters = Math.max(0, (stopFrac - vehicleFrac) * totalDist);
                    // Approaching bus must be within 5 km of the boarding stop
                    if (distanceMeters > 5000) continue;

                    double speedKmh = vehicle.getSmoothedSpeedKmh() > 0
                            ? vehicle.getSmoothedSpeedKmh()
                            : vehicle.getRawGpsSpeedKmh();
                    if (speedKmh < 5) continue; // ignore stopped/creeping buses

                    double etaMinutes = (distanceMeters / 1000.0) / speedKmh * 60.0;
                    int etaMin = (int) Math.ceil(etaMinutes);
                    if (etaMin > 20) continue; // too far in the future to be useful

                    if (best == null || etaMin < best.etaMinutes()) {
                        best = new NearestBusInfo(
                                vehicle.getVehicleId(),
                                vehicle.getLicensePlate(),
                                routeNumber,
                                direction,
                                etaMin,
                                (int) distanceMeters,
                                speedKmh
                        );
                    }
                }
            }

            if (best != null) {
                log.info("Real-time ETA: route={} stop={} → bus {} in {}min ({}m, {}km/h)",
                        routeNumber, stopName, best.licensePlate(), best.etaMinutes(),
                        best.distanceMeters(), Math.round(best.speedKmh()));
            }

            return best;
        });
    }

    /**
     * Get real-time waiting time for a bus at a given stop.
     * Returns the ETA of the nearest approaching bus in minutes.
     */
    public Mono<Integer> getWaitingTimeMinutes(String routeNumber, String stopName) {
        return findNearestBus(routeNumber, stopName)
                .map(NearestBusInfo::etaMinutes);
    }

    public record NearestBusInfo(
            String vehicleId,
            String licensePlate,
            String routeNumber,
            int direction,
            int etaMinutes,
            int distanceMeters,
            double speedKmh
    ) {}
}
