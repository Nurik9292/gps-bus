package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.synth.SyntheticGeometries;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalTopologyScenariosTest {

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

    private record TickState(PredictionModel.Estimate est, int direction, long tripId, int lap,
                             List<StopAware.Eta> etas) {}

    private record TopoRun(List<TickState> ticks, List<StopAware.StopEvent> events, MotionFilterCore core) {}

    private TopoRun run(List<GpsFix> fixes, RouteTopology topo) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<TickState> ticks = new ArrayList<>();
        List<StopAware.StopEvent> events = new ArrayList<>();
        for (GpsFix fx : fixes) {
            PredictionModel.Estimate est = core.onFix(fx, topo);
            ticks.add(new TickState(est, core.direction(), core.tripId(), core.lapCount(), core.etas()));
            events.addAll(core.drainEvents());
        }
        return new TopoRun(ticks, events, core);
    }

    private static List<String> compressedModesFrom(List<TickState> ticks, String fromMode) {
        List<String> out = new ArrayList<>();
        boolean started = false;
        for (TickState ts : ticks) {
            String m = ts.est().mode();
            if (!started && m.equals(fromMode)) started = true;
            if (started && (out.isEmpty() || !out.get(out.size() - 1).equals(m))) out.add(m);
        }
        return out;
    }

    private static void assertNoForbiddenTransitions(List<TickState> ticks) {
        for (int i = 1; i < ticks.size(); i++) {
            String prev = ticks.get(i - 1).est().mode();
            String cur = ticks.get(i).est().mode();
            assertThat(prev.equals("DWELL") && cur.equals("AT_TERMINAL"))
                    .as("запрещённый переход DWELL→AT_TERMINAL напрямую (tick %d)", i)
                    .isFalse();
            boolean entersNewTrip = cur.equals("NEW_TRIP") && !prev.equals("NEW_TRIP");
            assertThat(entersNewTrip && !prev.equals("TURNING"))
                    .as("запрещённый переход %s→NEW_TRIP без TURNING (tick %d)", prev, i)
                    .isFalse();
        }
    }

    @Test
    void sc04TurnFlipChainRoute10() {
        RouteTopology topo = RouteTopology.thereAndBack(G10_0, G10_1);
        for (long seed = 60; seed < 65; seed++) {
            double turnDwell = 120 + (seed - 60) * 195;
            SyntheticScenario.TurnTrack track = SyntheticScenario.terminalTurnRun(
                    G10_0, G10_1, SyntheticScenario.Params.defaults(seed, "10", 0),
                    G10_0.totalMeters() - 2000, CRUISE, 1.0, turnDwell, 2100, 20, 0.3);
            TopoRun r = run(track.fixes(), topo);

            long flips = 0;
            for (int i = 1; i < r.ticks().size(); i++) {
                if (r.ticks().get(i).direction() != r.ticks().get(i - 1).direction()) flips++;
            }
            assertThat(flips).as("ровно 1 флип направления (seed=%d)", seed).isEqualTo(1);
            assertThat(r.core().tripId()).as("trip_id++ ровно один раз (seed=%d)", seed).isEqualTo(2);

            List<String> chain = compressedModesFrom(r.ticks(), "AT_TERMINAL");
            assertThat(chain.size()).isGreaterThanOrEqualTo(4);
            assertThat(chain.subList(0, 4))
                    .as("строгая цепочка mode (seed=%d)", seed)
                    .containsExactly("AT_TERMINAL", "TURNING", "NEW_TRIP", "TRACKING");
            assertNoForbiddenTransitions(r.ticks());

            int newTripIdx = -1;
            for (int i = 0; i < r.ticks().size(); i++) {
                if (r.ticks().get(i).est().mode().equals("NEW_TRIP")) {
                    newTripIdx = i;
                    break;
                }
            }
            double[] pHeld = G10_0.pointAtS(r.ticks().get(newTripIdx - 1).est().s());
            double[] pNew = G10_1.pointAtS(r.ticks().get(newTripIdx).est().s());
            double dp = GeometryFixture.haversineMeters(pHeld[0], pHeld[1], pNew[0], pNew[1]);
            double terminalGap = terminalGapMeters(G10_0, G10_1);
            double tNewTrip = (track.fixes().get(newTripIdx).timestamp().toEpochMilli()
                    - track.fixes().get(0).timestamp().toEpochMilli()) / 1000.0;
            System.out.printf(
                    "SC04 route10 seed=%d turnDwell=%.0fs: |dp| chain=%.1fm (terminal_gap=%.1fm + допуск 50); "
                            + "t(NEW_TRIP)=%.0fs vs t_flip=%.0fs (lag=%.0fs)%n",
                    seed, turnDwell, dp, terminalGap, tNewTrip, track.tFlipSec(), tNewTrip - track.tFlipSec());
            assertThat(dp).as("непрерывность метки: |Δp| ≤ terminal_gap + 50 м (seed=%d)", seed)
                    .isLessThanOrEqualTo(terminalGap + 50.0);
            assertThat(dp).as("санити-кап 150 м (seed=%d)", seed).isLessThanOrEqualTo(150.0);

            var flipAt = track.fixes().get(newTripIdx).timestamp();
            long returnDwells = r.events().stream()
                    .filter(e -> e.type() == StopAware.StopEventType.DWELL_ENTER)
                    .filter(e -> !e.at().isBefore(flipAt)).count();
            assertThat(returnDwells).as("стоп-слой жив на обратном рейсе").isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void sc04TurnFlipChainRoute8UnequalLegs() {
        RouteTopology topo = RouteTopology.thereAndBack(G8_0, G8_1);
        SyntheticScenario.TurnTrack track = SyntheticScenario.terminalTurnRun(
                G8_0, G8_1, SyntheticScenario.Params.defaults(66, "8", 0),
                G8_0.totalMeters() - 2000, CRUISE, 1.0, 300, 2600, 20, 0.3);
        TopoRun r = run(track.fixes(), topo);

        assertThat(r.core().tripId()).isEqualTo(2);
        List<String> chain = compressedModesFrom(r.ticks(), "AT_TERMINAL");
        assertThat(chain.subList(0, 4))
                .containsExactly("AT_TERMINAL", "TURNING", "NEW_TRIP", "TRACKING");

        int newTripIdx = -1;
        for (int i = 0; i < r.ticks().size(); i++) {
            if (r.ticks().get(i).est().mode().equals("NEW_TRIP")) {
                newTripIdx = i;
                break;
            }
        }
        double[] pHeld = G8_0.pointAtS(r.ticks().get(newTripIdx - 1).est().s());
        double[] pNew = G8_1.pointAtS(r.ticks().get(newTripIdx).est().s());
        double dp = GeometryFixture.haversineMeters(pHeld[0], pHeld[1], pNew[0], pNew[1]);
        double terminalGap = terminalGapMeters(G8_0, G8_1);
        System.out.printf("SC04 route8: |dp| chain=%.1fm; terminal_gap=%.1fm (вклад данных) + допуск 50 м%n",
                dp, terminalGap);
        assertThat(dp).as("|Δp| ≤ terminal_gap + допуск модели")
                .isLessThanOrEqualTo(terminalGap + 50.0);
        assertThat(dp).as("санити-кап 150 м").isLessThanOrEqualTo(150.0);
    }

    private static double terminalGapMeters(GeometryFixture gOut, GeometryFixture gBack) {
        double[] endOut = gOut.pointAtS(gOut.totalMeters());
        double[] startBack = gBack.pointAtS(0);
        return GeometryFixture.haversineMeters(endOut[0], endOut[1], startBack[0], startBack[1]);
    }

    @Test
    void turningGpsLossFallsToGpsLostWithoutFalseNewTrip() {
        RouteTopology topo = RouteTopology.thereAndBack(G10_0, G10_1);
        SyntheticScenario.TurnTrack track = SyntheticScenario.terminalTurnRun(
                G10_0, G10_1, SyntheticScenario.Params.defaults(96, "10", 0),
                G10_0.totalMeters() - 2000, CRUISE, 1.0, 300, 2100, 20, 0.3);

        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        double gapUntil = -1;
        boolean gapDone = false;
        int ticksAfterGap = 0;
        long tripIdAfterGapWindow = -1;
        List<String> modesAfterGap = new ArrayList<>();
        for (int i = 0; i < track.fixes().size(); i++) {
            GpsFix fx = track.fixes().get(i);
            double t = (fx.timestamp().toEpochMilli()
                    - track.fixes().get(0).timestamp().toEpochMilli()) / 1000.0;
            if (gapUntil >= 0 && t < gapUntil) continue;
            PredictionModel.Estimate est = core.onFix(fx, topo);
            if (!gapDone && est.mode().equals("TURNING")) {
                gapUntil = t + 40;
                gapDone = true;
                continue;
            }
            if (gapDone && gapUntil >= 0 && t >= gapUntil && ticksAfterGap < 3) {
                modesAfterGap.add(est.mode());
                ticksAfterGap++;
                if (ticksAfterGap == 3) tripIdAfterGapWindow = core.tripId();
            }
        }
        System.out.printf("A6.6: gap 40с посреди TURNING; режимы после возврата сигнала=%s; "
                + "trip_id после окна=%d (финально=%d)%n", modesAfterGap, tripIdAfterGapWindow, core.tripId());
        assertThat(gapDone).as("gap врезан именно посреди TURNING").isTrue();
        assertThat(modesAfterGap)
                .as("после потери GPS посреди разворота NEW_TRIP не срабатывает по до-gap подтверждениям")
                .doesNotContain("NEW_TRIP", "TURNING");
        assertThat(tripIdAfterGapWindow)
                .as("ложного trip_id++ в окне после gap нет").isEqualTo(1);
    }

    @Test
    void sc04NoFalseTurnOnNoisyStandstill() {
        RouteTopology topo = RouteTopology.thereAndBack(G10_0, G10_1);
        for (long seed = 70; seed < 75; seed++) {
            SyntheticScenario.TurnTrack track = SyntheticScenario.terminalStandstillRun(
                    G10_0, SyntheticScenario.Params.defaults(seed, "10", 0),
                    G10_0.totalMeters() - 1500, CRUISE, 1.0, 20, 660);
            TopoRun r = run(track.fixes(), topo);
            long turningTicks = r.ticks().stream()
                    .filter(ts -> ts.est().mode().equals("TURNING") || ts.est().mode().equals("NEW_TRIP"))
                    .count();
            assertThat(turningTicks)
                    .as("0 ложных TURNING/NEW_TRIP на шумной стоянке ≥10 мин (seed=%d)", seed)
                    .isZero();
            assertThat(r.core().tripId()).isEqualTo(1);
            assertThat(r.core().direction()).isEqualTo(0);
        }
    }

    @Test
    void sc03LoopThreeLapsWrapContinuity() {
        GeometryFixture gLoop = SyntheticGeometries.circleLoop("SYNLOOP", 15000, 600,
                List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9));
        double closure = GeometryFixture.haversineMeters(
                gLoop.pointAtS(0)[0], gLoop.pointAtS(0)[1],
                gLoop.pointAtS(gLoop.totalMeters())[0], gLoop.pointAtS(gLoop.totalMeters())[1]);
        assertThat(closure).as("порог склейки: Γ(0)≡Γ(L)").isLessThanOrEqualTo(1.0);

        SyntheticScenario.MultiStopTrack track = SyntheticScenario.loopRun(
                gLoop, SyntheticScenario.Params.defaults(80, "SYNLOOP", 0),
                300, 3, CRUISE, 1.0, 20, 0.2);
        TopoRun r = run(track.fixes(), RouteTopology.of(gLoop));

        assertThat(r.core().lapCount()).as("3 непрерывных оборота → 3 wrap").isEqualTo(3);
        long dirChanges = 0;
        double maxNormalStep = 0;
        double maxWrapStep = 0;
        long recovering = 0;
        for (int i = 1; i < r.ticks().size(); i++) {
            TickState prev = r.ticks().get(i - 1);
            TickState cur = r.ticks().get(i);
            if (cur.direction() != prev.direction()) dirChanges++;
            if (cur.est().mode().equals("RECOVERING")) recovering++;
            double[] a = gLoop.pointAtS(prev.est().s());
            double[] b = gLoop.pointAtS(cur.est().s());
            double step = GeometryFixture.haversineMeters(a[0], a[1], b[0], b[1]);
            if (cur.lap() != prev.lap()) maxWrapStep = Math.max(maxWrapStep, step);
            else maxNormalStep = Math.max(maxNormalStep, step);
        }
        System.out.printf("SC03 loop: wrap-step max=%.1fm vs normal-step max=%.1fm; laps=%d%n",
                maxWrapStep, maxNormalStep, r.core().lapCount());
        assertThat(dirChanges).as("0 флипов на кольце").isZero();
        assertThat(recovering).as("0 ре-привязок на кольце").isZero();
        assertThat(maxWrapStep)
                .as("|Δp| на wrap не выбивается из обычного шага (склейка непрерывна)")
                .isLessThanOrEqualTo(maxNormalStep * 1.5 + 10.0);
        assertNoForbiddenTransitions(r.ticks());
    }

    @Test
    void sc03LoopStopJustAfterSpliceGetsFullEventCycle() {
        GeometryFixture gLoop = SyntheticGeometries.circleLoop("SYNLOOP-B", 15000, 600,
                List.of(50.0 / 15000, 0.15, 0.3, 0.45, 0.6, 0.75, 0.9));
        String spliceStop = gLoop.stops().get(0).stopId();
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.loopRun(
                gLoop, SyntheticScenario.Params.defaults(81, "SYNLOOP-B", 0),
                300, 3, CRUISE, 1.0, 20, 0.2);
        TopoRun r = run(track.fixes(), RouteTopology.of(gLoop));

        List<StopAware.StopEventType> spliceSeq = r.events().stream()
                .filter(e -> e.stopId().equals(spliceStop))
                .map(StopAware.StopEvent::type).toList();
        long spliceVisitsTruth = track.visits().stream()
                .filter(v -> v.stopId().equals(spliceStop)).count();
        long dwellEnters = spliceSeq.stream().filter(t -> t == StopAware.StopEventType.DWELL_ENTER).count();
        System.out.printf("SC03-B splice-stop: истинных визитов=%d, событий=%s%n", spliceVisitsTruth, spliceSeq);
        assertThat(spliceVisitsTruth).isGreaterThanOrEqualTo(2);
        assertThat(dwellEnters)
                .as("стоп сразу после склейки (x≈0+) получает DWELL на каждом истинном визите")
                .isEqualTo(spliceVisitsTruth);
        assertThat(spliceSeq).containsSubsequence(
                StopAware.StopEventType.DECEL_ENTER, StopAware.StopEventType.DWELL_ENTER,
                StopAware.StopEventType.DWELL_EXIT,
                StopAware.StopEventType.DECEL_ENTER, StopAware.StopEventType.DWELL_ENTER,
                StopAware.StopEventType.DWELL_EXIT);
        var start = track.fixes().get(0).timestamp();
        List<StopAware.StopEvent> skipEvents = r.events().stream()
                .filter(e -> e.type() == StopAware.StopEventType.SKIP).toList();
        for (StopAware.StopEvent skip : skipEvents) {
            double tSkip = (skip.at().toEpochMilli() - start.toEpochMilli()) / 1000.0;
            boolean duringTrueVisit = track.visits().stream()
                    .filter(v -> v.stopId().equals(skip.stopId()))
                    .anyMatch(v -> tSkip >= v.tArrivalSec() - 30 && tSkip <= v.tDepartSec() + 30);
            assertThat(duringTrueVisit)
                    .as("ложный SKIP через склейку: %s в t=%.0f попал на истинный визит", skip.stopId(), tSkip)
                    .isFalse();
        }
        System.out.printf("SC03-B: skip-события вне истинных визитов (финальный проезд без цели): %d%n",
                skipEvents.size());

        boolean etaAcrossWrapSeen = false;
        for (TickState ts : r.ticks()) {
            double s = ts.est().s();
            if (s > gLoop.totalMeters() - 400 && s < gLoop.totalMeters() - 50
                    && !ts.etas().isEmpty()
                    && ts.etas().get(0).stopId().equals(spliceStop)
                    && ts.etas().get(0).etaSec() > 0 && ts.etas().get(0).etaSec() < 120) {
                etaAcrossWrapSeen = true;
                break;
            }
        }
        assertThat(etaAcrossWrapSeen).as("ETA работает через wrap (стоп за склейкой виден до склейки)").isTrue();
    }

    @Test
    void terminalOwnedStopYieldsSingleTerminalEventNoDuplicateArrival() {
        GeometryFixture gLine = SyntheticGeometries.straightLine("SYNLINE", 0, 6000, 25,
                List.of(1000.0, 2500.0, 4000.0, 5990.0));
        String terminalStop = "line-d0-stop-4";
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(
                gLine, SyntheticScenario.Params.defaults(82, "SYNLINE", 0),
                100, 6000, CRUISE, 1.0, 20, 0.3, java.util.Set.of(), false);
        TopoRun r = run(track.fixes(), RouteTopology.of(gLine));

        List<StopAware.StopEvent> terminalStopEvents = r.events().stream()
                .filter(e -> e.stopId().equals(terminalStop)).toList();
        assertThat(terminalStopEvents)
                .as("зона последней остановки пересекается с ε_term → прибытие отдаёт терминальная ветка, "
                        + "дубля ARRIVAL+TERMINAL нет")
                .isEmpty();
        long terminalEvents = r.events().stream()
                .filter(e -> e.type() == StopAware.StopEventType.AT_TERMINAL).count();
        assertThat(terminalEvents).as("ровно одно AT_TERMINAL").isEqualTo(1);
        assertNoForbiddenTransitions(r.ticks());

        long dwellsBefore = r.events().stream()
                .filter(e -> e.type() == StopAware.StopEventType.DWELL_ENTER).count();
        assertThat(dwellsBefore).as("обычные стопы до терминала обслужены слоем").isGreaterThanOrEqualTo(2);
    }
}
