package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OffRouteScenariosTest {

    private static final GeometryFixture G =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;

    private record Tick(PredictionModel.Estimate est, List<StopAware.Eta> etas,
                        List<StopAware.StopEvent> events) {}

    private record Run(List<Tick> ticks, MotionFilterCore core) {}

    private Run run(List<GpsFix> fixes) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<Tick> ticks = new ArrayList<>();
        for (GpsFix fx : fixes) {
            PredictionModel.Estimate est = core.onFix(fx, G);
            ticks.add(new Tick(est, core.etas(), core.drainEvents()));
        }
        return new Run(ticks, core);
    }

    private static int firstIdx(Run r, String mode, int from) {
        for (int i = from; i < r.ticks().size(); i++) {
            if (r.ticks().get(i).est().mode().equals(mode)) return i;
        }
        return -1;
    }

    private int firstDetourFixIdx(SyntheticScenario.OffRouteTrack track) {
        for (int i = 0; i < track.truth().size(); i++) {
            if (track.truth().get(i)[0] >= track.tOffSec()) return i;
        }
        throw new IllegalStateException("нет фиксов объезда");
    }

    @Test
    void sc06EntryExactlyAfterKConsecutiveMissesAndFrozenInside() {
        SyntheticScenario.OffRouteTrack track = SyntheticScenario.offRouteRun(
                G, SyntheticScenario.Params.defaults(90, "8", 0),
                2000, CRUISE, 120, 60, 120, 150, 400, Set.of(), 0);
        Run r = run(track.fixes());

        int firstDetour = firstDetourFixIdx(track);
        int entry = firstIdx(r, "OFF_ROUTE", 0);
        assertThat(entry).as("вход в OFF_ROUTE состоялся").isPositive();
        assertThat(entry - firstDetour)
                .as("вход ровно после K=%d подряд отказов коридора", CFG.kOffRoute())
                .isEqualTo(CFG.kOffRoute() - 1);

        double frozenS = r.ticks().get(entry).est().s();
        int lastOff = entry;
        for (int i = entry; i < r.ticks().size() && r.ticks().get(i).est().mode().equals("OFF_ROUTE"); i++) {
            lastOff = i;
            assertThat(r.ticks().get(i).est().s())
                    .as("x̂ заморожен внутри OFF_ROUTE (tick %d)", i)
                    .isCloseTo(frozenS, org.assertj.core.data.Offset.offset(1e-6));
            assertThat(r.ticks().get(i).etas())
                    .as("ETA в OFF_ROUTE только с reliable=false (tick %d)", i)
                    .allMatch(e -> !e.reliable());
            assertThat(r.ticks().get(i).events())
                    .as("события стоп-слоя в OFF_ROUTE не эмитятся (tick %d)", i)
                    .isEmpty();
        }
        System.out.printf("SC06-B: вход tick=%d (K=%d), заморожен x̂=%.0f, OFF_ROUTE тиков=%d%n",
                entry, CFG.kOffRoute(), frozenS, lastOff - entry + 1);
    }

    @Test
    void sc06ReturnSmallGapBlendsIntoPreviousTravelModeWithoutReanchor() {
        SyntheticScenario.OffRouteTrack track = SyntheticScenario.offRouteRun(
                G, SyntheticScenario.Params.defaults(91, "8", 0),
                2000, CRUISE, 120, 60, 150, 150, 400, Set.of(), 0);
        Run r = run(track.fixes());

        int entry = firstIdx(r, "OFF_ROUTE", 0);
        int exit = entry;
        while (exit < r.ticks().size() && r.ticks().get(exit).est().mode().equals("OFF_ROUTE")) exit++;
        assertThat(exit).isLessThan(r.ticks().size());

        double frozenS = r.ticks().get(entry).est().s();
        double gap = Math.abs(track.sReturnMeters() - frozenS);
        String exitMode = r.ticks().get(exit).est().mode();
        System.out.printf("SC06-возврат-B: gap=%.0fм (D_reanchor=%.0f), выходной режим=%s%n",
                gap, CFG.dReanchorMeters(), exitMode);
        assertThat(gap).as("профиль подобран под ветку B").isLessThanOrEqualTo(CFG.dReanchorMeters());
        assertThat(exitMode).as("возврат в предыдущий ходовой режим без ре-привязки")
                .isEqualTo("TRACKING");
        assertThat(r.ticks().stream().map(t -> t.est().mode())
                .filter(m -> m.equals("RECOVERING")).count())
                .as("вливание ветки B — без RECOVERING").isZero();

        int settled = Math.min(exit + 5, r.ticks().size() - 1);
        double sTrue = track.truth().get(settled)[1];
        assertThat(Math.abs(r.ticks().get(settled).est().s() - sTrue))
                .as("после вливания метка сходится к правде (не чужая линия)")
                .isLessThanOrEqualTo(100.0);
    }

    @Test
    void sc06ReturnLargeGapReanchorsViaBranchA() {
        SyntheticScenario.OffRouteTrack track = SyntheticScenario.offRouteRun(
                G, SyntheticScenario.Params.defaults(92, "8", 0),
                2000, CRUISE, 120, 180, 150, 200, 1400, Set.of(), 0);
        Run r = run(track.fixes());

        int entry = firstIdx(r, "OFF_ROUTE", 0);
        int exit = entry;
        while (exit < r.ticks().size() && r.ticks().get(exit).est().mode().equals("OFF_ROUTE")) exit++;
        assertThat(exit).isLessThan(r.ticks().size());

        double frozenS = r.ticks().get(entry).est().s();
        double gap = Math.abs(track.sReturnMeters() - frozenS);
        System.out.printf("SC06-возврат-A: gap=%.0fм (>%.0f), выходной режим=%s, s(exit)=%.0f vs sReturn=%.0f%n",
                gap, CFG.dReanchorMeters(), r.ticks().get(exit).est().mode(),
                r.ticks().get(exit).est().s(), track.sReturnMeters());
        assertThat(gap).as("профиль подобран под ветку A").isGreaterThan(CFG.dReanchorMeters());
        assertThat(r.ticks().get(exit).est().mode())
                .as("gap > D_reanchor → ре-привязка веткой A (§6, через P_init)")
                .isEqualTo("RECOVERING");
        assertThat(Math.abs(r.ticks().get(exit).est().s() - track.sReturnMeters()))
                .as("ре-привязка на точку возврата, не на чужую линию")
                .isLessThanOrEqualTo(150.0);
    }

    @Test
    void sc06EntryDoesNotHappenOnKMinusOneMissesPlusValidFix() {
        int k = CFG.kOffRoute();
        SyntheticScenario.OffRouteTrack track = SyntheticScenario.offRouteRun(
                G, SyntheticScenario.Params.defaults(93, "8", 0),
                2000, CRUISE, 120, 90, 120, 150, 600, Set.of(k - 1), 0);
        Run r = run(track.fixes());

        int firstDetour = firstDetourFixIdx(track);
        int entry = firstIdx(r, "OFF_ROUTE", 0);
        assertThat(entry)
                .as("K−1 отказов + валидный фикс в коридоре → входа нет; вход только после свежей K-серии")
                .isGreaterThan(firstDetour + k);
        System.out.printf("SC06-K-1: touch на детур-фиксе %d отложил вход до tick=%d (детур с %d)%n",
                k - 1, entry, firstDetour);
    }

    @Test
    void sc06CorridorTouchNoFalseExitWithDebounce() {
        SyntheticScenario.OffRouteTrack track = SyntheticScenario.offRouteRun(
                G, SyntheticScenario.Params.defaults(94, "8", 0),
                2000, CRUISE, 120, 180, 200, 150, 900, Set.of(8, 15), 0);
        Run r = run(track.fixes());

        int entry = firstIdx(r, "OFF_ROUTE", 0);
        double frozenS = r.ticks().get(entry).est().s();
        long falseExits = 0;
        long offTicks = 0;
        for (int i = entry; i < r.ticks().size(); i++) {
            double t = track.truth().get(i)[0];
            if (t >= track.tReturnSec()) break;
            if (r.ticks().get(i).est().mode().equals("OFF_ROUTE")) {
                offTicks++;
                assertThat(r.ticks().get(i).est().s())
                        .as("x̂ побитово заморожен весь объезд, касание не размораживает (tick %d)", i)
                        .isCloseTo(frozenS, org.assertj.core.data.Offset.offset(1e-6));
            } else {
                falseExits++;
            }
        }
        System.out.printf("SC06-дебаунс (M=%d): касания=2, ложных выходов=%d, "
                        + "переходов OFF↔ходовой=%d, OFF-тиков=%d%n",
                CFG.mOffRouteExit(), falseExits, r.core().offRouteTransitions(), offTicks);
        assertThat(falseExits)
                .as("A7.1: одиночное касание коридора не выпускает из OFF_ROUTE").isZero();
        assertThat(r.core().offRouteTransitions())
                .as("переходов ≤ 2 (вход + финальный выход)").isLessThanOrEqualTo(2);

        int lastN = 5;
        for (int i = r.ticks().size() - lastN; i < r.ticks().size(); i++) {
            double sTrue = track.truth().get(i)[1];
            assertThat(Math.abs(r.ticks().get(i).est().s() - sTrue))
                    .as("после объезда с касаниями система восстанавливается (tick %d)", i)
                    .isLessThanOrEqualTo(150.0);
        }
    }

    @Test
    void a70ConfidenceDegradesMonotonicallyDuringRejectSeries() {
        SyntheticScenario.OffRouteTrack track = SyntheticScenario.offRouteRun(
                G, SyntheticScenario.Params.defaults(96, "8", 0),
                2000, CRUISE, 120, 60, 120, 150, 400, Set.of(), 0);
        Run r = run(track.fixes());

        int firstDetour = firstDetourFixIdx(track);
        int entry = firstIdx(r, "OFF_ROUTE", 0);
        double prevVar = -1;
        for (int i = firstDetour; i < r.ticks().size(); i++) {
            String m = r.ticks().get(i).est().mode();
            if (!m.equals("OFF_ROUTE") && i >= entry) break;
            assertThat(m)
                    .as("факт τ_valid: фиксы идут (пусть и отвергнутые) → GPS_LOST/NO_GPS не наступают (tick %d)", i)
                    .isNotIn("GPS_LOST", "NO_GPS");
            double varNow = r.ticks().get(i).est().varianceS();
            if (prevVar >= 0) {
                assertThat(varNow)
                        .as("A7.0: conf монотонно невозрастает (P₀₀ неубывает) в серии подряд отказов (tick %d)", i)
                        .isGreaterThanOrEqualTo(prevVar - 1e-9);
            }
            prevVar = varNow;
        }
        System.out.printf("A7.0: τ_valid сбрасывается любым фиксом (факт кода: lastFixTime безусловно); "
                + "деградация conf — P-ростом predict-без-update: P₀₀ %.0f → %.0f за серию отказов%n",
                r.ticks().get(firstDetour).est().varianceS(), prevVar);
    }

    @Test
    void sc06NegativeNoDwellDuringStopOnDetour() {
        SyntheticScenario.OffRouteTrack track = SyntheticScenario.offRouteRun(
                G, SyntheticScenario.Params.defaults(95, "8", 0),
                2000, CRUISE, 120, 300, 120, 150, 500, Set.of(), 120);
        Run r = run(track.fixes());

        int entry = firstIdx(r, "OFF_ROUTE", 0);
        assertThat(entry).isPositive();
        long dwellTicksInDetour = 0;
        long dwellEventsInDetour = 0;
        for (int i = 0; i < r.ticks().size(); i++) {
            double t = track.truth().get(i)[0];
            if (t >= track.tOffSec() && t < track.tReturnSec()) {
                if (r.ticks().get(i).est().mode().equals("DWELL")) dwellTicksInDetour++;
                dwellEventsInDetour += r.ticks().get(i).events().stream()
                        .filter(e -> e.type() == StopAware.StopEventType.DWELL_ENTER).count();
            }
        }
        assertThat(dwellTicksInDetour)
                .as("OFF_ROUTE→DWELL на стоянке посреди объезда запрещён").isZero();
        assertThat(dwellEventsInDetour)
                .as("DWELL_ENTER на объезде не эмитится").isZero();
    }
}
