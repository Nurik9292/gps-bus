package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors vehicle positions and triggers push notifications when a bus
 * is approaching a stop where users are waiting.
 *
 * <p>Architecture:
 * <ul>
 *   <li>Uses TRUE position (lastGpsFraction / raw GPS) — not the visual predicted position</li>
 *   <li>Uses smoothed speed for stable ETA</li>
 *   <li>Checks confidence level — only notifies on HIGH/MEDIUM confidence</li>
 *   <li>Debounces notifications — same (vehicle, stop) pair notified at most once per trip</li>
 * </ul>
 *
 * <p>TODO (next steps):
 * <ul>
 *   <li>Integrate with FCM/APNs for actual push delivery</li>
 *   <li>Add user subscription model (which users watch which stops)</li>
 *   <li>Add configurable notification thresholds (e.g. notify at 2 min, 5 min)</li>
 *   <li>Add stop-specific dwell time from historical data</li>
 * </ul>
 */
@Service
@Slf4j
public class StopArrivalNotificationService {

    /** Notification thresholds in meters — when bus is within this distance, notify. */
    private static final double NOTIFY_DISTANCE_METERS = 300.0;

    /** Minimum confidence to trigger a notification. */
    private static final PositionConfidence MIN_CONFIDENCE = PositionConfidence.MEDIUM;

    /** Debounce: same (vehicleId, stopId) notified at most once per this interval (ms). */
    private static final long DEBOUNCE_MS = 5 * 60 * 1000; // 5 minutes

    private final VehiclePositionPredictionService predictionService;
    private final RouteGeometryCache routeGeometryCache;

    /** Key = "vehicleId:stopId", Value = timestamp of last notification. */
    private final Map<String, Long> notificationDebounce = new ConcurrentHashMap<>();

    public StopArrivalNotificationService(VehiclePositionPredictionService predictionService,
                                           RouteGeometryCache routeGeometryCache) {
        this.predictionService = predictionService;
        this.routeGeometryCache = routeGeometryCache;
    }

    /**
     * Periodic check: for each actively predicted vehicle, compute distance to upcoming stops.
     * If a bus is approaching a stop within NOTIFY_DISTANCE_METERS and confidence is sufficient,
     * trigger a notification (currently just logs — replace with FCM/APNs integration).
     */
    @Scheduled(fixedDelay = 5_000)
    public void checkUpcomingArrivals() {
        List<VehiclePredictionState> activeStates = predictionService.getActiveStates();
        if (activeStates.isEmpty()) return;

        Instant now = Instant.now();

        for (VehiclePredictionState state : activeStates) {
            PositionConfidence confidence = predictionService.getConfidence(state.getVehicleId());
            if (confidence.ordinal() > MIN_CONFIDENCE.ordinal()) {
                continue; // Skip LOW/STALE — data not reliable enough for notifications
            }

            // Use TRUE fraction (last GPS snap), not visual fraction
            double trueFraction = state.getLastGpsFraction() >= 0
                    ? state.getLastGpsFraction()
                    : state.getFractionOnRoute();
            if (trueFraction < 0) continue;

            double totalDist = state.getTotalRouteDistanceMeters();
            if (totalDist <= 0 || state.getRouteNumber() == null) continue;

            // Get stops ahead of true position
            List<biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo> stopsAhead =
                    routeGeometryCache.getStopsAhead(state.getRouteNumber(), state.getDirection(), trueFraction);

            for (biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo stop : stopsAhead) {
                double stopFrac = stop.getDistanceFromStartMeters() / totalDist;
                double distMeters = (stopFrac - trueFraction) * totalDist;

                if (distMeters > NOTIFY_DISTANCE_METERS) {
                    break; // Stops are sorted — all subsequent are farther
                }

                if (distMeters > 0 && distMeters <= NOTIFY_DISTANCE_METERS) {
                    String debounceKey = state.getVehicleId() + ":" + stop.getStopId();
                    Long lastNotified = notificationDebounce.get(debounceKey);
                    if (lastNotified != null && (now.toEpochMilli() - lastNotified) < DEBOUNCE_MS) {
                        continue; // Already notified recently
                    }

                    // Compute ETA
                    double speedKmh = state.getSmoothedSpeedKmh() > 0
                            ? state.getSmoothedSpeedKmh()
                            : state.getRawGpsSpeedKmh();
                    int etaSeconds = speedKmh > 1.0
                            ? (int) Math.ceil(distMeters / (speedKmh / 3.6))
                            : -1;

                    // TRIGGER NOTIFICATION
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

        // Cleanup old debounce entries
        long cutoff = now.toEpochMilli() - DEBOUNCE_MS * 2;
        notificationDebounce.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
