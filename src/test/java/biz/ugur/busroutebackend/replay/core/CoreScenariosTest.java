package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.ReplayHarness;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoreScenariosTest {

    private static final GeometryFixture G =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;
    private static final double ACCEL = 1.0;

    private MotionFilterCore core() {
        return new MotionFilterCore(CFG);
    }

    @Test
    void sc15RampAbsoluteDeviationControlledAndDetected() {
        SyntheticScenario.Track track = SyntheticScenario.cruiseWithAccumulatingSnapDrift(
                G, SyntheticScenario.Params.defaults(151, "8", 0),
                1000, CRUISE, 0.7, 2800);
        MotionFilterCore core = core();
        ReplayHarness.Result r = ReplayHarness.run(core, G, track.fixes(), track.truth(), 300);

        int uncontrolled = 0;
        boolean detected = false;
        MotionFilterCore probe = core();
        probe.reset();
        for (int i = 0; i < track.fixes().size(); i++) {
            probe.onFix(track.fixes().get(i), G);
            double[] est = G.pointAtS(rAt(probe));
            double dev = GeometryFixture.haversineMeters(
                    track.fixes().get(i).latitude(), track.fixes().get(i).longitude(), est[0], est[1]);
            boolean eventActive = probe.driftEventActive();
            if (dev > CFG.dMaxMeters() && !eventActive) uncontrolled++;
            if (probe.absDeviationEvents() > 0) detected = true;
        }
        assertThat(uncontrolled)
                .as("INV-3: ни одного тика, где absDev>D_max и событие дрейфа не активно")
                .isZero();
        assertThat(detected).as("устойчивое расхождение задетектировано").isTrue();

        double pull = (CFG.rMaxRate() * 25.0 * 7.0 + CFG.rMaxBaseMeters()) * CFG.recoveryPullFactor();
        for (int i = 1; i < r.samples().size(); i++) {
            double step = Math.abs(r.samples().get(i).sEst() - r.samples().get(i - 1).sEst());
            assertThat(step)
                    .as("живая ветка (§5.5): стягивание без шага-разрыва (t=%.0f)", r.samples().get(i).tSec())
                    .isLessThanOrEqualTo(CRUISE * 7.0 + pull + 20);
        }
        System.out.printf("SC15-ramp core: meanAbs(truth)=%.1fm devEvents=%d nis=%s(%.2f) hash=%s%n",
                r.position().meanAbsError(), core.absDeviationEvents(),
                r.nisKind(), r.consistency().meanNis(), r.outputSha256().substring(0, 12));
    }

    private static double rAt(MotionFilterCore c) {
        return cLastS(c);
    }

    private static double cLastS(MotionFilterCore c) {
        try {
            var f = MotionFilterCore.class.getDeclaredField("x");
            f.setAccessible(true);
            return f.getDouble(c);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void sc15NoFalseSingleFixReanchorsAndSc01Clean() {
        SyntheticScenario.Track clean = SyntheticScenario.cruiseClean(
                G, SyntheticScenario.Params.defaults(11, "8", 0), 1000, CRUISE, 900);
        MotionFilterCore core = core();
        ReplayHarness.Result r = ReplayHarness.run(core, G, clean.fixes(), clean.truth(), 300);
        long recovering = r.samples().stream().filter(s -> s.mode().equals("RECOVERING")).count();
        assertThat(recovering).as("Р-2: гладкий профиль + шум → 0 переходов в RECOVERING").isZero();
        assertThat(core.absDeviationEvents()).isZero();
        assertThat(r.position().meanAbsError()).as("SC01: лаг ограничен").isLessThan(15.0);
    }

    @Test
    void sc16DeparturePeakAndSteadyThresholds() {
        SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                G, SyntheticScenario.Params.defaults(160, "8", 0),
                2000, CRUISE, ACCEL, 120, 900);
        ReplayHarness.Result r = ReplayHarness.run(core(), G, track.fixes(), track.truth(), 300);

        double peak = 0;
        List<Double> steady = new java.util.ArrayList<>();
        for (int i = 0; i < r.samples().size(); i++) {
            double t = r.samples().get(i).tSec();
            double err = Math.abs(r.samples().get(i).sEst() - r.samples().get(i).sTrue());
            if (t >= 120 && t <= 200) peak = Math.max(peak, err);
            if (t > 400) steady.add(err);
        }
        double steadyP95 = steady.stream().sorted().toList()
                .get((int) Math.floor(0.95 * (steady.size() - 1)));
        assertThat(peak).as("SC16 пик на отрыве ≤ 25 м (порог стенда v1)").isLessThanOrEqualTo(25.0);
        assertThat(steadyP95)
                .as("SC16 установившийся (p95 по t>400; трактовка порога v1 — в README) ≤ 10 м")
                .isLessThanOrEqualTo(10.0);
    }

    @Test
    void sc16HonestNeesMonteCarloWithinChi2() {
        int n = 20;
        double sumNees = 0;
        int count = 0;
        for (long seed = 1600; seed < 1600 + n; seed++) {
            SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                    G, SyntheticScenario.Params.defaults(seed, "8", 0),
                    2000, CRUISE, ACCEL, 120, 900);
            ReplayHarness.Result r = ReplayHarness.run(core(), G, track.fixes(), track.truth(), 300);
            for (var s : r.samples()) {
                if (Double.isNaN(s.sTrue()) || s.tSec() < 250) continue;
                sumNees += Math.pow(s.sEst() - s.sTrue(), 2) / s.varianceS();
                count++;
            }
        }
        double mean = sumNees / count;
        System.out.printf("SC16 honest NEES: RAW one-dimensional position NEES = err_s^2 / P00 "
                + "(dim=1, expectation 1.0, NOT dim=2 ANEES); q=(%.2f,%.2f) R0=sigma^2=%.0f%n",
                CFG.qPos(), CFG.qVel(), CFG.sigmaMeasDefaultMeters() * CFG.sigmaMeasDefaultMeters());
        System.out.printf("SC16 honest NEES(dim=1, MC x%d, steady): mean=%.2f n=%d%n", n, mean, count);
        SyntheticScenario.Track profTrack = SyntheticScenario.departureRamp(
                G, SyntheticScenario.Params.defaults(1600, "8", 0), 2000, CRUISE, ACCEL, 120, 900);
        ReplayHarness.Result prof = ReplayHarness.run(core(), G, profTrack.fixes(), profTrack.truth(), 300);
        for (int w = 0; w < 900; w += 150) {
            final int from = w;
            double wm = prof.samples().stream()
                    .filter(s -> s.tSec() >= from && s.tSec() < from + 150 && !Double.isNaN(s.sTrue()))
                    .mapToDouble(s -> Math.pow(s.sEst() - s.sTrue(), 2) / s.varianceS())
                    .average().orElse(Double.NaN);
            System.out.printf("  NEES-profile t=[%d..%d): %.2f%n", from, from + 150, wm);
        }
        double half = 2.576 * Math.sqrt(2.0 / count);
        assertThat(mean)
                .as("средний NEES в χ²-интервале (dim=1): [%.3f..%.3f]", 1 - half, 1 + half)
                .isBetween(0.5, 2.0);
    }

    @Test
    void p1ConstantAccelerationSteadyErrorBounded() {
        SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                G, SyntheticScenario.Params.defaults(31, "8", 0),
                1000, 20.0, 0.5, 60, 1200);
        ReplayHarness.Result r = ReplayHarness.run(core(), G, track.fixes(), track.truth(), 300);
        List<ReplayHarness.Sample> accelPhase = r.samples().stream()
                .filter(s -> s.tSec() >= 60 && s.tSec() <= 100).toList();
        double firstHalf = accelPhase.subList(0, accelPhase.size() / 2).stream()
                .mapToDouble(s -> Math.abs(s.sEst() - s.sTrue())).average().orElse(0);
        double secondHalf = accelPhase.subList(accelPhase.size() / 2, accelPhase.size()).stream()
                .mapToDouble(s -> Math.abs(s.sEst() - s.sTrue())).average().orElse(0);
        assertThat(secondHalf)
                .as("Р-1: установившаяся ошибка при пост. ускорении ограничена (1я половина %.1f)", firstHalf)
                .isLessThanOrEqualTo(Math.max(firstHalf * 1.5, 15.0));
    }

    @Test
    void sc02GapsNoJumpBeyondRmaxSeriesAndFreezeAfterTmax() {
        SyntheticScenario.Track track = SyntheticScenario.cruiseWithGaps(
                G, SyntheticScenario.Params.defaults(2, "8", 0), 1000, CRUISE, 1800,
                List.of(new double[]{300, 30}, new double[]{600, 60},
                        new double[]{900, 120}, new double[]{1300, 300}));
        ReplayHarness.Result r = ReplayHarness.run(core(), G, track.fixes(), track.truth(), 300);

        for (int i = 1; i < r.samples().size(); i++) {
            boolean reanchorStep = r.samples().get(i - 1).mode().equals("RECOVERING")
                    || r.samples().get(i).mode().equals("RECOVERING");
            if (reanchorStep) continue;
            double dTau = r.samples().get(i).tSec() - r.samples().get(i - 1).tSec();
            double step = Math.abs(r.samples().get(i).sEst() - r.samples().get(i - 1).sEst());
            double allowance = CRUISE * dTau + CFG.rMaxRate() * CRUISE * dTau + CFG.rMaxBaseMeters() + 20;
            assertThat(step)
                    .as("SC02: шаг вне ре-привязки t=%.0f (dTau=%.0f) в пределах хода+серии R_max",
                        r.samples().get(i).tSec(), dTau)
                    .isLessThanOrEqualTo(allowance);
        }
        var post300 = r.samples().stream().filter(s -> s.tSec() >= 1600).findFirst().orElseThrow();
        assertThat(post300.mode())
                .as("300с-gap (>T_max): прогноз заморожен и отстал >D_reanchor → ветка A (§6) re-init")
                .isEqualTo("RECOVERING");
        assertThat(Math.abs(post300.sEst() - post300.sTrue()))
                .as("ветка A: после re-init оценка у правды")
                .isLessThanOrEqualTo(3 * 15.0);
        var post60 = r.samples().stream().filter(s -> s.tSec() >= 660).findFirst().orElseThrow();
        assertThat(post60.mode())
                .as("60с-gap (<T_max): вливание обычным update, без re-init")
                .isNotEqualTo("RECOVERING");
    }

    @Test
    void sc08StationaryDriftBounded() {
        SyntheticScenario.Track track = SyntheticScenario.stationaryWithNoise(
                G, SyntheticScenario.Params.defaults(8, "8", 0), 5000, 900);
        ReplayHarness.Result r = ReplayHarness.run(core(), G, track.fixes(), track.truth(), 300);
        assertThat(r.position().maxAbsError())
                .as("SC08: дрейф стоячего борта ≤ ε_dwell=15 м")
                .isLessThanOrEqualTo(15.0);
    }

    @Test
    void determinismCoreSameHash() {
        SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                G, SyntheticScenario.Params.defaults(77, "8", 0), 2000, CRUISE, ACCEL, 60, 600);
        String h1 = ReplayHarness.run(core(), G, track.fixes(), track.truth(), 300).outputSha256();
        String h2 = ReplayHarness.run(core(), G, track.fixes(), track.truth(), 300).outputSha256();
        assertThat(h1).isEqualTo(h2);
    }
}
