package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.metrics.MarkerFlightMetric;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalSignal61ScenariosTest {

    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;
    private static final double[] GOKJE_CENTER = {37.981209, 58.232796};
    private static final double R_TERM_61_RATIFIED = 1050.0;
    private static final double T_TERM = 150.0;
    private static final double B_TERM = 0.5;

    private static final GeometryFixture FULL_0 = Variant61FixturesTest.FULL_0;
    private static final GeometryFixture FULL_1 = Variant61FixturesTest.FULL_1;

    private static RouteTopology banked61() {
        return RouteTopology.thereAndBack(FULL_0, FULL_1)
                .withVariants(List.of(Variant61FixturesTest.gokje0().shortVariant(),
                        Variant61FixturesTest.gokjeTail1().shortVariant()));
    }

    private record SyntTrack(List<GpsFix> fixes, double tZoneEnterSec, double tExitStartSec,
                             double tPhysTurnSec) {}

    private static SyntTrack approachStandExit(long seed, double[] standSecPhases,
                                               double moveSecBetween, boolean ringSweep) {
        var p = SyntheticScenario.Params.defaults(seed, "61", 0);
        Random rnd = new Random(seed);
        GeometryFixture prefix = Variant61FixturesTest.gokje0().shortVariant();
        GeometryFixture tail = Variant61FixturesTest.gokjeTail1().shortVariant();
        List<GpsFix> fixes = new ArrayList<>();
        double t = 0;
        double tZoneEnter = -1;
        for (double s = 7000; s < prefix.totalMeters() - 40; s += CRUISE * 7) {
            fixes.add(SyntheticScenario.emitFix(prefix, p, rnd, t, s, CRUISE));
            double[] pt = prefix.pointAtS(s);
            if (tZoneEnter < 0 && GeometryFixture.haversineMeters(pt[0], pt[1],
                    GOKJE_CENTER[0], GOKJE_CENTER[1]) <= R_TERM_61_RATIFIED) {
                tZoneEnter = t;
            }
            t += 7;
        }
        double mLon = 111320.0 * Math.cos(Math.toRadians(GOKJE_CENTER[0]));
        for (double standSec : standSecPhases) {
            for (double e = 0; e < standSec; e += 7) {
                fixes.add(SyntheticScenario.rawFix(p, rnd, t,
                        GOKJE_CENTER[0], GOKJE_CENTER[1], 0.5, 90));
                t += 7;
            }
            if (ringSweep) {
                for (double e = 0; e < moveSecBetween; e += 7) {
                    double ang = 2 * Math.PI * (e / Math.max(moveSecBetween, 1.0));
                    double offN = 120 * Math.sin(ang);
                    double offE = 120 * Math.cos(ang);
                    fixes.add(SyntheticScenario.rawFix(p, rnd, t,
                            GOKJE_CENTER[0] + offN / 111320.0, GOKJE_CENTER[1] + offE / mLon,
                            3 + rnd.nextDouble() * 9, (Math.toDegrees(ang) + 90) % 360));
                    t += 7;
                }
            }
        }
        double tPhysTurn = t;
        double tExit = t;
        for (double s = 30; s < 4200; s += CRUISE * 7) {
            fixes.add(SyntheticScenario.emitFix(tail, p, rnd, t, s, CRUISE));
            t += 7;
        }
        return new SyntTrack(fixes, tZoneEnter, tExit, tPhysTurn);
    }

    private record Run(List<PredictionModel.Estimate> ests, List<String> leaders,
                       List<double[]> geo, MotionFilterCore core, List<Double> tSec) {}

    private Run run(List<GpsFix> fixes, RouteTopology topo) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        core.bank().configureTerminalSignal(T_TERM, B_TERM);
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

    private static long newTripSpells(Run r) {
        long n = 0;
        String prev = "";
        for (var est : r.ests()) {
            if (est.mode().equals("NEW_TRIP") && !prev.equals("NEW_TRIP")) n++;
            prev = est.mode();
        }
        return n;
    }

    private static MarkerFlightMetric.FlightStats flight(Run r) {
        List<Boolean> sanct = new ArrayList<>();
        String prevLeader = null;
        for (int i = 0; i < r.ests().size(); i++) {
            sanct.add(r.ests().get(i).mode().equals("RECOVERING")
                    || r.ests().get(i).mode().equals("NEW_TRIP")
                    || (prevLeader != null && !prevLeader.equals(r.leaders().get(i))));
            prevLeader = r.leaders().get(i);
        }
        return MarkerFlightMetric.compute(r.geo(), r.tSec(), sanct, CFG.vMaxMs(), 1.5);
    }

    private static long vImplOver100Ticks(Run r) {
        long n = 0;
        String prevLeader = null;
        for (int i = 1; i < r.geo().size(); i++) {
            boolean sanctioned = r.ests().get(i).mode().equals("RECOVERING")
                    || r.ests().get(i).mode().equals("NEW_TRIP")
                    || (prevLeader != null && !prevLeader.equals(r.leaders().get(i)));
            prevLeader = r.leaders().get(i);
            if (sanctioned) continue;
            double dt = Math.max(r.tSec().get(i) - r.tSec().get(i - 1), 1.0);
            double v = GeometryFixture.haversineMeters(r.geo().get(i - 1)[0], r.geo().get(i - 1)[1],
                    r.geo().get(i)[0], r.geo().get(i)[1]) / dt * 3.6;
            if (v > 100) n++;
        }
        return n;
    }

    @Test
    void syntD61IdleAtGokjeThenExitBackward() {
        SyntTrack track = approachStandExit(6100, new double[]{170, 120, 170}, 40, false);
        Run r = run(track.fixes(), banked61());

        int enterIdx = 0;
        while (enterIdx < r.tSec().size() && r.tSec().get(enterIdx) < track.tZoneEnterSec()) enterIdx++;
        int exitIdx = 0;
        while (exitIdx < r.tSec().size() && r.tSec().get(exitIdx) < track.tExitStartSec()) exitIdx++;

        int firstPrefixInZone = -1;
        long holdBreaks = 0;
        for (int i = enterIdx; i < exitIdx; i++) {
            if (r.leaders().get(i).equals("61-gokje#d0")) {
                if (firstPrefixInZone < 0) firstPrefixInZone = i;
            } else if (firstPrefixInZone >= 0) {
                holdBreaks++;
            }
        }
        assertThat(firstPrefixInZone).as("gokje-префикс стал лидером в зоне").isPositive();
        assertThat(holdBreaks).as("SYNT-D61: разрывов удержания 0").isZero();
        assertThat(newTripSpells(r)).as("NEW_TRIP ровно 1 (выход назад)").isEqualTo(1);
        assertThat(r.leaders().get(r.leaders().size() - 1))
                .as("финальный лидер — 61-gokje-tail#d1").isEqualTo("61-gokje-tail#d1");
        assertThat(flight(r).violations()).as("полёт 0").isZero();
        assertThat(r.core().bank().bTermActiveTicks())
                .as("SYNT-D61 валидирует канал №28(б): отстой ≥ T_term → аддитив применялся")
                .isPositive();
        System.out.printf("SYNT-D61: hold-breaks=0, NEW_TRIP=1, полёт=0, bTerm-тиков=%d%n",
                r.core().bank().bTermActiveTicks());
    }

    @Test
    void syntF61TransitFullPastGokjeNoFalseFlip() {
        var track = SyntheticScenario.multiStopRun(FULL_0,
                SyntheticScenario.Params.defaults(6101, "61", 0),
                7000, 13000, CRUISE, 1.0, 20, 0.3, Set.of(), false);
        Run r = run(track.fixes(), banked61());

        long flipsToPrefix = 0;
        int flipIdx = -1;
        for (int i = 1; i < r.leaders().size(); i++) {
            if (r.leaders().get(i).equals("61-gokje#d0") && !r.leaders().get(i - 1).equals("61-gokje#d0")) {
                flipsToPrefix++;
                flipIdx = i;
            }
        }
        if (flipsToPrefix > 0) {
            int back = -1;
            for (int i = flipIdx + 1; i < r.leaders().size(); i++) {
                if (!r.leaders().get(i).equals("61-gokje#d0")) {
                    back = i;
                    break;
                }
            }
            assertThat(back - flipIdx)
                    .as("транзитный перехват гаснет ≤ H_sw+M тиков")
                    .isLessThanOrEqualTo(CFG.hSwitch() + CFG.mReanchor());
        }
        assertThat(newTripSpells(r)).as("SYNT-F61: NEW_TRIP 0").isZero();
        assertThat(r.core().bank().bTermActiveTicks()).as("SYNT-F61: bTerm-тиков 0").isZero();
        System.out.printf("SYNT-F61: транзит, flips=%d (гаснущие), NEW_TRIP=0, bTerm=0%n", flipsToPrefix);
    }

    @Test
    void syntP61ShortStop90sNoAdditive() {
        SyntTrack track = approachStandExit(6102, new double[]{90}, 0, false);
        Run r = run(track.fixes(), banked61());
        assertThat(r.core().bank().bTermActiveTicks())
                .as("SYNT-P61: стоянка 90 с < T_term=150 — аддитив не применялся").isZero();
        assertThat(newTripSpells(r)).as("NEW_TRIP=1 — только легитимный выход назад").isEqualTo(1);
        assertThat(flight(r).violations()).as("полёт 0").isZero();
        System.out.printf("SYNT-P61: bTerm=0, NEW_TRIP=1, полёт=0%n");
    }

    @Test
    void syntR61RollingReverseRealisticProfile() {
        SyntTrack track = approachStandExit(6103, new double[]{40, 0, 35, 20}, 45, true);
        Run r = run(track.fixes(), banked61());

        int physIdx = 0;
        while (physIdx < r.tSec().size() && r.tSec().get(physIdx) < track.tPhysTurnSec()) physIdx++;
        String leaderAtTurn = r.leaders().get(Math.min(physIdx, r.leaders().size() - 1));

        assertThat(newTripSpells(r)).as("SYNT-R61: NEW_TRIP ровно 1").isEqualTo(1);
        assertThat(r.leaders().get(r.leaders().size() - 1))
                .as("NEW_TRIP на 61-gokje-tail#d1").isEqualTo("61-gokje-tail#d1");
        assertThat(flight(r).violations()).as("полёт 0").isZero();
        assertThat(vImplOver100Ticks(r)).as("тиков v_impl>100 вне санкционированных переходов = 0").isZero();
        assertThat(r.core().bank().bTermActiveTicks())
                .as("bTerm-тиков 0 (стоянки < T_term — аддитив легитимно не применялся)").isZero();
        assertThat(leaderAtTurn)
                .as("лидер на момент физразворота ∈ gokje-семейству")
                .startsWith("61-gokje");
        System.out.printf("SYNT-R61: NEW_TRIP=1→tail, полёт=0, v_impl>100=0, bTerm=0, "
                + "лидер на развороте=%s%n", leaderAtTurn);
    }
}
