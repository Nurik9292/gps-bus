package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.HypothesisBank;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.metrics.MarkerFlightMetric;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Variant25BankScenariosTest {

    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;

    private static final GeometryFixture FULL_0 = Variant25FixturesTest.FULL_0;
    private static final GeometryFixture FULL_1 = Variant25FixturesTest.FULL_1;
    private static final GeometryFixture SHORT_0 = Variant25FixturesTest.short0().shortVariant();
    private static final GeometryFixture SHORT_1 = Variant25FixturesTest.short1().shortVariant();
    private static final double RING_ZONE_START_D1 = 17400;

    private static RouteTopology topo25FirstDir1() {
        return RouteTopology.thereAndBack(FULL_1, FULL_0).withVariants(List.of(SHORT_1, SHORT_0));
    }

    private record Tick(PredictionModel.Estimate est, int direction, String leaderId,
                        List<HypothesisBank.Hypothesis> hyps, double tSec) {}

    private record Run(List<Tick> ticks, MotionFilterCore core, MarkerFlightMetric.FlightStats flight) {}

    private Run run(List<GpsFix> fixes, RouteTopology topo) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<Tick> ticks = new ArrayList<>();
        List<double[]> geo = new ArrayList<>();
        List<Double> tSec = new ArrayList<>();
        List<Boolean> sanctioned = new ArrayList<>();
        String prevLeader = null;
        long t0 = fixes.get(0).timestamp().toEpochMilli();
        for (GpsFix fx : fixes) {
            PredictionModel.Estimate est = core.onFix(fx, topo);
            String leader = core.bank().leader().variantId();
            double t = (fx.timestamp().toEpochMilli() - t0) / 1000.0;
            ticks.add(new Tick(est, core.direction(), leader, core.bank().hypotheses(), t));
            geo.add(core.bank().leader().geom().pointAtS(est.s()));
            tSec.add(t);
            sanctioned.add(est.mode().equals("RECOVERING") || est.mode().equals("NEW_TRIP")
                    || (prevLeader != null && !prevLeader.equals(leader)));
            prevLeader = leader;
        }
        MarkerFlightMetric.FlightStats flight =
                MarkerFlightMetric.compute(geo, tSec, sanctioned, CFG.vMaxMs(), 1.5);
        return new Run(ticks, core, flight);
    }

    @Test
    void shortTripTurnAtZoneBorderCaughtByBankNoMarkerFlight() {
        SyntheticScenario.TurnTrack track = SyntheticScenario.journey(
                SyntheticScenario.Params.defaults(900, "25", 1), CRUISE, 1.0,
                List.of(new SyntheticScenario.Leg(SHORT_1, 200, SHORT_1.totalMeters() - 50, 1, 120),
                        new SyntheticScenario.Leg(SHORT_0, 0, 6000, 0, 0)));
        Run r = run(track.fixes(), topo25FirstDir1());

        int backStart = -1;
        int newTripIdx = -1;
        for (int i = 0; i < r.ticks().size(); i++) {
            if (backStart < 0 && track.truth().get(i)[3] == 0 && track.truth().get(i)[2] > 1.0) {
                backStart = i;
            }
            if (newTripIdx < 0 && r.ticks().get(i).est().mode().equals("NEW_TRIP")) {
                newTripIdx = i;
            }
        }
        assertThat(newTripIdx).as("разворот short-рейса на границе зоны пойман").isPositive();
        int n = newTripIdx - backStart;
        System.out.printf("A9.2-П1 (short-рейс, разворот на границе — вне терминалов full): "
                        + "N=%d фиксов от начала обратного хода до NEW_TRIP; путь=%s→NEW_TRIP; "
                        + "лидер=%s; полёт max=%.2f, нарушений=%d (санкц. скачков=%d)%n",
                n, r.ticks().get(newTripIdx - 1).est().mode(), r.ticks().get(newTripIdx).leaderId(),
                r.flight().maxRatio(), r.flight().violations(), r.flight().sanctionedJumps());
        assertThat(r.ticks().get(newTripIdx - 1).est().mode())
                .as("банковский путь RECOVERING→NEW_TRIP (№22)").isEqualTo("RECOVERING");
        assertThat(n).as("перехват за разумное число фиксов").isLessThanOrEqualTo(25);
        assertThat(r.core().tripId()).isEqualTo(2);
        assertThat(r.ticks().get(r.ticks().size() - 1).direction()).as("направление сменилось").isEqualTo(0);
        assertThat(r.flight().violations()).as("«полёта маркера» нет").isZero();
        long offRoute = r.ticks().stream().filter(t -> t.est().mode().equals("OFF_ROUTE")).count();
        assertThat(offRoute).as("short-рейс без OFF_ROUTE (геометрия ⊂ full)").isZero();
    }

    @Test
    void fullTripThroughRingKeepsLeaderWrongOrientationDies() {
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(FULL_1,
                SyntheticScenario.Params.defaults(901, "25", 1),
                13000, 33000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), false);
        Run r = run(track.fixes(), topo25FirstDir1());

        assertThat(r.core().bank().switchCount()).as("удержание full-лидера через кольцо").isZero();
        assertThat(r.core().tripId()).as("обход кольца без флипа d").isEqualTo(1);
        for (Tick t : r.ticks()) {
            assertThat(t.est().mode()).isNotIn("TURNING", "NEW_TRIP", "OFF_ROUTE");
        }
        double maxGap = 0;
        double tDeath = -1;
        for (Tick t : r.ticks()) {
            if (t.est().s() < RING_ZONE_START_D1) continue;
            double sCcw = score(t, "25#d1");
            double sCw = score(t, "25#d0");
            double gap = sCcw - sCw;
            if (gap >= CFG.sSwitch() && tDeath < 0) tDeath = t.tSec();
            maxGap = Math.max(maxGap, gap);
        }
        System.out.printf("A9.2-П2 (full через кольцо, CCW-борт): лидер удержан; неверная ориентация "
                        + "(CW=full-d0) мертва с t=%.0fс, ΔS_max=%.2f; полёт max=%.2f, нарушений=%d%n",
                tDeath, maxGap, r.flight().maxRatio(), r.flight().violations());
        assertThat(maxGap).as("неверная ориентация обхода умирает в банке (ΔS ≥ порога смены)")
                .isGreaterThanOrEqualTo(CFG.sSwitch());
        assertThat(r.flight().violations()).as("«полёта маркера» в кольце нет").isZero();
        long ringDwells = r.ticks().stream().filter(t -> t.est().mode().equals("DWELL")).count();
        assertThat(ringDwells).as("стопы кольца обслуживаются").isPositive();
    }

    @Test
    void shortThenFullTripBankReseedAcrossNewTrips() {
        SyntheticScenario.TurnTrack track = SyntheticScenario.journey(
                SyntheticScenario.Params.defaults(902, "25", 1), CRUISE, 1.0,
                List.of(new SyntheticScenario.Leg(SHORT_1, 200, SHORT_1.totalMeters() - 50, 1, 120),
                        new SyntheticScenario.Leg(FULL_0, 18716, FULL_0.totalMeters(), 0, 180),
                        new SyntheticScenario.Leg(FULL_1, 0, 22000, 1, 0)));
        Run r = run(track.fixes(), topo25FirstDir1());

        List<String> chain = new ArrayList<>();
        List<Integer> newTripTicks = new ArrayList<>();
        for (int i = 0; i < r.ticks().size(); i++) {
            String m = r.ticks().get(i).est().mode();
            if (chain.isEmpty() || !chain.get(chain.size() - 1).equals(m)) chain.add(m);
            if (m.equals("NEW_TRIP") && (newTripTicks.isEmpty()
                    || i - newTripTicks.get(newTripTicks.size() - 1) > 5)) {
                newTripTicks.add(i);
            }
        }
        assertThat(newTripTicks).as("два новых рейса: банковский на границе + терминальный у Gurtly")
                .hasSize(2);
        String before1 = r.ticks().get(newTripTicks.get(0) - 1).est().mode();
        String before2 = r.ticks().get(newTripTicks.get(1) - 1).est().mode();
        assertThat(before1).isEqualTo("RECOVERING");
        assertThat(before2).isEqualTo("TURNING");
        assertThat(r.core().tripId()).isEqualTo(3);

        Tick last = r.ticks().get(r.ticks().size() - 1);
        assertThat(last.direction()).as("второй рейс — d1").isEqualTo(1);
        assertThat(last.leaderId()).as("в кольце лидер full-d1 (short умерла за границей зоны — "
                + "пересев банка после NEW_TRIP, INV-16)").isEqualTo("25#d1");
        assertThat(last.est().s()).as("борт в кольцевой зоне full-d1").isGreaterThan(RING_ZONE_START_D1);
        System.out.printf("A9.2-П3 (short→full между рейсами): NEW_TRIP пути [%s, %s], trip_id=%d, "
                        + "финальный лидер=%s (s=%.0f, в кольце); полёт max=%.2f, нарушений=%d%n",
                before1, before2, r.core().tripId(), last.leaderId(), last.est().s(),
                r.flight().maxRatio(), r.flight().violations());
        assertThat(r.flight().violations()).as("«полёта маркера» на всей цепи нет").isZero();
    }

    private static double score(Tick t, String variantId) {
        return t.hyps().stream().filter(h -> h.variantId().equals(variantId))
                .findFirst().map(HypothesisBank.Hypothesis::score).orElse(Double.NaN);
    }
}
