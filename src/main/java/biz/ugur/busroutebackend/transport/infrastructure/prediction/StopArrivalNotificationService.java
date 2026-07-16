package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class StopArrivalNotificationService {

    private static final double NOTIFY_DISTANCE_METERS = 300.0;

    private static final PositionConfidence MIN_CONFIDENCE = PositionConfidence.MEDIUM;

    private static final long DEBOUNCE_MS = 5 * 60 * 1000; // 5 minutes

    private final VehiclePositionPredictionService predictionService;
    private final RouteGeometryCache routeGeometryCache;

    private final Map<String, Long> notificationDebounce = new ConcurrentHashMap<>();

    public StopArrivalNotificationService(VehiclePositionPredictionService predictionService,
                                           RouteGeometryCache routeGeometryCache) {
        this.predictionService = predictionService;
        this.routeGeometryCache = routeGeometryCache;
    }

    @Scheduled(fixedDelay = 5_000)
    public void checkUpcomingArrivals() {
        List<VehiclePredictionState> activeStates = predictionService.getActiveStates();
        if (activeStates.isEmpty()) return;

        Instant now = Instant.now();

        for (VehiclePredictionState state : activeStates) {
            PositionConfidence confidence = predictionService.getConfidence(state.getVehicleId());
            if (confidence.ordinal() > MIN_CONFIDENCE.ordinal()) {
                continue; 
            }

            double trueFraction = state.getLastGpsFraction() >= 0
                    ? state.getLastGpsFraction()
                    : state.getFractionOnRoute();
            if (trueFraction < 0) continue;

            double totalDist = state.getTotalRouteDistanceMeters();
            if (totalDist <= 0 || state.getRouteNumber() == null) continue;

            List<biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo> stopsAhead =
                    routeGeometryCache.getStopsAhead(state.getRouteId(), state.getDirection(), trueFraction);

            for (biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo stop : stopsAhead) {
                double stopFrac = stop.getDistanceFromStartMeters() / totalDist;
                double distMeters = (stopFrac - trueFraction) * totalDist;

                if (distMeters > NOTIFY_DISTANCE_METERS) {
                    break; 
                }

                if (distMeters > 0 && distMeters <= NOTIFY_DISTANCE_METERS) {
                    String debounceKey = state.getVehicleId() + ":" + stop.getStopId();
                    Long lastNotified = notificationDebounce.get(debounceKey);
                    if (lastNotified != null && (now.toEpochMilli() - lastNotified) < DEBOUNCE_MS) {
                        continue; 
                    }

                    double speedKmh = state.getSmoothedSpeedKmh() > 0
                            ? state.getSmoothedSpeedKmh()
                            : state.getRawGpsSpeedKmh();
                    int etaSeconds = speedKmh > 1.0
                            ? (int) Math.ceil(distMeters / (speedKmh / 3.6))
                            : -1;

                    log.info("[NOTIFICATION] APPROACHING vehicle={} plate={} route={} " +
                                    "stop={} dist={}m eta={}s confidence={}",
                            state.getVehicleId(),
                            state.getLicensePlate(),
                            state.getRouteNumber(),
                            stop.getStopId(),
                            String.format("%.0f", distMeters),
                            etaSeconds,
                            confidence);

                    notificationDebounce.put(debounceKey, now.toEpochMilli());

                    // TODO: Replace with actual push notification delivery:
                    // pushNotificationService.sendApproachingBus(
                    //     stop.getStopId(),
                    //     state.getRouteNumber(),
                    //     state.getLicensePlate(),
                    //     etaSeconds,
                    //     distMeters
                    // );
                }
            }
        }

        long cutoff = now.toEpochMilli() - DEBOUNCE_MS * 2;
        notificationDebounce.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
