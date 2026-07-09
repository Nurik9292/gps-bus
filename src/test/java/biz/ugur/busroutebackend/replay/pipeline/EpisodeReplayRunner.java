package biz.ugur.busroutebackend.replay.pipeline;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.InputValidator;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.core.StopAware;
import biz.ugur.busroutebackend.replay.metrics.ArrivalDetector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class EpisodeReplayRunner {

    public record EtaBucket(double p95, int n) {}

    public record EpisodeStats(
            String vehicleId,
            String routeNumber,
            int fixesTotal,
            int fixesDropped,
            double durationSec,
            double nullAccuracyShare,
            Map<String, Long> eventCounts,
            long recoveringSpells,
            long leaderSwitches,
            long offRouteTransitions,
            double offRouteShare,
            double gpsLostFrozenShare,
            double p50AbsInnovation,
            double p95AbsInnovation,
            double meanNis,
            int nisN,
            EtaBucket eta60,
            EtaBucket eta120,
            EtaBucket eta300,
            long tripsCompleted,
            double flightMaxRatio,
            long flightViolations,
            long sanctionedJumps) {}

    private EpisodeReplayRunner() {}

    private static java.io.BufferedWriter nisDumpWriter;

    private static synchronized void dumpNisTick(String route, String vid8, Instant ts,
                                                 double nisCandidate, boolean accepted,
                                                 String mode) {
        String path = System.getProperty("corpus.nisdump");
        if (path == null || path.isBlank()) return;
        try {
            if (nisDumpWriter == null) {
                nisDumpWriter = java.nio.file.Files.newBufferedWriter(java.nio.file.Path.of(path));
                nisDumpWriter.write("route|vid8|ts|nis_candidate|accepted|state\n");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        nisDumpWriter.close();
                    } catch (java.io.IOException ignored) {
                        System.err.println("nisdump close failed");
                    }
                }));
            }
            nisDumpWriter.write(String.format(java.util.Locale.ROOT, "%s|%s|%s|%.6f|%s|%s%n",
                    route, vid8, ts, nisCandidate, accepted ? "Y" : "n", mode));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static EpisodeStats run(Episode ep, RouteTopology topo, CoreConfig cfg) {
        MotionFilterCore core = new MotionFilterCore(cfg);
        core.reset();
        InputValidator validator = InputValidator.spec9Defaults();
        GeometryFixture g = topo.first();

        Map<String, Long> events = new TreeMap<>();
        List<Double> absInnovations = new ArrayList<>();
        double nisSum = 0;
        int nisN = 0;
        long recoveringSpells = 0;
        long offTicks = 0;
        long lostTicks = 0;
        int dropped = 0;
        String prevMode = "";
        Instant t0 = ep.fixes().get(0).timestamp();

        Map<String, Double> factArrivalSec = detectArrivals(ep, g);
        List<Double> e60 = new ArrayList<>();
        List<Double> e120 = new ArrayList<>();
        List<Double> e300 = new ArrayList<>();
        List<double[]> broadcastGeo = new ArrayList<>();
        List<Double> broadcastT = new ArrayList<>();
        List<Boolean> sanctioned = new ArrayList<>();
        String prevLeader = null;

        for (GpsFix fx : ep.fixes()) {
            if (!validator.validate(fx).accepted()) {
                dropped++;
                continue;
            }
            PredictionModel.Estimate est = core.onFix(fx, topo);
            String leader = core.bank().leader().variantId();
            broadcastGeo.add(core.bank().leader().geom().pointAtS(est.s()));
            broadcastT.add((fx.timestamp().toEpochMilli() - t0.toEpochMilli()) / 1000.0);
            sanctioned.add(est.mode().equals("RECOVERING") || est.mode().equals("NEW_TRIP")
                    || (prevLeader != null && !prevLeader.equals(leader)));
            prevLeader = leader;
            for (StopAware.StopEvent e : core.drainEvents()) {
                events.merge(e.type().name(), 1L, Long::sum);
            }
            String m = est.mode();
            if (m.equals("RECOVERING") && !prevMode.equals("RECOVERING")) recoveringSpells++;
            if (m.equals("OFF_ROUTE")) offTicks++;
            if (m.equals("GPS_LOST") || m.equals("NO_GPS")) lostTicks++;
            prevMode = m;
            if (!Double.isNaN(core.lastInnovation()) && core.lastInnovationVariance() > 0) {
                double nu = core.lastInnovation();
                double s = core.lastInnovationVariance();
                dumpNisTick(ep.routeNumber(), ep.vehicleId().substring(0, 8), fx.timestamp(),
                        nu * nu / s, core.lastUpdateAccepted(), est.mode());
            }
            if (core.lastUpdateAccepted() && !Double.isNaN(core.lastInnovation())) {
                double nu = core.lastInnovation();
                absInnovations.add(Math.abs(nu));
                double s = core.lastInnovationVariance();
                if (s > 0) {
                    nisSum += nu * nu / s;
                    nisN++;
                }
            }
            double tNow = (fx.timestamp().toEpochMilli() - t0.toEpochMilli()) / 1000.0;
            for (StopAware.Eta eta : core.etas()) {
                Double fact = factArrivalSec.get(eta.stopId());
                if (fact == null || !eta.reliable() || fact <= tNow) continue;
                double err = Math.abs(tNow + eta.etaSec() - fact);
                if (eta.etaSec() <= 60) e60.add(err);
                else if (eta.etaSec() <= 120) e120.add(err);
                else if (eta.etaSec() <= 300) e300.add(err);
            }
        }
        int processed = ep.fixes().size() - dropped;
        var flight = biz.ugur.busroutebackend.replay.metrics.MarkerFlightMetric.compute(
                broadcastGeo, broadcastT, sanctioned, cfg.vMaxMs(), 1.5);
        return new EpisodeStats(
                ep.vehicleId(), ep.routeNumber(), ep.fixes().size(), dropped,
                ep.durationSec(), ep.nullAccuracyShare(),
                events, recoveringSpells, core.bank().switchCount(), core.offRouteTransitions(),
                share(offTicks, processed), share(lostTicks, processed),
                percentile(absInnovations, 0.50), percentile(absInnovations, 0.95),
                nisN > 0 ? nisSum / nisN : Double.NaN, nisN,
                bucket(e60), bucket(e120), bucket(e300),
                core.tripId(),
                flight.maxRatio(), flight.violations(), flight.sanctionedJumps());
    }

    private static Map<String, Double> detectArrivals(Episode ep, GeometryFixture g) {
        Map<String, Double> out = new TreeMap<>();
        if (g.stops().isEmpty()) return out;
        ArrivalDetector det = new ArrivalDetector(new ArrivalDetector.Config(50.0, 5.0));
        List<ArrivalDetector.RawPoint> raw = ep.fixes().stream()
                .map(f -> new ArrivalDetector.RawPoint(f.timestamp(), f.latitude(), f.longitude(), f.speedKmh()))
                .toList();
        Instant t0 = ep.fixes().get(0).timestamp();
        for (GeometryFixture.StopPoint sp : g.stops()) {
            double[] pt = g.pointAtS(sp.sMeters());
            det.detectArrival(raw, pt[0], pt[1]).ifPresent(at ->
                    out.put(sp.stopId(), (at.toEpochMilli() - t0.toEpochMilli()) / 1000.0));
        }
        return out;
    }

    private static double share(long part, int total) {
        return total > 0 ? (double) part / total : 0;
    }

    private static EtaBucket bucket(List<Double> errs) {
        return new EtaBucket(percentile(errs, 0.95), errs.size());
    }

    private static double percentile(List<Double> xs, double q) {
        if (xs.isEmpty()) return Double.NaN;
        List<Double> s = xs.stream().sorted().toList();
        return s.get((int) Math.floor(q * (s.size() - 1)));
    }
}
