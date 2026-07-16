package biz.ugur.busroutebackend.transport.infrastructure.debug.synthetic;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SyntheticGpsGenerator {

    public record Spec(List<double[]> polyline,
                       double startArcMeters,
                       double dtSec,
                       int sampleCount,
                       Instant startTime,
                       String vehicleId,
                       String licensePlate,
                       String routeNumber,
                       int direction) {}

    public record Fix(String vehicleId, String licensePlate, String routeNumber,
                      double latitude, double longitude, double speedKmh, double course,
                      boolean inMotion, Instant timestamp, int direction,
                      Double hdop, Integer satellites, Double accuracy) {}

    public record GeneratedTrack(List<Fix> fixes,
                                 double[] trueArcLengthMeters,
                                 double totalRouteDistanceMeters) {}

    private static final double SYNTHETIC_HDOP = 1.0;
    private static final int SYNTHETIC_SATELLITES = 9;
    private static final double SYNTHETIC_ACCURACY_METERS = 5.0;
    private static final double MOVING_SPEED_MS = 0.3;

    public GeneratedTrack departureRamp(Spec spec, double targetSpeedMs, double accelMs2) {
        double[] cumDist = cumulativeDistances(spec.polyline());
        double total = cumDist[cumDist.length - 1];

        List<Fix> fixes = new ArrayList<>(spec.sampleCount());
        double[] trueArc = new double[spec.sampleCount()];

        double s = spec.startArcMeters();
        double v = 0.0;
        Instant t = spec.startTime();

        for (int i = 0; i < spec.sampleCount(); i++) {
            trueArc[i] = s;
            fixes.add(fixAtArc(spec, cumDist, s, v, t));

            double vNext = Math.min(v + accelMs2 * spec.dtSec(), targetSpeedMs);
            s = Math.min(s + 0.5 * (v + vNext) * spec.dtSec(), total);
            v = vNext;
            t = t.plusMillis((long) (spec.dtSec() * 1000));
        }
        return new GeneratedTrack(fixes, trueArc, total);
    }

    public GeneratedTrack systematicSnapBias(Spec spec, double speedMs, double biasMeters) {
        double[] cumDist = cumulativeDistances(spec.polyline());
        double total = cumDist[cumDist.length - 1];

        List<Fix> fixes = new ArrayList<>(spec.sampleCount());
        double[] trueArc = new double[spec.sampleCount()];

        double s = spec.startArcMeters();
        Instant t = spec.startTime();

        for (int i = 0; i < spec.sampleCount(); i++) {
            trueArc[i] = s;
            double biasedArc = Math.min(s + biasMeters, total);
            fixes.add(fixAtArc(spec, cumDist, biasedArc, speedMs, t));

            s = Math.min(s + speedMs * spec.dtSec(), total);
            t = t.plusMillis((long) (spec.dtSec() * 1000));
        }
        return new GeneratedTrack(fixes, trueArc, total);
    }

    private Fix fixAtArc(Spec spec, double[] cumDist, double arc, double speedMs, Instant t) {
        double[] point = pointAtArc(spec.polyline(), cumDist, arc);
        double course = bearingAtArc(spec.polyline(), cumDist, arc);
        boolean inMotion = speedMs >= MOVING_SPEED_MS;
        return new Fix(spec.vehicleId(), spec.licensePlate(), spec.routeNumber(),
                point[0], point[1], speedMs * 3.6, course, inMotion, t, spec.direction(),
                SYNTHETIC_HDOP, SYNTHETIC_SATELLITES, SYNTHETIC_ACCURACY_METERS);
    }

    public static double[] cumulativeDistances(List<double[]> polyline) {
        double[] cum = new double[polyline.size()];
        cum[0] = 0.0;
        for (int i = 1; i < polyline.size(); i++) {
            cum[i] = cum[i - 1] + DistanceCalculationService.haversineDistanceMeters(
                    polyline.get(i - 1)[0], polyline.get(i - 1)[1],
                    polyline.get(i)[0], polyline.get(i)[1]);
        }
        return cum;
    }

    public static double[] pointAtArc(List<double[]> polyline, double[] cumDist, double arc) {
        double total = cumDist[cumDist.length - 1];
        double target = Math.max(0, Math.min(arc, total));
        int seg = segmentIndex(cumDist, target);
        double[] a = polyline.get(seg);
        double[] b = polyline.get(seg + 1);
        double segLen = cumDist[seg + 1] - cumDist[seg];
        double f = segLen == 0 ? 0 : (target - cumDist[seg]) / segLen;
        return new double[]{a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1])};
    }

    public static double bearingAtArc(List<double[]> polyline, double[] cumDist, double arc) {
        int seg = segmentIndex(cumDist, Math.max(0, Math.min(arc, cumDist[cumDist.length - 1])));
        double[] a = polyline.get(seg);
        double[] b = polyline.get(seg + 1);
        double dLon = Math.toRadians(b[1] - a[1]);
        double rlat1 = Math.toRadians(a[0]);
        double rlat2 = Math.toRadians(b[0]);
        double y = Math.sin(dLon) * Math.cos(rlat2);
        double x = Math.cos(rlat1) * Math.sin(rlat2) - Math.sin(rlat1) * Math.cos(rlat2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static int segmentIndex(double[] cumDist, double target) {
        for (int i = 0; i < cumDist.length - 1; i++) {
            if (target <= cumDist[i + 1]) {
                return i;
            }
        }
        return cumDist.length - 2;
    }

    public static List<String> toJsonl(GeneratedTrack track, ObjectMapper objectMapper) {
        List<String> lines = new ArrayList<>(track.fixes().size());
        for (Fix fix : track.fixes()) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("vehicleId", fix.vehicleId());
            event.put("licensePlate", fix.licensePlate());
            event.put("routeNumber", fix.routeNumber());
            event.put("latitude", fix.latitude());
            event.put("longitude", fix.longitude());
            event.put("speedKmh", fix.speedKmh());
            event.put("course", fix.course());
            event.put("inMotion", fix.inMotion());
            event.put("timestamp", fix.timestamp().toString());
            event.put("direction", fix.direction());
            event.put("hdop", fix.hdop());
            event.put("satellites", fix.satellites());
            event.put("accuracy", fix.accuracy());
            event.put("wallClock", fix.timestamp().toString());
            try {
                lines.add(objectMapper.writeValueAsString(event));
            } catch (Exception e) {
                throw new IllegalStateException("failed to serialize synthetic fix", e);
            }
        }
        return lines;
    }
}
