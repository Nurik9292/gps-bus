package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.OptionalDouble;


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


    public Mono<NearestBusInfo> findNearestBus(String routeNumber, String stopName) {
        return findNearestBus(routeNumber, stopName, Double.NaN, Double.NaN);
    }

    public Mono<NearestBusInfo> findNearestBus(String routeNumber, String stopName,
                                                double stopLat, double stopLon) {
        return Mono.fromCallable(() -> {
            List<VehiclePredictionState> allStates = predictionService.getActiveStates();
            if (allStates.isEmpty()) return null;

            List<VehiclePredictionState> onRoute = allStates.stream()
                    .filter(s -> routeNumber.equals(s.getRouteNumber()))
                    .filter(s -> s.isInMotion() && s.getSpeedKmh() > 0)
                    .filter(s -> s.getLastGpsFraction() >= 0)
                    .toList();

            if (onRoute.isEmpty()) {
                log.debug("No active vehicles on route {} for real-time ETA", routeNumber);
                return null;
            }

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
                    if (vehicleFrac >= stopFrac + 0.005) continue;
                    if (vehicleFrac < stopFrac - 0.5) continue; 

                    double distanceMeters = Math.max(0, (stopFrac - vehicleFrac) * totalDist);
                    if (distanceMeters > 5000) continue;

                    double speedKmh = vehicle.getSmoothedSpeedKmh() > 0
                            ? vehicle.getSmoothedSpeedKmh()
                            : vehicle.getRawGpsSpeedKmh();
                    if (speedKmh < 5) continue; 

                    double etaMinutes = (distanceMeters / 1000.0) / speedKmh * 60.0;
                    int etaMin = (int) Math.ceil(etaMinutes);
                    if (etaMin > 20) continue; 

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
