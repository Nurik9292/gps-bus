package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.domain.valueobjects.TimePeriod;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import biz.ugur.busroutebackend.transport.infrastructure.config.TerminalDepartureProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class TerminalDepartureEtaService {

    private static final ZoneId ASHGABAT = ZoneId.of("Asia/Ashgabat");
    private static final int HISTORICAL_ETA_MIN_SAMPLES = 3;
    private static final int LIVE_FACTOR_HORIZON_SEGMENTS = 5;

    public record DepartureStopEta(String stopId, String stopName,
                                   int cumulativeSeconds, int distanceMeters,
                                   int departDirection) {
    }

    private final TerminalDepartureProperties properties;
    private final TerminalDwellSnapshotHolder dwellSnapshotHolder;
    private final TerminalPresenceHolder presenceHolder;
    private final RouteGeometryCache routeGeometryCache;
    private final VehiclePositionPredictor predictor;
    private final LiveFactorSnapshotHolder liveFactorSnapshotHolder;

    public TerminalDepartureEtaService(TerminalDepartureProperties properties,
                                       TerminalDwellSnapshotHolder dwellSnapshotHolder,
                                       TerminalPresenceHolder presenceHolder,
                                       RouteGeometryCache routeGeometryCache,
                                       @Lazy VehiclePositionPredictor predictor,
                                       LiveFactorSnapshotHolder liveFactorSnapshotHolder) {
        this.properties = properties;
        this.dwellSnapshotHolder = dwellSnapshotHolder;
        this.presenceHolder = presenceHolder;
        this.routeGeometryCache = routeGeometryCache;
        this.predictor = predictor;
        this.liveFactorSnapshotHolder = liveFactorSnapshotHolder;
    }

    public void retainVehicles(java.util.Set<String> activeVehicleIds) {
        presenceHolder.retainVehicles(activeVehicleIds);
    }

    public boolean enabled() {
        return properties.getMode() == TerminalDepartureProperties.Mode.LIVE;
    }

    public List<DepartureStopEta> departureEtasForVehicle(String vehicleId,
                                                          String routeIdForGeometry, Instant now) {
        if (!enabled()) {
            return List.of();
        }
        return presenceHolder.presentAt(vehicleId, now, properties.getDwellMaxSeconds())
                .filter(presence -> presence.routeId().equals(routeIdForGeometry))
                .map(presence -> departureEtas(presence, routeIdForGeometry, now))
                .orElse(List.of());
    }

    public List<DepartureStopEta> departureEtas(TerminalPresenceHolder.TerminalPresence presence,
                                                String routeIdForGeometry, Instant now) {
        if (!enabled() || presence == null || routeIdForGeometry == null) {
            return List.of();
        }
        int arrivalHour = LocalDateTime.ofInstant(presence.arrivedAt(), ASHGABAT).getHour();
        var dwellOpt = dwellSnapshotHolder.dwell(presence.routeId(),
                presence.arrivedDirection(), arrivalHour);
        if (dwellOpt.isEmpty() || dwellOpt.get().sampleCount() < properties.getMinSamples()) {
            return List.of();
        }
        long elapsedSeconds = now.getEpochSecond() - presence.arrivedAt().getEpochSecond();
        if (elapsedSeconds < 0 || elapsedSeconds > properties.getDwellMaxSeconds()) {
            return List.of();
        }
        double remainingSeconds = Math.max(properties.getFloorSeconds(),
                dwellOpt.get().avgDwellSeconds() - elapsedSeconds);

        int departDirection = 1 - presence.arrivedDirection();
        List<RouteStopInfo> stops = routeGeometryCache.getRouteStops(routeIdForGeometry, departDirection);
        if (stops.isEmpty()) {
            return List.of();
        }

        LocalDateTime local = LocalDateTime.ofInstant(now, ASHGABAT);
        TimePeriod period = TimePeriod.fromDateTime(local);
        boolean weekend = TimePeriod.isWeekend(local);
        int hourOfDay = local.getHour();
        double fallbackSpeedKmh = period.getAverageSpeedKmh();
        double trafficMultiplier = period.getTrafficMultiplier(weekend);

        List<DepartureStopEta> etas = new ArrayList<>();
        double cumulativeSeconds = remainingSeconds;
        String prevStopId = null;
        int prevDistanceMeters = 0;
        for (RouteStopInfo stop : stops) {
            if (etas.size() >= properties.getMaxStops()) {
                break;
            }
            Integer distanceMeters = stop.getDistanceFromStartMeters();
            if (distanceMeters == null) {
                break;
            }
            if (prevStopId != null) {
                cumulativeSeconds += segmentSeconds(presence.routeId(), departDirection,
                        prevStopId, stop.getStopId(), hourOfDay, weekend,
                        distanceMeters - prevDistanceMeters, fallbackSpeedKmh,
                        trafficMultiplier, etas.size() - 1);
            }
            etas.add(new DepartureStopEta(stop.getStopId(), stop.getStopName(),
                    (int) Math.ceil(cumulativeSeconds), distanceMeters, departDirection));
            prevStopId = stop.getStopId();
            prevDistanceMeters = distanceMeters;
        }
        return etas;
    }

    private double segmentSeconds(String routeId, int direction,
                                  String fromStopId, String toStopId,
                                  int hourOfDay, boolean weekend,
                                  int distanceMeters, double fallbackSpeedKmh,
                                  double trafficMultiplier, int segmentIndexAhead) {
        double seconds;
        SegmentTravelStat historical = predictor.getSegmentTravelStat(
                routeId, direction, fromStopId, toStopId, hourOfDay, weekend);
        var sharedEdge = liveFactorSnapshotHolder.edgeBaseline(fromStopId, toStopId);
        if (historical != null && historical.getSampleCount() >= HISTORICAL_ETA_MIN_SAMPLES) {
            seconds = historical.getAvgTravelSeconds();
        } else if (sharedEdge != null && sharedEdge.n() >= HISTORICAL_ETA_MIN_SAMPLES) {
            seconds = sharedEdge.meanSec();
        } else {
            seconds = (Math.max(0, distanceMeters) / 1000.0 / fallbackSpeedKmh)
                    * 3600.0 * trafficMultiplier;
        }
        return seconds * horizonDampedLiveFactor(
                liveFactorSnapshotHolder.factor(fromStopId, toStopId), segmentIndexAhead);
    }

    private static double horizonDampedLiveFactor(double factor, int segmentIndexAhead) {
        if (segmentIndexAhead >= LIVE_FACTOR_HORIZON_SEGMENTS) {
            return 1.0;
        }
        double weight = (LIVE_FACTOR_HORIZON_SEGMENTS - segmentIndexAhead)
                / (double) LIVE_FACTOR_HORIZON_SEGMENTS;
        return 1.0 + (factor - 1.0) * weight;
    }
}
