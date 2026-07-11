package biz.ugur.busroutebackend.replay.a10;

import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.replay.InputValidator;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.StopAware;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import biz.ugur.busroutebackend.replay.variants.RingCutout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class A10Support {

    public static final double FLIGHT_K = 1.5;

    private A10Support() {}

    public static Map<String, RouteTopology> geometryMap() {
        Map<String, RouteTopology> map = new TreeMap<>();
        for (String route : List.of("8", "10", "25", "61", "62", "63", "27", "48", "74", "80", "12", "97")) {
            map.put(route, RouteTopology.thereAndBack(
                    GeometryFixture.loadClasspath("/fixtures/geometry/route-" + route + "-dir0.json"),
                    GeometryFixture.loadClasspath("/fixtures/geometry/route-" + route + "-dir1.json")));
        }
        return map;
    }

    public static RouteTopology withShortVariants(String route, RingCutout.Bbox ringBbox) {
        GeometryFixture full0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-" + route + "-dir0.json");
        GeometryFixture full1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-" + route + "-dir1.json");
        GeometryFixture short0 = RingCutout.trunkOutsideRingZone(full0, ringBbox, route + "-short").shortVariant();
        GeometryFixture short1 = RingCutout.trunkOutsideRingZone(full1, ringBbox, route + "-short").shortVariant();
        return RouteTopology.thereAndBack(full0, full1).withVariants(List.of(short0, short1));
    }

    public record Tick(
            int idx,
            double tSec,
            long epochMs,
            double lat,
            double lon,
            double s,
            String leader,
            String mode,
            double stepRatio,
            boolean sanctioned,
            double nis,
            boolean nisValid,
            Double hdop,
            double dtSec,
            List<String> events) {}

    public record NisRow(String route, String vehicleId, String mode, Double hdop, double dtSec, double nis) {}

    public record RunOutcome(
            Episode episode,
            double flightMaxRatio,
            long flightViolations,
            long sanctionedJumps,
            long leaderSwitches,
            long recoveringSpells,
            int processedFixes,
            List<Tick> ticks,
            List<NisRow> nisRows) {}

    public static RunOutcome run(Episode ep, RouteTopology topo, CoreConfig cfg, boolean keepTicks) {
        MotionFilterCore core = new MotionFilterCore(cfg);
        core.reset();
        InputValidator validator = InputValidator.spec9Defaults();

        List<Tick> ticks = keepTicks ? new ArrayList<>() : List.of();
        List<NisRow> nisRows = new ArrayList<>();
        List<double[]> broadcastGeo = new ArrayList<>();
        List<Double> broadcastT = new ArrayList<>();
        List<Boolean> sanctioned = new ArrayList<>();
        String prevLeader = null;
        String prevMode = "";
        long recoveringSpells = 0;
        int processed = 0;
        long t0 = ep.fixes().get(0).timestamp().toEpochMilli();
        long prevFixMs = Long.MIN_VALUE;

        for (GpsFix fx : ep.fixes()) {
            if (!validator.validate(fx).accepted()) {
                continue;
            }
            processed++;
            double dtSec = prevFixMs == Long.MIN_VALUE
                    ? 0.0
                    : (fx.timestamp().toEpochMilli() - prevFixMs) / 1000.0;
            prevFixMs = fx.timestamp().toEpochMilli();

            PredictionModel.Estimate est = core.onFix(fx, topo);
            String leader = core.bank().leader().variantId();
            double[] p = core.bank().leader().geom().pointAtS(est.s());
            double tSec = (fx.timestamp().toEpochMilli() - t0) / 1000.0;
            boolean sanc = est.mode().equals("RECOVERING") || est.mode().equals("NEW_TRIP")
                    || (prevLeader != null && !prevLeader.equals(leader));
            broadcastGeo.add(p);
            broadcastT.add(tSec);
            sanctioned.add(sanc);
            prevLeader = leader;

            List<String> events = new ArrayList<>();
            for (StopAware.StopEvent e : core.drainEvents()) {
                events.add(e.type().name());
            }
            if (est.mode().equals("RECOVERING") && !prevMode.equals("RECOVERING")) recoveringSpells++;
            prevMode = est.mode();

            double nis = Double.NaN;
            boolean nisValid = false;
            if (core.lastUpdateAccepted() && !Double.isNaN(core.lastInnovation())) {
                double s = core.lastInnovationVariance();
                if (s > 0) {
                    nis = core.lastInnovation() * core.lastInnovation() / s;
                    nisValid = true;
                    nisRows.add(new NisRow(ep.routeNumber(), ep.vehicleId(), est.mode(), fx.hdop(), dtSec, nis));
                }
            }

            double stepRatio = 0;
            int n = broadcastGeo.size();
            if (n >= 2) {
                double dt = Math.max(broadcastT.get(n - 1) - broadcastT.get(n - 2), 1.0);
                double dist = GeometryFixture.haversineMeters(
                        broadcastGeo.get(n - 2)[0], broadcastGeo.get(n - 2)[1],
                        broadcastGeo.get(n - 1)[0], broadcastGeo.get(n - 1)[1]);
                stepRatio = dist / (dt * cfg.vMaxMs());
            }
            if (keepTicks) {
                ticks.add(new Tick(processed - 1, tSec, fx.timestamp().toEpochMilli(),
                        fx.latitude(), fx.longitude(), est.s(), leader, est.mode(),
                        stepRatio, sanc, nis, nisValid, fx.hdop(), dtSec, events));
            }
        }

        var flight = biz.ugur.busroutebackend.replay.metrics.MarkerFlightMetric.compute(
                broadcastGeo, broadcastT, sanctioned, cfg.vMaxMs(), FLIGHT_K);
        return new RunOutcome(ep, flight.maxRatio(), flight.violations(), flight.sanctionedJumps(),
                core.bank().switchCount(), recoveringSpells, processed,
                ticks, nisRows);
    }

    public static CoreConfig withBankKnobs(CoreConfig c, double lambda, double cRej, int hSw) {
        return new CoreConfig(
                c.dtSec(), c.w0Meters(), c.kWindowPerSpeed(), c.sigmaMeasDefaultMeters(),
                c.accuracyRefMeters(), c.dSnapMeters(), c.dMaxMeters(), c.gammaGate(),
                c.qPos(), c.qVel(), c.pInitPos(), c.pInitVel(), c.rMaxRate(), c.rMaxBaseMeters(),
                c.weakZvWeight(), c.nPersist(), c.mReanchor(), c.tLostSec(), c.tMaxSec(),
                c.vTargetMs(), c.aDepMs2(), c.aMaxMs2(), c.vMaxMs(), c.dReanchorMeters(),
                c.recoveryPullFactor(), c.vStopKmh(), c.vMoveKmh(), c.hStop(), c.hDep(), c.hDec(),
                c.dwellMinSec(), c.dDecelMeters(), c.epsArrMeters(), c.epsStopMeters(),
                c.epsTermMeters(), c.dwellExpectedSec(), c.dwellMaxSec(), c.nTurnConfirm(),
                c.dTurnConfirmMeters(), c.unpinWindowTicks(), c.kTurnRevert(), c.wTurnWindowMeters(), c.historyNMin(),
                c.kOffRoute(), c.mOffRouteExit(),
                lambda, cRej, c.scoreProgressBonus(),
                c.sSwitch(), hSw, c.dSwitchSmoothMeters(), c.maxHypotheses(),
                c.rHdopEnabled(), c.rHdopAMeters(), c.rHdopBMetersPerHdop(),
                c.gateNisThreshold(), c.qScale(),
                c.epsCloseTailMeters(), c.nTurnConfirmTerm(), c.dTurnConfirmTermMeters(),
                c.kTermMissOffRoute(), c.nDepMoveConfirm());
    }
}
