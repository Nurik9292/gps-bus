package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.StopAware;

import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;
import biz.ugur.busroutebackend.replay.metrics.ArrivalDetector;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StopEtaScenariosTest {

    private static final GeometryFixture G =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;
    private static final Instant T0 = Instant.parse("2026-07-03T06:00:00Z");

    private record RunResult(MotionFilterCore core, List<StopAware.StopEvent> events,
                             Map<Double, List<StopAware.Eta>> etasByTime,
                             List<PredictionModel.Estimate> estimates,
                             SyntheticScenario.MultiStopTrack track) {}

    private RunResult run(SyntheticScenario.MultiStopTrack track) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<StopAware.StopEvent> events = new ArrayList<>();
        Map<Double, List<StopAware.Eta>> etas = new java.util.LinkedHashMap<>();
        List<PredictionModel.Estimate> ests = new ArrayList<>();
        for (int i = 0; i < track.fixes().size(); i++) {
            ests.add(core.onFix(track.fixes().get(i), G));
            events.addAll(core.drainEvents());
            etas.put(track.truth().get(i)[0], core.etas());
        }
        return new RunResult(core, events, etas, ests, track);
    }

    private SyntheticScenario.MultiStopTrack multiStop(long seed, Set<String> skip, boolean saw) {
        return SyntheticScenario.multiStopRun(G,
                SyntheticScenario.Params.defaults(seed, "8", 0),
                2000, 9000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, skip, saw);
    }

    @Test
    void multiStopSequencesAndNoFalseSkip() {
        SyntheticScenario.MultiStopTrack track = multiStop(41, Set.of(), false);
        RunResult r = run(track);

        long skips = r.events().stream().filter(e -> e.type() == StopAware.StopEventType.SKIP).count();
        assertThat(skips).as("0 ложных SKIP на честном мультистопе").isZero();

        for (SyntheticScenario.StopVisit visit : track.visits()) {
            List<StopAware.StopEventType> seq = r.events().stream()
                    .filter(e -> e.stopId().equals(visit.stopId()))
                    .map(StopAware.StopEvent::type).toList();
            assertThat(seq)
                    .as("остановка %s: DECEL→DWELL→DEPART", visit.stopId())
                    .containsSubsequence(StopAware.StopEventType.DECEL_ENTER,
                            StopAware.StopEventType.DWELL_ENTER,
                            StopAware.StopEventType.DWELL_EXIT);
        }
        assertThat(track.visits()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void skipProfileEmitsExactlyOneSkip() {
        String skipId = G.stops().stream()
                .filter(s -> s.sMeters() > 4000 && s.sMeters() < 7000)
                .findFirst().orElseThrow().stopId();
        SyntheticScenario.MultiStopTrack track = multiStop(42, Set.of(skipId), false);
        RunResult r = run(track);

        List<StopAware.StopEvent> skips = r.events().stream()
                .filter(e -> e.type() == StopAware.StopEventType.SKIP).toList();
        assertThat(skips).as("ровно 1 SKIP на skip-профиле").hasSize(1);
        assertThat(skips.get(0).stopId()).isEqualTo(skipId);
    }

    @Test
    void dwellPinsXAtStop() {
        SyntheticScenario.MultiStopTrack track = multiStop(43, Set.of(), false);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        double maxDrift = 0;
        for (GpsFix fx : track.fixes()) {
            PredictionModel.Estimate est = core.onFix(fx, G);
            if (est.mode().equals("DWELL")) {
                double nearest = G.stops().stream()
                        .mapToDouble(s -> Math.abs(s.sMeters() - est.s())).min().orElse(999);
                maxDrift = Math.max(maxDrift, nearest);
            }
        }
        assertThat(maxDrift).as("DWELL: x зафиксирован на x_stop, дрейф ≤ ε_dwell").isLessThanOrEqualTo(15.0);
    }

    @Test
    void arrivalDetectorAgreesWithGeneratorTruth() {
        SyntheticScenario.MultiStopTrack track = multiStop(44, Set.of(), false);
        ArrivalDetector det = new ArrivalDetector(new ArrivalDetector.Config(50.0, 5.0));
        List<ArrivalDetector.RawPoint> raw = track.fixes().stream()
                .map(f -> new ArrivalDetector.RawPoint(f.timestamp(), f.latitude(), f.longitude(), f.speedKmh()))
                .toList();
        int checked = 0;
        for (SyntheticScenario.StopVisit visit : track.visits()) {
            var sp = G.stops().stream().filter(s -> s.stopId().equals(visit.stopId())).findFirst().orElseThrow();
            double[] pt = G.pointAtS(sp.sMeters());
            var found = det.detectArrival(raw, pt[0], pt[1]);
            if (found.isEmpty()) continue;
            double tFact = (found.get().toEpochMilli() - T0.toEpochMilli()) / 1000.0;
            assertThat(Math.abs(tFact - visit.tArrivalSec()))
                    .as("П-2-детектор vs истина, стоп %s", visit.stopId())
                    .isLessThanOrEqualTo(10.0);
            checked++;
        }
        assertThat(checked).isGreaterThanOrEqualTo(3);
    }

    @Test
    void headlineEtaVsFactByHorizon() {
        List<Double> h60 = new ArrayList<>();
        List<Double> h120 = new ArrayList<>();
        List<Double> h300 = new ArrayList<>();
        for (long seed = 440; seed < 445; seed++) {
            SyntheticScenario.MultiStopTrack track = multiStop(seed, Set.of(), false);
            RunResult r = run(track);
            Map<String, Double> factArr = new java.util.HashMap<>();
            for (var v : track.visits()) factArr.put(v.stopId(), v.tArrivalSec());
            r.etasByTime().forEach((tNow, etas) -> {
                for (StopAware.Eta eta : etas) {
                    Double fact = factArr.get(eta.stopId());
                    if (fact == null || !eta.reliable() || fact <= tNow) continue;
                    double err = Math.abs((tNow + eta.etaSec()) - fact);
                    double horizon = eta.etaSec();
                    if (horizon <= 60) h60.add(err);
                    else if (horizon <= 120) h120.add(err);
                    else if (horizon <= 300) h300.add(err);
                }
            });
        }
        double p60 = p95(h60);
        double p120 = p95(h120);
        double p300 = p95(h300);
        System.out.printf("HEADLINE v1: p95|ETA-fact| h<=60s: %.1fs (n=%d) | h<=120s: %.1fs (n=%d) | h<=300s: %.1fs (n=%d)%n",
                p60, h60.size(), p120, h120.size(), p300, h300.size());
        assertThat(p60).as("headline 60с ≤ 15с").isLessThanOrEqualTo(15.0);
        assertThat(p120).as("headline 120с ≤ 25с").isLessThanOrEqualTo(25.0);
        assertThat(p300).as("headline 300с ≤ 60с").isLessThanOrEqualTo(60.0);
    }

    @Test
    void sc09SawNoModeChatter() {
        SyntheticScenario.MultiStopTrack track = multiStop(45, Set.of(), true);
        RunResult r = run(track);
        Map<String, Integer> decelEnters = new java.util.HashMap<>();
        for (StopAware.StopEvent e : r.events()) {
            if (e.type() == StopAware.StopEventType.DECEL_ENTER) {
                decelEnters.merge(e.stopId(), 1, Integer::sum);
            }
        }
        List<String> reentries = decelEnters.entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
        int changes = 0;
        for (int i = 1; i < r.estimates().size(); i++) {
            if (!r.estimates().get(i).mode().equals(r.estimates().get(i - 1).mode())) changes++;
        }
        double windows = Math.max(1, track.truth().get(track.truth().size() - 1)[0] / 600.0);
        System.out.printf("SC09-saw: mode-changes=%d (%.1f/10мин), re-entries=%d%n",
                changes, changes / windows, reentries.size());
        assertThat(reentries)
                .as("SC09: предельный цикл = повторный DECEL_ENTER к одному стопу; должен отсутствовать")
                .isEmpty();
    }

    @Test
    void inv11EtaFromSameXAndStalenessFlag() {
        SyntheticScenario.MultiStopTrack track = multiStop(46, Set.of(), false);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        for (int i = 0; i < track.fixes().size(); i++) {
            PredictionModel.Estimate est = core.onFix(track.fixes().get(i), G);
            List<StopAware.Eta> etas = core.etas();
            if (!etas.isEmpty() && !est.mode().equals("DWELL")) {
                var first = etas.get(0);
                var sp = G.stops().stream().filter(s -> s.stopId().equals(first.stopId())).findFirst().orElseThrow();
                double vNow = est.speedMs() >= CFG.vMoveKmh() / 3.6 ? est.speedMs() : 0.0;
                double recomputed = kinematicTime(Math.max(0, sp.sMeters() - est.s()),
                        vNow, CFG.vTargetMs(), CFG.aDepMs2());
                assertThat(first.etaSec())
                        .as("INV-11: ETA первой остановки считается из того же x̂ (t=%d)", i)
                        .isCloseTo(recomputed, org.assertj.core.data.Offset.offset(1.0));
            }
        }
        SyntheticScenario.Track gap = SyntheticScenario.cruiseWithGaps(
                G, SyntheticScenario.Params.defaults(47, "8", 0), 2000, CRUISE, 900,
                List.of(new double[]{300, 200}));
        MotionFilterCore c2 = new MotionFilterCore(CFG);
        c2.reset();
        boolean sawUnreliable = false;
        for (GpsFix fx : gap.fixes()) {
            PredictionModel.Estimate est = c2.onFix(fx, G);
            if (est.mode().equals("RECOVERING")) {
                sawUnreliable = c2.etas().stream().noneMatch(StopAware.Eta::reliable);
            }
        }
        assertThat(sawUnreliable).as("staleness: в RECOVERING ETA без флага reliable не выдаётся").isTrue();
    }

    private static double kinematicTime(double dist, double vNow, double vCruise, double a) {
        double brakeFromNow = vNow * vNow / (2 * a);
        if (dist <= brakeFromNow) {
            return vNow > 0.1 ? 2 * dist / vNow : Math.sqrt(2 * dist / a);
        }
        double vPeak = Math.min(vCruise, Math.sqrt(a * dist + vNow * vNow / 2));
        double tAccel = (vPeak - vNow) / a;
        double tBrake = vPeak / a;
        double dAccel = (vPeak * vPeak - vNow * vNow) / (2 * a);
        double dBrake = vPeak * vPeak / (2 * a);
        return tAccel + tBrake + Math.max(0, dist - dAccel - dBrake) / vPeak;
    }

    private static double p95(List<Double> xs) {
        if (xs.isEmpty()) return Double.NaN;
        List<Double> s = xs.stream().sorted().toList();
        return s.get((int) Math.floor(0.95 * (s.size() - 1)));
    }
}
