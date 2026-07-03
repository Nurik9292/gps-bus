package biz.ugur.busroutebackend.replay.synth;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SyntheticScenario {

    public record Params(
            long seed,
            double fixIntervalSec,
            double positionSigmaMeters,
            double tugdkAccuracyMeters,
            Instant startTime,
            String vehicleId,
            String plate,
            String route,
            int direction) {

        public static Params defaults(long seed, String route, int direction) {
            return new Params(seed, 7.0, 5.0, 5.0,
                    Instant.parse("2026-07-03T06:00:00Z"),
                    "veh-syn-" + seed, "SYN " + seed, route, direction);
        }
    }

    public record Track(List<GpsFix> fixes, List<double[]> truth) {}

    private SyntheticScenario() {}

    public static Track departureRamp(GeometryFixture g, Params p,
                                      double startS, double cruiseSpeedMs, double accelMs2,
                                      double standstillSec, double totalSec) {
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        double s = startS;
        double v = 0;
        for (double t = 0; t <= totalSec; t += p.fixIntervalSec()) {
            if (t >= standstillSec) {
                double vNext = Math.min(v + accelMs2 * p.fixIntervalSec(), cruiseSpeedMs);
                s = Math.min(s + 0.5 * (v + vNext) * p.fixIntervalSec(), g.totalMeters());
                v = vNext;
            }
            truth.add(new double[]{t, s, v});
            fixes.add(fixAt(g, p, rnd, t, s, v, 0.0));
        }
        return new Track(fixes, truth);
    }

    public static Track cruiseWithForwardSnapBias(GeometryFixture g, Params p,
                                                  double startS, double cruiseSpeedMs,
                                                  double biasMeters, double totalSec) {
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        double s = startS;
        for (double t = 0; t <= totalSec; t += p.fixIntervalSec()) {
            truth.add(new double[]{t, s, cruiseSpeedMs});
            fixes.add(fixAt(g, p, rnd, t, s, cruiseSpeedMs, biasMeters));
            s = Math.min(s + cruiseSpeedMs * p.fixIntervalSec(), g.totalMeters());
        }
        return new Track(fixes, truth);
    }

    private static GpsFix fixAt(GeometryFixture g, Params p, Random rnd,
                                double t, double trueS, double speedMs, double alongBiasMeters) {
        double emittedS = Math.min(trueS + alongBiasMeters, g.totalMeters());
        double[] pt = g.pointAtS(emittedS);
        double mLat = 111320.0;
        double mLon = 111320.0 * Math.cos(Math.toRadians(pt[0]));
        double lat = pt[0] + rnd.nextGaussian() * p.positionSigmaMeters() / mLat;
        double lon = pt[1] + rnd.nextGaussian() * p.positionSigmaMeters() / mLon;
        Instant ts = p.startTime().plusMillis((long) (t * 1000));
        double hdop = Math.max(0.6, 1.0 + rnd.nextGaussian() * 0.2);
        int sats = Math.max(5, 9 + (int) Math.round(rnd.nextGaussian()));
        return new GpsFix(p.vehicleId(), p.plate(), p.route(),
                round7(lat), round7(lon), speedMs * 3.6, courseAt(g, emittedS),
                speedMs * 3.6 >= 1.0, ts, p.direction(),
                round2(hdop), sats, p.tugdkAccuracyMeters(), ts);
    }

    private static double courseAt(GeometryFixture g, double s) {
        double[] a = g.pointAtS(Math.max(0, s - 5));
        double[] b = g.pointAtS(Math.min(g.totalMeters(), s + 5));
        double dLon = Math.toRadians(b[1] - a[1]);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(b[0]));
        double x = Math.cos(Math.toRadians(a[0])) * Math.sin(Math.toRadians(b[0]))
                - Math.sin(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static double round7(double v) {
        return Math.round(v * 1e7) / 1e7;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    public static void saveTruth(java.nio.file.Path file, List<double[]> truth) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("t_sec,s_true_m,v_true_ms");
            for (double[] row : truth) {
                lines.add(row[0] + "," + row[1] + "," + row[2]);
            }
            java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());
            java.nio.file.Files.write(file, lines);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
