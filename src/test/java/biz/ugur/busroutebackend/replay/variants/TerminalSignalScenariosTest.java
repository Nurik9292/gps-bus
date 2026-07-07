package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.metrics.MarkerFlightMetric;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalSignalScenariosTest {

    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;
    private static final double[] PAD = {38.046798, 58.200888};
    private static final double R_TERM_BRINGUP = 750.0;
    private static final double T_TERM = 150.0;

    private static final GeometryFixture FULL_0 = Variant25FixturesTest.FULL_0;
    private static final GeometryFixture FULL_1 = Variant25FixturesTest.FULL_1;

    private static GeometryFixture prefixWithZone(double rTerm) {
        return Variant25FixturesTest.short0().shortVariant()
                .withTerminalZone(new GeometryFixture.TerminalZone(PAD[0], PAD[1], rTerm));
    }

    private static RouteTopology topoWithZone(double rTerm) {
        return RouteTopology.thereAndBack(FULL_0, FULL_1)
                .withVariants(List.of(prefixWithZone(rTerm),
                        Variant25FixturesTest.short1().shortVariant()));
    }

    private record SyntTrack(List<GpsFix> fixes, double tPadEnterSec, double tExitStartSec) {}

    private static SyntTrack syntD(long seed, double creepMinutes) {
        var p = SyntheticScenario.Params.defaults(seed, "25", 0);
        Random rnd = new Random(seed);
        GeometryFixture prefix = Variant25FixturesTest.short0().shortVariant();
        GeometryFixture tail = Variant25FixturesTest.short1().shortVariant();
        List<GpsFix> fixes = new ArrayList<>();
        double t = 0;
        double tPadEnter = -1;
        for (double s = 14200; s < prefix.totalMeters() - 40; s += CRUISE * 7) {
            fixes.add(SyntheticScenario.emitFix(prefix, p, rnd, t, s, CRUISE));
            double[] pt = prefix.pointAtS(s);
            if (tPadEnter < 0 && GeometryFixture.haversineMeters(pt[0], pt[1], PAD[0], PAD[1])
                    <= R_TERM_BRINGUP) {
                tPadEnter = t;
            }
            t += 7;
        }
        double creepEnd = t + creepMinutes * 60;
        boolean standing = true;
        double phase = 0;
        double offN = 0;
        double offE = 0;
        while (t < creepEnd) {
            phase += 7;
            if (standing && phase > 100) {
                standing = false;
                phase = 0;
            } else if (!standing && phase > 40) {
                standing = true;
                phase = 0;
            }
            double speed = standing ? 0.5 : 3 + rnd.nextDouble() * 7;
            if (!standing) {
                offN = Math.max(-40, Math.min(40, offN + rnd.nextGaussian() * 12));
                offE = Math.max(-40, Math.min(40, offE + rnd.nextGaussian() * 12));
            }
            double mLon = 111320.0 * Math.cos(Math.toRadians(PAD[0]));
            fixes.add(SyntheticScenario.rawFix(p, rnd, t,
                    PAD[0] + offN / 111320.0, PAD[1] + offE / mLon, speed, 90));
            t += 7;
        }
        double tExit = t;
        for (double s = 30; s < 4000; s += CRUISE * 7) {
            fixes.add(SyntheticScenario.emitFix(tail, p, rnd, t, s, CRUISE));
            t += 7;
        }
        return new SyntTrack(fixes, tPadEnter, tExit);
    }

    private record Run(List<PredictionModel.Estimate> ests, List<String> leaders,
                       List<double[]> geo, MotionFilterCore core, List<Double> tSec) {}

    private Run run(List<GpsFix> fixes, RouteTopology topo, double bTerm) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        core.bank().configureTerminalSignal(T_TERM, bTerm);
        List<PredictionModel.Estimate> ests = new ArrayList<>();
        List<String> leaders = new ArrayList<>();
        List<double[]> geo = new ArrayList<>();
        List<Double> tSec = new ArrayList<>();
        long t0 = fixes.get(0).timestamp().toEpochMilli();
        for (GpsFix fx : fixes) {
            ests.add(core.onFix(fx, topo));
            leaders.add(core.bank().leader().variantId());
            geo.add(core.bank().leader().geom().pointAtS(ests.get(ests.size() - 1).s()));
            tSec.add((fx.timestamp().toEpochMilli() - t0) / 1000.0);
        }
        return new Run(ests, leaders, geo, core, tSec);
    }

    @Test
    void syntDIdleWithCreepThenExitBackward() {
        SyntTrack track = syntD(1100, 8.5);
        Run r = run(track.fixes(), topoWithZone(R_TERM_BRINGUP), 1.0);

        int enterIdx = 0;
        while (enterIdx < r.tSec().size() && r.tSec().get(enterIdx) < track.tPadEnterSec()) enterIdx++;
        int exitIdx = 0;
        while (exitIdx < r.tSec().size() && r.tSec().get(exitIdx) < track.tExitStartSec()) exitIdx++;

        long nonPrefixInZone = 0;
        int firstPrefixInZone = -1;
        for (int i = enterIdx; i < exitIdx; i++) {
            if (r.leaders().get(i).equals("25-short#d0")) {
                if (firstPrefixInZone < 0) firstPrefixInZone = i;
            } else if (firstPrefixInZone >= 0) {
                nonPrefixInZone++;
            }
        }
        assertThat(firstPrefixInZone).as("prefix стал лидером в зоне").isPositive();
        assertThat(nonPrefixInZone)
                .as("SYNT-D: лидер в зоне = prefix непрерывно после входа")
                .isZero();

        long newTrips = 0;
        String prevMode = "";
        for (var est : r.ests()) {
            if (est.mode().equals("NEW_TRIP") && !prevMode.equals("NEW_TRIP")) newTrips++;
            prevMode = est.mode();
        }
        assertThat(newTrips).as("NEW_TRIP ровно 1 (выход назад на хвост)").isEqualTo(1);
        assertThat(r.leaders().get(r.leaders().size() - 1))
                .as("финальный лидер — tail").isEqualTo("25-short-tail#d1");

        List<Boolean> sanct = new ArrayList<>();
        String prevLeader = null;
        for (int i = 0; i < r.ests().size(); i++) {
            sanct.add(r.ests().get(i).mode().equals("RECOVERING")
                    || r.ests().get(i).mode().equals("NEW_TRIP")
                    || (prevLeader != null && !prevLeader.equals(r.leaders().get(i))));
            prevLeader = r.leaders().get(i);
        }
        var flight = MarkerFlightMetric.compute(r.geo(), r.tSec(), sanct, CFG.vMaxMs(), 1.5);
        assertThat(flight.violations()).as("полёт-метрика: 0 нарушений").isZero();

        double maxDrift = 0;
        double[] pinPt = prefixWithZone(R_TERM_BRINGUP).pointAtS(
                prefixWithZone(R_TERM_BRINGUP).totalMeters());
        for (int i = firstPrefixInZone; i < exitIdx; i++) {
            maxDrift = Math.max(maxDrift, GeometryFixture.haversineMeters(
                    r.geo().get(i)[0], r.geo().get(i)[1], pinPt[0], pinPt[1]));
        }
        System.out.printf("SYNT-D: prefix-лидер с тика %d, наруш. непрерывности=0, NEW_TRIP=1, "
                        + "финал=tail, полёт=0, дрейф вещания в зоне max=%.1fм%n",
                firstPrefixInZone, maxDrift);
        assertThat(maxDrift).as("дрейф вещания в зоне ≤ ε_dwell").isLessThanOrEqualTo(15.0);
    }

    @Test
    void syntFTransitFullThroughZoneNoFalseFlip() {
        var track = SyntheticScenario.multiStopRun(FULL_0,
                SyntheticScenario.Params.defaults(1101, "25", 0),
                13000, 18500, CRUISE, 1.0, 20, 0.3, Set.of(), false);
        Run r = run(track.fixes(), topoWithZone(R_TERM_BRINGUP), 1.0);

        long flipsToPrefix = 0;
        int flipIdx = -1;
        for (int i = 1; i < r.leaders().size(); i++) {
            if (r.leaders().get(i).equals("25-short#d0")
                    && !r.leaders().get(i - 1).equals("25-short#d0")) {
                flipsToPrefix++;
                flipIdx = i;
            }
        }
        long newTrips = r.ests().stream().filter(e -> e.mode().equals("NEW_TRIP")).count();
        System.out.printf("SYNT-F: флипов на prefix=%d; NEW_TRIP-тиков=%d; bTerm-тиков=%d%n",
                flipsToPrefix, newTrips, r.core().bank().bTermActiveTicks());
        if (flipsToPrefix > 0) {
            int back = -1;
            for (int i = flipIdx + 1; i < r.leaders().size(); i++) {
                if (!r.leaders().get(i).equals("25-short#d0")) {
                    back = i;
                    break;
                }
            }
            assertThat(back - flipIdx)
                    .as("возврат к full ≤ H_sw+M тиков после выхода")
                    .isLessThanOrEqualTo(CFG.hSwitch() + CFG.mReanchor());
        }
        assertThat(newTrips).as("NEW_TRIP = 0").isZero();
    }

    @Test
    void syntPPassengerStop90sNoTermEvidence() {
        String zoneStop = FULL_0.stops().stream()
                .filter(s -> s.sMeters() > 15900 && s.sMeters() < 16800)
                .findFirst().orElseThrow().stopId();
        var track = SyntheticScenario.multiStopRun(FULL_0,
                SyntheticScenario.Params.defaults(1102, "25", 0),
                13000, 18500, CRUISE, 1.0,
                stopId -> stopId.equals(zoneStop) ? 90.0 : 20.0, 0.3, Set.of(), false);
        Run r = run(track.fixes(), topoWithZone(R_TERM_BRINGUP), 1.0);

        long flips = 0;
        for (int i = 1; i < r.leaders().size(); i++) {
            if (r.leaders().get(i).equals("25-short#d0")
                    && !r.leaders().get(i - 1).equals("25-short#d0")) flips++;
        }
        System.out.printf("SYNT-P: стоп 90с<T_term=150: bTerm-тиков=%d, флипов на prefix=%d%n",
                r.core().bank().bTermActiveTicks(), flips);
        assertThat(r.core().bank().bTermActiveTicks())
                .as("cum<T_term → S_eff-аддитив не применялся").isZero();
        assertThat(flips).as("флипов на prefix = 0").isZero();
    }

    @Test
    void bTermBringUpMinimalHoldingValue() {
        for (double b : List.of(0.5, 1.0, 2.0)) {
            SyntTrack track = syntD(1103, 8.5);
            Run r = run(track.fixes(), topoWithZone(R_TERM_BRINGUP), b);
            int enterIdx = 0;
            while (enterIdx < r.tSec().size() && r.tSec().get(enterIdx) < track.tPadEnterSec()) enterIdx++;
            int exitIdx = 0;
            while (exitIdx < r.tSec().size() && r.tSec().get(exitIdx) < track.tExitStartSec()) exitIdx++;
            int firstPrefix = -1;
            long breaks = 0;
            for (int i = enterIdx; i < exitIdx; i++) {
                if (r.leaders().get(i).equals("25-short#d0")) {
                    if (firstPrefix < 0) firstPrefix = i;
                } else if (firstPrefix >= 0) {
                    breaks++;
                }
            }
            System.out.printf("B_term=%.1f: prefix-лидер с тика %s, разрывов удержания=%d, "
                            + "bTerm-тиков=%d%n",
                    b, firstPrefix < 0 ? "НЕ СТАЛ" : String.valueOf(firstPrefix), breaks,
                    r.core().bank().bTermActiveTicks());
        }
    }
}
