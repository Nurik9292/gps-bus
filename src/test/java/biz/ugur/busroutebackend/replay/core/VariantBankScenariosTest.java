package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.StopAware;

import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.replay.synth.SyntheticGeometries;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VariantBankScenariosTest {

    private static final GeometryFixture G10_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-10-dir0.json");
    private static final GeometryFixture G10_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-10-dir1.json");
    private static final GeometryFixture G8_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");
    private static final GeometryFixture G8_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;

    private record Tick(PredictionModel.Estimate est, String leaderId, int direction,
                        List<StopAware.Eta> etas, List<StopAware.StopEvent> events) {}

    private record Run(List<Tick> ticks, MotionFilterCore core) {}

    private Run run(List<GpsFix> fixes, RouteTopology topo) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<Tick> ticks = new ArrayList<>();
        for (GpsFix fx : fixes) {
            PredictionModel.Estimate est = core.onFix(fx, topo);
            ticks.add(new Tick(est, core.bank().leader().variantId(), core.direction(),
                    core.etas(), core.drainEvents()));
        }
        return new Run(ticks, core);
    }

    @Test
    void sc07SelfIntersectionPassesWithoutBranchJump() {
        GeometryFixture eight = SyntheticGeometries.figureEight("EIGHT");
        SyntheticScenario.Track track = SyntheticScenario.cruiseClean(
                eight, SyntheticScenario.Params.defaults(100, "EIGHT", 0),
                100, CRUISE, (eight.totalMeters() - 300) / CRUISE);
        Run r = run(track.fixes(), RouteTopology.of(eight));

        assertThat(r.core().bank().switchCount()).as("SC07: лидер не сменился").isZero();
        double maxErr = 0;
        double maxStep = 0;
        for (int i = 0; i < r.ticks().size(); i++) {
            maxErr = Math.max(maxErr, Math.abs(r.ticks().get(i).est().s() - track.truth().get(i)[1]));
            if (i > 0) {
                maxStep = Math.max(maxStep,
                        Math.abs(r.ticks().get(i).est().s() - r.ticks().get(i - 1).est().s()));
            }
            assertThat(r.ticks().get(i).est().mode())
                    .as("SC07: проход самопересечения без RECOVERING/NEW_TRIP (tick %d)", i)
                    .isNotIn("RECOVERING", "NEW_TRIP", "TURNING", "OFF_ROUTE");
        }
        System.out.printf("SC07 восьмёрка: maxErr=%.1fм, maxStep=%.1fм (чужая ветка на дуге ~3200м — прыжка нет)%n",
                maxErr, maxStep);
        assertThat(maxErr).as("0 снапов на чужую ветку (ошибка << расстояния веток по дуге)")
                .isLessThanOrEqualTo(60.0);
        assertThat(maxStep).as("скачок метки отсутствует").isLessThanOrEqualTo(150.0);
    }

    private record ForkResult(Run run, int divergeIdx, int switchIdx, double switchStepMeters,
                              String switchMode, double forkGapMeters,
                              SyntheticScenario.MultiStopTrack track) {}

    private static double switchAllowanceMeters() {
        return (CFG.rMaxRate() * CRUISE * 7 + CFG.rMaxBaseMeters()) * (CFG.hSwitch() + 3);
    }

    private ForkResult runFork(SyntheticGeometries.ForkPair fork, long seed) {
        RouteTopology topo = RouteTopology.of(fork.full()).withVariants(List.of(fork.shortVariant()));
        GeometryFixture gTrue = fork.shortVariant();
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(
                gTrue, SyntheticScenario.Params.defaults(seed, gTrue.routeNumber(), 0),
                200, gTrue.totalMeters() - 100, CRUISE, 1.0, 20, 0.3, Set.of(), false);
        Run r = run(track.fixes(), topo);

        int divergeIdx = -1;
        for (int i = 0; i < track.fixes().size(); i++) {
            GpsFix f = track.fixes().get(i);
            var p = fork.full().projectOntoRange(f.latitude(), f.longitude(),
                    0, fork.full().totalMeters(), 0);
            if (p.distMeters() > CFG.dSnapMeters()) {
                divergeIdx = i;
                break;
            }
        }
        int switchIdx = -1;
        for (int i = 0; i < r.ticks().size(); i++) {
            if (r.ticks().get(i).leaderId().endsWith("-short#d0")) {
                switchIdx = i;
                break;
            }
        }
        double step = -1;
        double forkGap = -1;
        String switchMode = "";
        if (switchIdx > 0) {
            GeometryFixture gOld = fork.full();
            GeometryFixture gNew = fork.shortVariant();
            double[] pPrev = gOld.pointAtS(r.ticks().get(switchIdx - 1).est().s());
            double[] pCur = gNew.pointAtS(r.ticks().get(switchIdx).est().s());
            step = GeometryFixture.haversineMeters(pPrev[0], pPrev[1], pCur[0], pCur[1]);
            forkGap = gOld.projectOntoRange(pCur[0], pCur[1], 0, gOld.totalMeters(), 0).distMeters();
            switchMode = r.ticks().get(switchIdx).est().mode();
        }
        return new ForkResult(r, divergeIdx, switchIdx, step, switchMode, forkGap, track);
    }

    @Test
    void sc14EarlyDisambiguationSwitchesFastWithSmoothedLabel() {
        SyntheticGeometries.ForkPair fork = SyntheticGeometries.forkPair(
                "FORKE", 3000, 5000, 2000, 0, 0);
        ForkResult fr = runFork(fork, 110);

        assertThat(fr.switchIdx()).as("смена лидера на short состоялась").isPositive();
        assertThat(fr.divergeIdx()).isPositive();
        int lag = fr.switchIdx() - fr.divergeIdx();
        System.out.printf("SC14-ранняя: расхождение tick=%d, смена tick=%d (лаг=%d фиксов), "
                        + "|dp|=%.1fм, fork_gap=%.1fм, mode=%s%n",
                fr.divergeIdx(), fr.switchIdx(), lag, fr.switchStepMeters(), fr.forkGapMeters(),
                fr.switchMode());
        assertThat(fr.switchStepMeters())
                .as("A8.3: гео-скачок ≤ fork_gap + допуск сглаживания (%.0fм)", switchAllowanceMeters())
                .isLessThanOrEqualTo(fr.forkGapMeters() + switchAllowanceMeters());
        assertThat(lag).as("смена ≤ 3 фиксов после физического расхождения").isLessThanOrEqualTo(3);
        assertThat(fr.switchMode())
                .as("ранняя дизамбигуация: дуговое расхождение в допуске сглаживания — смена тихая, "
                        + "без RECOVERING (гео-скачок %.0fм = переход на верную ветку, печатается)",
                        fr.switchStepMeters())
                .isNotEqualTo("RECOVERING");

        boolean etaFromNewLeader = false;
        for (int i = fr.switchIdx(); i < Math.min(fr.switchIdx() + 8, fr.run().ticks().size()); i++) {
            var etas = fr.run().ticks().get(i).etas();
            if (etas.stream().anyMatch(e -> e.stopId().equals("FORKE-short-stop"))
                    && etas.stream().noneMatch(e -> e.stopId().equals("FORKE-full-stop"))) {
                etaFromNewLeader = true;
                break;
            }
        }
        assertThat(etaFromNewLeader).as("ETA после смены — от геометрии нового лидера").isTrue();

        boolean dwellAtShortStop = fr.run().ticks().stream()
                .flatMap(t -> t.events().stream())
                .anyMatch(e -> e.type() == StopAware.StopEventType.DWELL_ENTER
                        && e.stopId().equals("FORKE-short-stop"));
        assertThat(dwellAtShortStop).as("события после смены — от геометрии нового лидера").isTrue();
    }

    @Test
    void sc14LateDisambiguationJumpMeasuredAsEventNotSilentDrift() {
        SyntheticGeometries.ForkPair fork = SyntheticGeometries.forkPair(
                "FORKL", 3000, 5000, 2000, 40, 500);
        ForkResult fr = runFork(fork, 111);

        assertThat(fr.switchIdx()).as("поздняя смена состоялась").isPositive();
        System.out.printf("SC14-поздняя (параллель 40м/500м): смена tick=%d, гео-скачок |dp|=%.1fм, "
                        + "fork_gap=%.1fм, mode=%s (атомарная смена с печатью ядра — не тихое сползание)%n",
                fr.switchIdx(), fr.switchStepMeters(), fr.forkGapMeters(), fr.switchMode());
        assertThat(fr.switchStepMeters())
                .as("A8.3: гео-скачок ≤ fork_gap + допуск сглаживания (%.0fм)", switchAllowanceMeters())
                .isLessThanOrEqualTo(fr.forkGapMeters() + switchAllowanceMeters());
        assertThat(fr.switchStepMeters())
                .as("величина скачка измерена (не NaN/не нулевой сдвиг между ветками)")
                .isGreaterThan(0.0);
        boolean etaFromNewLeader = false;
        for (int i = fr.switchIdx(); i < Math.min(fr.switchIdx() + 8, fr.run().ticks().size()); i++) {
            if (fr.run().ticks().get(i).etas().stream()
                    .anyMatch(e -> e.stopId().equals("FORKL-short-stop"))) {
                etaFromNewLeader = true;
                break;
            }
        }
        assertThat(etaFromNewLeader).as("после смены — геометрия нового лидера").isTrue();
    }

    @Test
    void missedFlipRecoveredByBankWithReanchorChain() {
        RouteTopology topo = RouteTopology.thereAndBack(G10_0, G10_1);
        SyntheticScenario.TurnTrack track = SyntheticScenario.terminalTurnRun(
                G10_0, G10_1, SyntheticScenario.Params.defaults(120, "10", 0),
                G10_0.totalMeters() - 2000, CRUISE, 1.0, 300, 2600, 20, 0.3);

        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        double gapUntil = -1;
        boolean gapDone = false;
        int gapEndIdx = -1;
        List<String> modes = new ArrayList<>();
        List<StopAware.StopEvent> events = new ArrayList<>();
        int processed = 0;
        int newTripTick = -1;
        for (int i = 0; i < track.fixes().size(); i++) {
            GpsFix fx = track.fixes().get(i);
            double t = (fx.timestamp().toEpochMilli()
                    - track.fixes().get(0).timestamp().toEpochMilli()) / 1000.0;
            if (gapUntil >= 0 && t < gapUntil) continue;
            PredictionModel.Estimate est = core.onFix(fx, topo);
            events.addAll(core.drainEvents());
            modes.add(est.mode());
            processed++;
            if (gapDone && gapEndIdx < 0) gapEndIdx = processed - 1;
            if (gapDone && newTripTick < 0 && est.mode().equals("NEW_TRIP")) {
                newTripTick = processed - 1;
            }
            if (!gapDone && est.mode().equals("TURNING")) {
                gapUntil = t + 90;
                gapDone = true;
            }
        }
        assertThat(gapDone).as("gap 90с врезан посреди TURNING").isTrue();
        System.out.printf("missedFlip: режимы после gap: %s%n",
                modes.subList(Math.max(0, gapEndIdx),
                        Math.min(gapEndIdx + 25, modes.size())));
        assertThat(newTripTick).as("банк восстановил флип после gap").isPositive();
        int mFlip = newTripTick - gapEndIdx;
        System.out.printf("missedFlip: банк перехватил за M_flip=%d фиксов после возврата сигнала; trip_id=%d%n",
                mFlip, core.tripId());
        assertThat(mFlip)
                .as("смена лидера на L_d' за M_flip фиксов после возврата сигнала "
                        + "(факт ~22: мягкий возврат + INV-3-персист + EMA + гистерезис; калибровка — A8)")
                .isLessThanOrEqualTo(25);
        assertThat(core.tripId()).as("trip_id++ ровно 1").isEqualTo(2);

        assertThat(modes.get(newTripTick - 1))
                .as("цепочка RECOVERING→NEW_TRIP (аддендум №22)").isEqualTo("RECOVERING");
        boolean trackingAfter = false;
        for (int i = newTripTick + 1; i < Math.min(newTripTick + 4, modes.size()); i++) {
            if (modes.get(i).equals("TRACKING")) trackingAfter = true;
        }
        assertThat(trackingAfter).as("…→TRACKING после NEW_TRIP").isTrue();

        Set<String> terminalOwnedIds = new java.util.HashSet<>();
        for (var sp : G10_1.stops()) {
            if (sp.sMeters() <= CFG.epsTermMeters()
                    || sp.sMeters() >= G10_1.totalMeters() - CFG.epsTermMeters()) {
                terminalOwnedIds.add(sp.stopId());
            }
        }
        long terminalSkips = events.stream()
                .filter(e -> e.type() == StopAware.StopEventType.SKIP)
                .filter(e -> terminalOwnedIds.contains(e.stopId())).count();
        assertThat(terminalSkips).as("ложных SKIP терминальных стопов нет (правило OQ5)").isZero();
    }

    @Test
    void cascadeDetourThenTerminalStandThenReturnFixedAsFact() {
        RouteTopology topo = RouteTopology.thereAndBack(G10_0, G10_1);
        SyntheticScenario.TurnTrack track = SyntheticScenario.detourThenTerminalStandThenReturn(
                G10_0, G10_1, SyntheticScenario.Params.defaults(140, "10", 0),
                G10_0.totalMeters() - 2500, CRUISE, 1.0,
                G10_0.totalMeters() - 900, 150, 180, 2400);

        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<String> modes = new ArrayList<>();
        List<StopAware.StopEvent> events = new ArrayList<>();
        int firstOff = -1;
        int newTripIdx = -1;
        for (int i = 0; i < track.fixes().size(); i++) {
            var est = core.onFix(track.fixes().get(i), topo);
            modes.add(est.mode());
            events.addAll(core.drainEvents());
            if (firstOff < 0 && est.mode().equals("OFF_ROUTE")) firstOff = i;
            if (newTripIdx < 0 && est.mode().equals("NEW_TRIP")) newTripIdx = i;
        }
        double tOff = firstOff >= 0 ? track.truth().get(firstOff)[0] : Double.NaN;
        double tNewTrip = newTripIdx >= 0 ? track.truth().get(newTripIdx)[0] : Double.NaN;
        long offTicks = modes.stream().filter(m -> m.equals("OFF_ROUTE")).count();
        System.out.printf("КАСКАД (A8.4, фиксация фактом): вход OFF t=%.0fс (объезд с t=%.0fс), "
                        + "OFF-тиков=%d, NEW_TRIP t=%.0fс (t_flip=%.0fс), trip_id=%d, dir=%d, "
                        + "переходов OFF=%d, смен банка=%d%n",
                tOff, track.truth().get(0)[0], offTicks, tNewTrip, track.tFlipSec(),
                core.tripId(), core.direction(), core.offRouteTransitions(), core.bank().switchCount());

        if (newTripIdx > 0) {
            assertThat(modes.get(newTripIdx - 1))
                    .as("восстановление санкционированным путём: TURNING (терминальная ветка) "
                            + "или RECOVERING (банк, №22) — факт: %s", modes.get(newTripIdx - 1))
                    .isIn("TURNING", "RECOVERING");
        }
        assertThat(core.tripId()).as("trip_id не растёт хаотично").isLessThanOrEqualTo(2);
        Set<String> terminalOwnedIds = new java.util.HashSet<>();
        for (var g : List.of(G10_0, G10_1)) {
            for (var sp : g.stops()) {
                if (sp.sMeters() <= CFG.epsTermMeters()
                        || sp.sMeters() >= g.totalMeters() - CFG.epsTermMeters()) {
                    terminalOwnedIds.add(sp.stopId());
                }
            }
        }
        long terminalSkips = events.stream()
                .filter(e -> e.type() == StopAware.StopEventType.SKIP)
                .filter(e -> terminalOwnedIds.contains(e.stopId())).count();
        assertThat(terminalSkips).as("ложных SKIP терминальных стопов нет").isZero();
    }

    @Test
    void bankOfTwoDoesNotChangeSingleRouteBehavior() {
        RouteTopology topo = RouteTopology.thereAndBack(G8_0, G8_1);
        List<Double> h60 = new ArrayList<>();
        List<Double> h120 = new ArrayList<>();
        List<Double> h300 = new ArrayList<>();
        long totalSwitches = 0;
        for (long seed = 440; seed < 445; seed++) {
            SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(
                    G8_0, SyntheticScenario.Params.defaults(seed, "8", 0),
                    2000, 9000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), false);
            Run r = run(track.fixes(), topo);
            totalSwitches += r.core().bank().switchCount();
            java.util.Map<String, Double> factArr = new java.util.HashMap<>();
            for (var v : track.visits()) factArr.put(v.stopId(), v.tArrivalSec());
            for (int i = 0; i < r.ticks().size(); i++) {
                double tNow = track.truth().get(i)[0];
                for (StopAware.Eta eta : r.ticks().get(i).etas()) {
                    Double fact = factArr.get(eta.stopId());
                    if (fact == null || !eta.reliable() || fact <= tNow) continue;
                    double err = Math.abs((tNow + eta.etaSec()) - fact);
                    if (eta.etaSec() <= 60) h60.add(err);
                    else if (eta.etaSec() <= 120) h120.add(err);
                    else if (eta.etaSec() <= 300) h300.add(err);
                }
            }
        }
        double p60 = p95(h60);
        double p120 = p95(h120);
        double p300 = p95(h300);
        System.out.printf("Стабильность банка (N_hyp=2, A4-профили): headline %.1f/%.1f/%.1f, смен лидера=%d%n",
                p60, p120, p300, totalSwitches);
        assertThat(totalSwitches).as("0 смен лидера на чистом одиночном маршруте").isZero();
        assertThat(p60).as("headline 60с не хуже A6-факта").isLessThanOrEqualTo(7.9);
        assertThat(p120).as("headline 120с не хуже A6-факта").isLessThanOrEqualTo(14.6);
        assertThat(p300).as("headline 300с не хуже A6-факта").isLessThanOrEqualTo(12.6);
    }

    @Test
    void bankOverheadMeasuredForTwoAndSixHypotheses() {
        SyntheticScenario.TurnTrack track = SyntheticScenario.terminalTurnRun(
                G10_0, G10_1, SyntheticScenario.Params.defaults(130, "10", 0),
                G10_0.totalMeters() - 2000, CRUISE, 1.0, 120, 2100, 20, 0.3);
        RouteTopology topo2 = RouteTopology.thereAndBack(G10_0, G10_1);
        SyntheticGeometries.ForkPair fe = SyntheticGeometries.forkPair("FORKO", 3000, 5000, 2000, 0, 0);
        RouteTopology topo6 = RouteTopology.thereAndBack(G10_0, G10_1)
                .withVariants(List.of(fe.full(), fe.shortVariant(),
                        SyntheticGeometries.figureEight("EIGHTO"), G8_0));

        double ns2 = timeRun(track.fixes(), topo2);
        double ns6 = timeRun(track.fixes(), topo6);
        System.out.printf("Оверхед тика: N_hyp=2 → %.1f мкс/фикс; N_hyp=6 → %.1f мкс/фикс (×%.1f)%n",
                ns2 / 1000, ns6 / 1000, ns6 / Math.max(1, ns2));
        assertThat(ns6).as("тик при N_hyp=6 остаётся дешёвым (<2 мс)").isLessThan(2_000_000);
    }

    private double timeRun(List<GpsFix> fixes, RouteTopology topo) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long start = System.nanoTime();
        for (GpsFix fx : fixes) {
            core.onFix(fx, topo);
        }
        return (System.nanoTime() - start) / (double) fixes.size();
    }

    private static double p95(List<Double> xs) {
        if (xs.isEmpty()) return Double.NaN;
        List<Double> s = xs.stream().sorted().toList();
        return s.get((int) Math.floor(0.95 * (s.size() - 1)));
    }
}
